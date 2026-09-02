package cn.iocoder.yudao.module.trade.service.logistics;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.product.api.sku.dto.ProductSkuRespDTO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsPendingOrderRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsPrintTaskRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsTraceRespVO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.*;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.*;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsWaybillStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfApiException;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfLogisticsClient;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Supplier;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.*;

@Service
public class LogisticsWaybillServiceImpl implements LogisticsWaybillService {

    @Resource private TradeOrderMapper orderMapper;
    @Resource private TradeOrderItemMapper orderItemMapper;
    @Resource private TradeLogisticsAccountMapper accountMapper;
    @Resource private TradeLogisticsWaybillMapper waybillMapper;
    @Resource private TradeWechatLogisticsWaybillMapper wechatWaybillMapper;
    @Resource private TradeLogisticsPrintDeviceMapper deviceMapper;
    @Resource private TradeLogisticsPrintTaskMapper taskMapper;
    @Resource private TradeLogisticsTraceMapper traceMapper;
    @Resource private SfLogisticsClient sfClient;
    @Resource private LogisticsLabelNormalizer labelNormalizer;
    @Resource private FileApi fileApi;
    @Resource private ProductSkuApi productSkuApi;
    @Resource private TradeOrderUpdateService orderUpdateService;
    @Resource private PlatformTransactionManager transactionManager;

    @Override
    public LogisticsWaybillRespVO createWaybill(LogisticsWaybillCreateReqVO request) {
        CreationPreparation preparation = inTransaction(() -> prepareCreation(request));
        TradeLogisticsWaybillDO waybill = preparation.waybill();
        if (preparation.task() != null) {
            return toResp(waybill, preparation.task()).setReused(true);
        }
        TradeLogisticsAccountDO account = preparation.account();
        if (preparation.newRecord() || LogisticsWaybillStatusEnum.FAILED.name().equals(waybill.getStatus())) {
            createRemoteWaybill(waybill, account, preparation.order());
        } else if (List.of(LogisticsWaybillStatusEnum.CREATING.name(),
                LogisticsWaybillStatusEnum.UNKNOWN.name()).contains(waybill.getStatus())) {
            queryRemoteWaybill(waybill, account);
        }
        if (!LogisticsWaybillStatusEnum.CREATED.name().equals(waybill.getStatus())) {
            return toResp(waybill, null);
        }
        return createLabelAndTask(waybill, account, preparation.device());
    }

    private CreationPreparation prepareCreation(LogisticsWaybillCreateReqVO request) {
        TradeOrderDO order = orderMapper.selectByIdForUpdate(request.getOrderId());
        validateOrder(order);
        TradeWechatLogisticsWaybillDO wechatWaybill = wechatWaybillMapper.selectByOrderIdForUpdate(order.getId());
        if (wechatWaybill != null && List.of("CREATING", "CREATED", "UNKNOWN")
                .contains(wechatWaybill.getStatus())) {
            throw exception(WECHAT_LOGISTICS_WAYBILL_ALREADY_EXISTS);
        }
        TradeLogisticsAccountDO account = getAccount(request.getAccountId());
        TradeLogisticsPrintDeviceDO device = getDevice(request.getDeviceId());
        TradeLogisticsWaybillDO waybill = waybillMapper.selectByOrderIdForUpdate(order.getId());
        boolean newRecord = false;
        if (waybill == null) {
            String providerOrderNo = "YD-" + TenantContextHolder.getTenantId() + "-" + order.getId();
            waybill = new TradeLogisticsWaybillDO().setOrderId(order.getId()).setOrderNo(order.getNo())
                    .setAccountId(account.getId()).setLogisticsId(account.getLogisticsId())
                    .setRequestedDeviceId(device.getId())
                    .setProviderOrderNo(providerOrderNo).setStatus(LogisticsWaybillStatusEnum.CREATING.name())
                    .setDeliveryStatus("PENDING");
            waybillMapper.insert(waybill);
            newRecord = true;
        } else {
            TradeLogisticsPrintTaskDO task = taskMapper.selectLatestByWaybillId(waybill.getId());
            if (LogisticsWaybillStatusEnum.CANCELLED.name().equals(waybill.getStatus())) {
                String providerOrderNo = "YD-" + TenantContextHolder.getTenantId() + "-" + order.getId()
                        + "-" + IdUtil.fastSimpleUUID();
                waybill = new TradeLogisticsWaybillDO().setOrderId(order.getId()).setOrderNo(order.getNo())
                        .setAccountId(account.getId()).setLogisticsId(account.getLogisticsId())
                        .setRequestedDeviceId(device.getId()).setProviderOrderNo(providerOrderNo)
                        .setStatus(LogisticsWaybillStatusEnum.CREATING.name()).setDeliveryStatus("PENDING");
                waybillMapper.insert(waybill);
                newRecord = true;
            } else if (task != null) {
                return new CreationPreparation(order, account, device, waybill, task, false);
            } else {
                account = getAccount(waybill.getAccountId());
            }
        }
        return new CreationPreparation(order, account, device, waybill, null, newRecord);
    }

