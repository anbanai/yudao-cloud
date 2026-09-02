package cn.iocoder.yudao.module.trade.service.logistics;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.trade.controller.notify.logistics.vo.SfRoutePushReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsTraceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsWaybillDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsTraceMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsWaybillMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class SfRoutePushServiceImpl implements SfRoutePushService {

    private static final DateTimeFormatter SF_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_ROUTES_PER_PUSH = 10;

    @Resource private TradeLogisticsWaybillMapper waybillMapper;
    @Resource private TradeLogisticsTraceMapper traceMapper;

    @Override
    public void process(SfRoutePushReqVO request) {
        List<SfRoutePushReqVO.Route> routes = request == null || request.getBody() == null
                ? null : request.getBody().getWaybillRoute();
        if (CollUtil.isEmpty(routes) || routes.size() > MAX_ROUTES_PER_PUSH) {
            throw new IllegalArgumentException("顺丰路由推送必须包含 1 至 10 条轨迹");
        }
        routes.forEach(this::processRoute);
    }

    private void processRoute(SfRoutePushReqVO.Route route) {
        validateRoute(route);
        List<TradeLogisticsWaybillDO> matches = waybillMapper.selectForRoutePushIgnoreTenant(
                route.getMailno(), route.getOrderid());
        if (matches.size() != 1) {
            throw new IllegalArgumentException("无法唯一匹配顺丰运单");
        }
        TradeLogisticsWaybillDO waybill = matches.get(0);
        TenantUtils.execute(waybill.getTenantId(), () -> persistRoute(waybill, route));
    }

    private void persistRoute(TradeLogisticsWaybillDO waybill, SfRoutePushReqVO.Route route) {
        if (traceMapper.selectByWaybillIdAndProviderEventId(waybill.getId(), route.getId()) == null) {
            TradeLogisticsTraceDO trace = new TradeLogisticsTraceDO().setWaybillId(waybill.getId())
                    .setProviderEventId(route.getId()).setStatus(route.getOpCode())
                    .setContent(StrUtil.blankToDefault(route.getRemark(), route.getReasonName()))
                    .setLocation(route.getAcceptAddress()).setOperateTime(parseTime(route.getAcceptTime()))
                    .setRawData(JsonUtils.toJsonString(route));
            try {
                traceMapper.insert(trace);
            } catch (DuplicateKeyException ignored) {
                // 顺丰会重试整批路由；数据库唯一键负责并发幂等。
            }
        }
        waybillMapper.updateById(new TradeLogisticsWaybillDO().setId(waybill.getId())
                .setLastSyncTime(LocalDateTime.now()));
    }

    private static void validateRoute(SfRoutePushReqVO.Route route) {
        if (route == null || StrUtil.isBlank(route.getMailno()) || StrUtil.isBlank(route.getOrderid())
                || StrUtil.isBlank(route.getId()) || StrUtil.isBlank(route.getAcceptTime())
                || StrUtil.isBlank(StrUtil.blankToDefault(route.getRemark(), route.getReasonName()))) {
            throw new IllegalArgumentException("顺丰路由推送缺少必要字段");
        }
    }

    private static LocalDateTime parseTime(String value) {
        try {
            return LocalDateTime.parse(value, SF_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("顺丰路由时间格式无效", exception);
        }
    }
}
