package cn.iocoder.yudao.module.trade.service.report;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.report.vo.*;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.report.TradeReportHistoryDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.report.TradeReportHistoryDetailDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.report.TradeReportHistoryDetailMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.report.TradeReportHistoryMapper;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TradeReportServiceImpl implements TradeReportService {

    private static final String PICKING = "picking";
    private static final String RECONCILIATION = "reconciliation";

    @Resource
    private TradeOrderMapper tradeOrderMapper;
    @Resource
    private TradeOrderItemMapper tradeOrderItemMapper;
    @Resource
    private ProductSkuApi productSkuApi;
    @Resource
    private TradeReportHistoryMapper historyMapper;
    @Resource
    private TradeReportHistoryDetailMapper detailMapper;
    private final TradeReportAggregator aggregator = new TradeReportAggregator();

    @Override
    public TradeReportPageRespVO getReportPage(String reportType, TradeReportPageReqVO reqVO) {
        ReportData data = getReportData(reportType, reqVO);
        return page(data.rows(), data.orderCount(), reqVO);
    }

    @Override
    public PageResult<TradeReportRowRespVO> getRows(String reportType, TradeReportPageReqVO reqVO) {
        List<TradeReportRowRespVO> rows = getReportData(reportType, reqVO).rows();
        int pageNo = Math.max(1, reqVO.getPageNo());
        int pageSize = reqVO.getPageSize() == null || reqVO.getPageSize() <= 0 ? rows.size() : reqVO.getPageSize();
        pageSize = Math.max(1, pageSize);
        int from = Math.min(rows.size(), (pageNo - 1) * pageSize);
        int to = Math.min(rows.size(), from + pageSize);
        return new PageResult<>(rows.subList(from, to), (long) rows.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveHistory(Long operatorId, String reportType, TradeReportPageReqVO reqVO) {
        // Build the report once so the summary and detail rows belong to the same
        // read snapshot even when orders change while the history is being saved.
        TradeReportPageReqVO query = copyForAll(reqVO);
        ReportData data = getReportData(reportType, query);
        TradeReportPageRespVO report = page(data.rows(), data.orderCount(), query);
        TradeReportHistoryDO history = new TradeReportHistoryDO();
        history.setReportType(reportType);
        history.setFiltersJson(JSONUtil.toJsonStr(reqVO));
        history.setOrderCount(report.getOrderCount());
        history.setProductTypeCount(report.getProductTypeCount());
        history.setProductCount(report.getProductCount());
        history.setAmount(report.getAmount());
        history.setCreator(operatorId == null ? null : String.valueOf(operatorId));
        history.setTenantId(TenantContextHolder.getRequiredTenantId());
        historyMapper.insert(history);
        List<TradeReportRowRespVO> rows = data.rows();
        for (TradeReportRowRespVO row : rows) {
            TradeReportHistoryDetailDO detail = new TradeReportHistoryDetailDO();
            detail.setHistoryId(history.getId());
            detail.setSpuId(row.getSpuId());
            detail.setSkuId(row.getSkuId());
            detail.setProductName(row.getProductName());
            detail.setPicUrl(row.getPicUrl());
            detail.setBarCode(row.getBarCode());
            detail.setProperties(row.getProperties());
            detail.setQuantity(row.getQuantity());
            detail.setAmount(row.getAmount());
            detail.setOrderCount(row.getOrderCount());
            detail.setTenantId(history.getTenantId());
            detailMapper.insert(detail);
        }
        return history.getId();
    }

    @Override
    public PageResult<TradeReportHistoryRespVO> getHistoryPage(TradeReportHistoryPageReqVO reqVO) {
        PageResult<TradeReportHistoryDO> page = historyMapper.selectPage(reqVO, TenantContextHolder.getRequiredTenantId());
        List<TradeReportHistoryRespVO> list = page.getList().stream().map(this::convertHistory).toList();
        return new PageResult<>(list, page.getTotal());
    }

    @Override
    public TradeReportPageRespVO getHistory(String reportType, Long id) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        TradeReportHistoryDO history = historyMapper.selectOne(new LambdaQueryWrapperX<TradeReportHistoryDO>()
                .eq(TradeReportHistoryDO::getId, id)
                .apply("tenant_id = {0}", tenantId));
        if (history == null || !reportType.equalsIgnoreCase(history.getReportType())) {
            return null;
        }
        List<TradeReportRowRespVO> rows = detailMapper.selectListByHistoryId(id, tenantId).stream().map(this::convertDetail).toList();
        TradeReportPageRespVO result = new TradeReportPageRespVO();
        result.setTotal((long) rows.size());
        result.setOrderCount(history.getOrderCount());
        result.setProductTypeCount(history.getProductTypeCount());
        result.setProductCount(history.getProductCount());
        result.setAmount(history.getAmount());
        result.setList(rows);
        return result;
    }

    private ReportData getReportData(String reportType, TradeReportPageReqVO reqVO) {
        TradeReportPageReqVO query = reqVO == null ? new TradeReportPageReqVO() : reqVO;
        List<Integer> statuses = defaultStatuses(reportType, query.getStatuses());
        LocalDateTime[] range = query.getDateTime();
        LocalDateTime from = range != null && range.length > 0 ? range[0] : null;
        LocalDateTime to = range != null && range.length > 1 ? range[1] : null;
        boolean deliveryDate = RECONCILIATION.equalsIgnoreCase(reportType);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        LambdaQueryWrapperX<TradeOrderDO> wrapper = new LambdaQueryWrapperX<>();
        /* conditions are added separately because inherited wrapper methods return the base type */
        wrapper.inIfPresent(TradeOrderDO::getStatus, statuses);
        wrapper.eqIfPresent(TradeOrderDO::getTerminal, query.getTerminal());
        wrapper.eqIfPresent(TradeOrderDO::getDeliveryType, query.getDeliveryType());
        wrapper.apply("tenant_id = {0}", tenantId);
        if (deliveryDate) {
            wrapper.geIfPresent(TradeOrderDO::getDeliveryTime, from)
                    .leIfPresent(TradeOrderDO::getDeliveryTime, to);
        } else {
            wrapper.geIfPresent(TradeOrderDO::getPayTime, from)
                    .leIfPresent(TradeOrderDO::getPayTime, to);
        }
        List<TradeOrderDO> orders = tradeOrderMapper.selectList(wrapper);
        // Keep the in-memory check as a defensive guard for custom mapper implementations.
        orders.removeIf(order -> {
            LocalDateTime date = deliveryDate ? order.getDeliveryTime() : order.getPayTime();
            return date == null || (from != null && date.isBefore(from)) || (to != null && date.isAfter(to));
        });
        if (orders.isEmpty()) {
            return new ReportData(List.of(), 0);
        }
        Set<Long> orderIds = orders.stream().map(TradeOrderDO::getId).collect(Collectors.toSet());
        Map<Long, TradeOrderDO> orderMap = orders.stream().collect(Collectors.toMap(TradeOrderDO::getId, Function.identity()));
        List<TradeOrderItemDO> items = tradeOrderItemMapper.selectListByOrderIdsAndTenant(orderIds, tenantId);
        Set<Long> skuIds = items.stream().map(TradeOrderItemDO::getSkuId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProductSkuRespDTO> skuMap = skuIds.isEmpty() ? Map.of() : productSkuApi.getSkuMap(skuIds);
        List<TradeReportAggregator.Line> lines = new ArrayList<>();
        for (TradeOrderItemDO item : items) {
            if (!orderMap.containsKey(item.getOrderId())) {
                continue;
            }
            String properties = properties(item.getProperties());
            ProductSkuRespDTO sku = skuMap.get(item.getSkuId());
            String barCode = sku == null ? null : sku.getBarCode();
            if (contains(query.getProductName(), item.getSpuName())
                    && contains(query.getProperties(), properties)
                    && contains(query.getBarCode(), barCode)
                    && contains(query.getLogisticsNo(), orderMap.get(item.getOrderId()).getLogisticsNo())) {
                int count = item.getCount() == null ? 0 : item.getCount();
                long amount = item.getPayPrice() == null ? 0L : item.getPayPrice();
                lines.add(new TradeReportAggregator.Line(item.getOrderId(), item.getSpuId(), item.getSkuId(),
                        item.getSpuName(), item.getPicUrl(), barCode, properties, count, amount));
            }
        }
        List<TradeReportRowRespVO> rows = aggregator.aggregate(lines).stream().map(this::convertRow).toList();
        long orderCount = lines.stream().map(TradeReportAggregator.Line::orderId)
                .filter(Objects::nonNull).distinct().count();
        return new ReportData(rows, orderCount);
    }

    private static boolean contains(String expected, String actual) {
        return expected == null || expected.isBlank() || (actual != null && actual.contains(expected));
    }

    private static String properties(List<TradeOrderItemDO.Property> properties) {
        if (CollUtil.isEmpty(properties)) {
            return "";
        }
        return properties.stream().map(property -> {
            String name = property.getPropertyName() == null ? "" : property.getPropertyName();
            String value = property.getValueName() == null ? "" : property.getValueName();
            return name.isEmpty() ? value : name + ":" + value;
        }).collect(Collectors.joining("; "));
    }

    private static List<Integer> defaultStatuses(String reportType, List<Integer> statuses) {
        if (statuses != null && !statuses.isEmpty()) {
            return statuses;
        }
        return RECONCILIATION.equalsIgnoreCase(reportType)
                ? List.of(TradeOrderStatusEnum.DELIVERED.getStatus(), TradeOrderStatusEnum.COMPLETED.getStatus())
                : List.of(TradeOrderStatusEnum.UNDELIVERED.getStatus());
    }

    private static TradeReportPageReqVO copyForAll(TradeReportPageReqVO source) {
        TradeReportPageReqVO target = new TradeReportPageReqVO();
        if (source != null) {
            target.setDateTime(source.getDateTime());
            target.setStatuses(source.getStatuses());
            target.setTerminal(source.getTerminal());
            target.setDeliveryType(source.getDeliveryType());
            target.setProductName(source.getProductName());
            target.setProperties(source.getProperties());
            target.setBarCode(source.getBarCode());
            target.setLogisticsNo(source.getLogisticsNo());
        }
        target.setPageNo(1);
        target.setPageSize(-1);
        return target;
    }

    private static TradeReportPageRespVO page(List<TradeReportRowRespVO> rows, long orderCount, TradeReportPageReqVO reqVO) {
        int pageNo = Math.max(1, reqVO == null ? 1 : reqVO.getPageNo());
        int pageSize = reqVO == null || reqVO.getPageSize() == null || reqVO.getPageSize() <= 0
                ? rows.size() : reqVO.getPageSize();
        pageSize = Math.max(1, pageSize);
        int from = Math.min(rows.size(), (pageNo - 1) * pageSize);
        int to = Math.min(rows.size(), from + pageSize);
        TradeReportPageRespVO result = new TradeReportPageRespVO();
        result.setTotal((long) rows.size());
        result.setProductTypeCount(rows.size());
        result.setProductCount(rows.stream().mapToInt(row -> row.getQuantity() == null ? 0 : row.getQuantity()).sum());
        result.setAmount(rows.stream().mapToLong(row -> row.getAmount() == null ? 0L : row.getAmount()).sum());
        result.setOrderCount(orderCount);
        result.setList(rows.subList(from, to));
        return result;
    }

    private record ReportData(List<TradeReportRowRespVO> rows, long orderCount) {
    }

    private TradeReportRowRespVO convertRow(TradeReportAggregator.Row row) {
        TradeReportRowRespVO vo = new TradeReportRowRespVO();
        vo.setSpuId(row.spuId());
        vo.setSkuId(row.skuId());
        vo.setProductName(row.name());
        vo.setPicUrl(row.picUrl());
        vo.setBarCode(row.barCode());
        vo.setProperties(row.properties());
        vo.setQuantity(row.quantity());
        vo.setAmount(row.amount());
        vo.setOrderCount(row.orderCount());
        return vo;
    }

    private TradeReportHistoryRespVO convertHistory(TradeReportHistoryDO history) {
        TradeReportHistoryRespVO vo = new TradeReportHistoryRespVO();
        vo.setId(history.getId());
        vo.setReportType(history.getReportType());
        vo.setOrderCount(history.getOrderCount());
        vo.setProductTypeCount(history.getProductTypeCount());
        vo.setProductCount(history.getProductCount());
        vo.setAmount(history.getAmount());
        vo.setCreateTime(history.getCreateTime());
        vo.setFiltersJson(history.getFiltersJson());
        return vo;
    }

    private TradeReportRowRespVO convertDetail(TradeReportHistoryDetailDO detail) {
        TradeReportRowRespVO vo = new TradeReportRowRespVO();
        vo.setSpuId(detail.getSpuId());
        vo.setSkuId(detail.getSkuId());
        vo.setProductName(detail.getProductName());
        vo.setPicUrl(detail.getPicUrl());
        vo.setBarCode(detail.getBarCode());
        vo.setProperties(detail.getProperties());
        vo.setQuantity(detail.getQuantity());
        vo.setAmount(detail.getAmount());
        vo.setOrderCount(detail.getOrderCount());
        return vo;
    }
}