    private void createRemoteWaybill(TradeLogisticsWaybillDO waybill, TradeLogisticsAccountDO account,
                                     TradeOrderDO order) {
        try {
            List<TradeOrderItemDO> items = orderItemMapper.selectListByOrderId(order.getId());
            Map<Long, ProductSkuRespDTO> skuMap = items.isEmpty() ? Map.of()
                    : productSkuApi.getSkuMap(items.stream().map(TradeOrderItemDO::getSkuId)
                            .collect(Collectors.toSet()));
            markCreated(waybill, sfClient.createWaybill(account, waybill.getProviderOrderNo(), order, items, skuMap));
        } catch (SfApiException exception) {
            updateFailure(waybill, exception);
        }
    }

    private void queryRemoteWaybill(TradeLogisticsWaybillDO waybill, TradeLogisticsAccountDO account) {
        try {
            markCreated(waybill, sfClient.queryByProviderOrderNo(account, waybill.getProviderOrderNo()));
        } catch (SfApiException exception) {
            updateFailure(waybill, exception);
        }
    }

    private LogisticsWaybillRespVO createLabelAndTask(TradeLogisticsWaybillDO waybill,
                                                        TradeLogisticsAccountDO account,
                                                        TradeLogisticsPrintDeviceDO device) {
        try {
            var privateStorage = fileApi.isPrivatePresignedGetSupported();
            if (privateStorage == null || !Boolean.TRUE.equals(privateStorage.getData())) {
                throw exception(LOGISTICS_PRIVATE_STORAGE_REQUIRED);
            }
            byte[] pdf = sfClient.getLabel(account, waybill.getWaybillNo());
            byte[] png = labelNormalizer.normalizePdf(pdf, account.getPaperWidthMm(), account.getPaperHeightMm(),
                    account.getDpi());
            String checksum = DigestUtil.sha256Hex(png);
            String labelUrl = fileApi.createFile(png, waybill.getWaybillNo() + ".png",
                    "trade/logistics/labels", "image/png");
            return inTransaction(() -> persistLabelAndTask(waybill, account, device, labelUrl, checksum, png.length));
        } catch (RuntimeException exception) {
            inTransaction(() -> {
                TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(waybill.getId());
                if (current == null) current = waybill;
                current.setErrorCode("LABEL_FAILED").setErrorMessage(StrUtil.maxLength(exception.getMessage(), 1024));
                waybillMapper.updateById(current);
                waybill.setErrorCode(current.getErrorCode()).setErrorMessage(current.getErrorMessage());
                return null;
            });
            return toResp(waybill, null);
        }
    }

    private LogisticsWaybillRespVO persistLabelAndTask(TradeLogisticsWaybillDO waybill,
                                                        TradeLogisticsAccountDO account,
                                                        TradeLogisticsPrintDeviceDO device,
                                                        String labelUrl, String checksum, int labelSize) {
        TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(waybill.getId());
        if (current == null) current = waybill;
        TradeLogisticsPrintTaskDO existingTask = taskMapper.selectLatestByWaybillId(waybill.getId());
        if (existingTask != null) {
            return toResp(current, existingTask).setReused(true);
        }
        current.setLabelUrl(labelUrl).setLabelContentType("image/png").setLabelChecksum(checksum)
                .setLabelSize((long) labelSize).setTemplateCode(account.getTemplateCode())
                .setPaperWidthMm(account.getPaperWidthMm()).setPaperHeightMm(account.getPaperHeightMm())
                .setDpi(account.getDpi()).setErrorCode(null).setErrorMessage(null);
        waybillMapper.updateById(current);
        TradeLogisticsPrintTaskDO task = newTask(current, device);
        taskMapper.insert(task);
        return toResp(current, task);
    }

    private void markCreated(TradeLogisticsWaybillDO waybill, SfLogisticsClient.WaybillResult result) {
        inTransaction(() -> {
            TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(waybill.getId());
            if (current == null) current = waybill;
            current.setWaybillNo(result.waybillNo()).setStatus(LogisticsWaybillStatusEnum.CREATED.name())
                    .setProviderResponse(StrUtil.maxLength(JsonUtils.toJsonString(result.rawResponse()), 10_000))
                    .setLastSyncTime(LocalDateTime.now()).setErrorCode(null).setErrorMessage(null);
            waybillMapper.updateById(current);
            copyRemoteState(current, waybill);
            return null;
        });
    }

    private void updateFailure(TradeLogisticsWaybillDO waybill, SfApiException exception) {
        inTransaction(() -> {
            TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(waybill.getId());
            if (current == null) current = waybill;
            if (!LogisticsWaybillStatusEnum.CREATED.name().equals(current.getStatus())) {
                current.setStatus(exception.isUnknownResult() ? LogisticsWaybillStatusEnum.UNKNOWN.name()
                                : LogisticsWaybillStatusEnum.FAILED.name())
                        .setErrorCode(exception.getCode())
                        .setErrorMessage(StrUtil.maxLength(exception.getMessage(), 1024));
                waybillMapper.updateById(current);
            }
            copyRemoteState(current, waybill);
            return null;
        });
    }

    private TradeLogisticsPrintTaskDO newTask(TradeLogisticsWaybillDO waybill,
                                               TradeLogisticsPrintDeviceDO device) {
        return new TradeLogisticsPrintTaskDO().setRequestId("REQ-" + IdUtil.fastSimpleUUID())
                .setJobId("JOB-" + IdUtil.fastSimpleUUID()).setOrderId(waybill.getOrderId())
                .setWaybillId(waybill.getId()).setDeviceId(device.getId())
                .setStatus(LogisticsPrintTaskStatusEnum.PENDING.name()).setFormat("image")
                .setLabelUrl(waybill.getLabelUrl()).setChecksum(waybill.getLabelChecksum())
                .setPaperWidthMm(waybill.getPaperWidthMm()).setPaperHeightMm(waybill.getPaperHeightMm())
                .setDpi(waybill.getDpi()).setCopies(1).setTestFlag(false);
    }

    @Override
    public LogisticsWaybillRespVO getWaybill(Long id) {
        TradeLogisticsWaybillDO waybill = waybillMapper.selectById(id);
        if (waybill == null) throw exception(LOGISTICS_WAYBILL_NOT_EXISTS);
        return toResp(waybill, taskMapper.selectLatestByWaybillId(id));
    }

    @Override
    public List<LogisticsWaybillRespVO> getWaybills() {
        return waybillMapper.selectListAll().stream()
                .map(waybill -> toResp(waybill, taskMapper.selectLatestByWaybillId(waybill.getId()))).toList();
    }

    @Override
    public List<LogisticsPendingOrderRespVO> getPendingOrders() {
        List<TradeOrderDO> orders = orderMapper.selectListUndeliveredExpress();
        if (orders.isEmpty()) {
            return List.of();
        }
        Set<Long> blockedOrderIds = wechatWaybillMapper.selectListByOrderIdsAndStatuses(
                        orders.stream().map(TradeOrderDO::getId).toList(), List.of("CREATING", "CREATED", "UNKNOWN"))
                .stream().map(TradeWechatLogisticsWaybillDO::getOrderId).collect(Collectors.toCollection(HashSet::new));
        return orders.stream().filter(order -> !blockedOrderIds.contains(order.getId())).map(order -> new LogisticsPendingOrderRespVO()
                .setId(order.getId()).setNo(order.getNo()).setReceiverName(order.getReceiverName())
                .setReceiverMobile(order.getReceiverMobile()).setProductCount(order.getProductCount())
                .setPayPrice(order.getPayPrice()).setCreateTime(order.getCreateTime())).toList();
    }

    @Override
    public List<LogisticsPrintTaskRespVO> getPrintTasks() {
        return taskMapper.selectListAll().stream().map(this::toTaskResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogisticsWaybillRespVO reprint(Long id, Long deviceId) {
        TradeLogisticsWaybillDO waybill = waybillMapper.selectByIdForUpdate(id);
        if (waybill == null || !LogisticsWaybillStatusEnum.CREATED.name().equals(waybill.getStatus())) {
            throw exception(LOGISTICS_WAYBILL_NOT_EXISTS);
        }
        TradeLogisticsPrintTaskDO previous = taskMapper.selectLatestByWaybillId(id);
        if (previous != null && !List.of(LogisticsPrintTaskStatusEnum.FAILED.name(),
                LogisticsPrintTaskStatusEnum.UNKNOWN.name()).contains(previous.getStatus())) {
            throw exception(LOGISTICS_PRINT_TASK_INVALID_STATE);
        }
        TradeLogisticsPrintTaskDO task = newTask(waybill, getDevice(deviceId));
        taskMapper.insert(task);
        return toResp(waybill, task);
    }

    @Override
    public void cancelWaybill(Long id) {
        CancellationPreparation preparation = inTransaction(() -> prepareCancellation(id));
        try {
            sfClient.cancelWaybill(preparation.account(), preparation.waybill().getProviderOrderNo());
            inTransaction(() -> {
                TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(id);
                current.setStatus(LogisticsWaybillStatusEnum.CANCELLED.name()).setCancelledTime(LocalDateTime.now());
                waybillMapper.updateById(current);
                return null;
            });
        } catch (RuntimeException exception) {
            inTransaction(() -> {
                TradeLogisticsWaybillDO current = waybillMapper.selectByIdForUpdate(id);
                current.setStatus(LogisticsWaybillStatusEnum.CANCEL_UNKNOWN.name())
                        .setErrorCode("CANCEL_FAILED").setErrorMessage(StrUtil.maxLength(exception.getMessage(), 1024));
                waybillMapper.updateById(current);
                return null;
            });
            throw exception;
        }
    }

    private CancellationPreparation prepareCancellation(Long id) {
        TradeLogisticsWaybillDO waybill = waybillMapper.selectByIdForUpdate(id);
        if (waybill == null || !List.of(LogisticsWaybillStatusEnum.CREATED.name(),
                        LogisticsWaybillStatusEnum.CANCELLING.name(),
                        LogisticsWaybillStatusEnum.CANCEL_UNKNOWN.name()).contains(waybill.getStatus())
                || !"PENDING".equals(waybill.getDeliveryStatus())) throw exception(LOGISTICS_WAYBILL_NOT_EXISTS);
        TradeLogisticsPrintTaskDO task = taskMapper.selectLatestByWaybillIdForUpdate(id);
        if (task != null && !List.of(LogisticsPrintTaskStatusEnum.PENDING.name(),
                LogisticsPrintTaskStatusEnum.CANCELLED.name()).contains(task.getStatus())) {
            throw exception(LOGISTICS_PRINT_TASK_INVALID_STATE);
        }
        if (task != null) taskMapper.updateById(task.setStatus(LogisticsPrintTaskStatusEnum.CANCELLED.name()));
        waybillMapper.updateById(waybill.setStatus(LogisticsWaybillStatusEnum.CANCELLING.name()));
        return new CancellationPreparation(waybill, task, getAccount(waybill.getAccountId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<LogisticsTraceRespVO> syncTrace(Long id) {
        TradeLogisticsWaybillDO waybill = waybillMapper.selectById(id);
        if (waybill == null || !LogisticsWaybillStatusEnum.CREATED.name().equals(waybill.getStatus())) {
            throw exception(LOGISTICS_WAYBILL_NOT_EXISTS);
        }
        JsonNode routes = sfClient.queryTrace(getAccount(waybill.getAccountId()), waybill.getWaybillNo());
        traceMapper.delete(TradeLogisticsTraceDO::getWaybillId, id);
        for (JsonNode route : extractRoutes(routes)) {
            String content = route.path("remark").asText();
            LocalDateTime time = parseTime(route.path("acceptTime").asText());
            if (StrUtil.isBlank(content) || time == null) {
                continue;
            }
            traceMapper.insert(new TradeLogisticsTraceDO().setWaybillId(id)
                    .setProviderEventId(route.path("id").asText(null)).setStatus(route.path("opCode").asText())
                    .setContent(content).setLocation(route.path("acceptAddress").asText())
                    .setOperateTime(time).setRawData(JsonUtils.toJsonString(route)));
        }
        waybillMapper.updateById(new TradeLogisticsWaybillDO().setId(id).setLastSyncTime(LocalDateTime.now()));
        return traceMapper.selectListByWaybillId(id).stream().map(this::toTraceResp).toList();
    }

    @Override
    public List<LogisticsTraceRespVO> getTrace(Long id) {
        if (waybillMapper.selectById(id) == null) throw exception(LOGISTICS_WAYBILL_NOT_EXISTS);
        return traceMapper.selectListByWaybillId(id).stream().map(this::toTraceResp).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void validateManualDelivery(Long orderId, Long logisticsId, String logisticsNo) {
        orderMapper.selectByIdForUpdate(orderId);
        TradeLogisticsWaybillDO waybill = waybillMapper.selectActiveByOrderId(orderId);
        if (waybill == null || !List.of(LogisticsWaybillStatusEnum.CREATING.name(),
                LogisticsWaybillStatusEnum.CREATED.name(), LogisticsWaybillStatusEnum.UNKNOWN.name(),
                LogisticsWaybillStatusEnum.CANCELLING.name(), LogisticsWaybillStatusEnum.CANCEL_UNKNOWN.name())
                .contains(waybill.getStatus())) {
            return;
        }
        throw exception(LOGISTICS_WAYBILL_ALREADY_EXISTS);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deliverManually(TradeOrderDeliveryReqVO request) {
        validateManualDelivery(request.getId(), request.getLogisticsId(), request.getLogisticsNo());
        orderUpdateService.deliveryOrder(request);
    }

    private void validateOrder(TradeOrderDO order) {
        if (order == null || !TradeOrderStatusEnum.isUndelivered(order.getStatus())) {
            throw exception(ORDER_DELIVERY_FAIL_STATUS_NOT_UNDELIVERED);
        }
        if (!DeliveryTypeEnum.EXPRESS.getType().equals(order.getDeliveryType())) {
            throw exception(ORDER_DELIVERY_FAIL_DELIVERY_TYPE_NOT_EXPRESS);
        }
        if (!TradeOrderRefundStatusEnum.NONE.getStatus().equals(order.getRefundStatus())) {
            throw exception(ORDER_DELIVERY_FAIL_REFUND_STATUS_NOT_NONE);
        }
    }

    private TradeLogisticsAccountDO getAccount(Long id) {
        TradeLogisticsAccountDO account = id == null ? accountMapper.selectDefaultEnabled() : accountMapper.selectById(id);
        if (account == null) throw exception(LOGISTICS_ACCOUNT_NOT_EXISTS);
        if (account.getStatus() == null || account.getStatus() != 0) throw exception(LOGISTICS_ACCOUNT_DISABLED);
        return account;
    }

    private TradeLogisticsPrintDeviceDO getDevice(Long id) {
        TradeLogisticsPrintDeviceDO device = id == null ? deviceMapper.selectDefaultEnabled() : deviceMapper.selectById(id);
        if (device == null || device.getStatus() == null || device.getStatus() != 0) {
            throw exception(LOGISTICS_DEVICE_NOT_EXISTS);
        }
        return device;
    }

    private LogisticsWaybillRespVO toResp(TradeLogisticsWaybillDO waybill, TradeLogisticsPrintTaskDO task) {
        return new LogisticsWaybillRespVO().setId(waybill.getId()).setOrderId(waybill.getOrderId())
                .setOrderNo(waybill.getOrderNo()).setProviderOrderNo(waybill.getProviderOrderNo())
                .setWaybillNo(waybill.getWaybillNo()).setStatus(waybill.getStatus())
                .setDeliveryStatus(waybill.getDeliveryStatus()).setPrintStatus(task == null ? null : task.getStatus())
                .setJobId(task == null ? null : task.getJobId()).setDeviceId(task == null ? null : task.getDeviceId())
                .setErrorCode(waybill.getErrorCode()).setErrorMessage(waybill.getErrorMessage())
                .setCreateTime(waybill.getCreateTime());
    }

    private LogisticsPrintTaskRespVO toTaskResp(TradeLogisticsPrintTaskDO task) {
        return new LogisticsPrintTaskRespVO().setId(task.getId()).setRequestId(task.getRequestId())
                .setJobId(task.getJobId()).setOrderId(task.getOrderId()).setWaybillId(task.getWaybillId())
                .setDeviceId(task.getDeviceId()).setStatus(task.getStatus()).setFormat(task.getFormat())
                .setPaperWidthMm(task.getPaperWidthMm()).setPaperHeightMm(task.getPaperHeightMm())
                .setDpi(task.getDpi()).setCopies(task.getCopies()).setLeaseExpireTime(task.getLeaseExpireTime())
                .setLastError(task.getLastError()).setCreateTime(task.getCreateTime());
    }

    private LogisticsTraceRespVO toTraceResp(TradeLogisticsTraceDO trace) {
        return new LogisticsTraceRespVO().setId(trace.getId()).setStatus(trace.getStatus())
                .setContent(trace.getContent()).setLocation(trace.getLocation()).setOperateTime(trace.getOperateTime());
    }

    private LocalDateTime parseTime(String value) {
        if (StrUtil.isBlank(value)) return null;
        try { return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
        catch (Exception ignored) { return null; }
    }

    private List<JsonNode> extractRoutes(JsonNode response) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode routeResponses = response.path("routeResps");
        if (routeResponses.isArray()) {
            for (JsonNode routeResponse : routeResponses) {
                JsonNode routes = routeResponse.path("routes");
                if (routes.isArray()) {
                    routes.forEach(result::add);
                }
            }
            return result;
        }
        JsonNode routes = response.path("routes");
        if (routes.isArray()) {
            routes.forEach(result::add);
        }
        return result;
    }

    private static void copyRemoteState(TradeLogisticsWaybillDO source, TradeLogisticsWaybillDO target) {
        target.setWaybillNo(source.getWaybillNo()).setStatus(source.getStatus())
                .setProviderResponse(source.getProviderResponse()).setLastSyncTime(source.getLastSyncTime())
                .setErrorCode(source.getErrorCode()).setErrorMessage(source.getErrorMessage());
    }

    private <T> T inTransaction(Supplier<T> supplier) {
        if (transactionManager == null) { // 仅供无 Spring 容器的单元测试使用
            return supplier.get();
        }
        return new TransactionTemplate(transactionManager).execute(status -> supplier.get());
    }

    private record CreationPreparation(TradeOrderDO order, TradeLogisticsAccountDO account,
                                       TradeLogisticsPrintDeviceDO device, TradeLogisticsWaybillDO waybill,
                                       TradeLogisticsPrintTaskDO task, boolean newRecord) {
    }

    private record CancellationPreparation(TradeLogisticsWaybillDO waybill, TradeLogisticsPrintTaskDO task,
                                           TradeLogisticsAccountDO account) {
    }
}
