package cn.iocoder.yudao.module.trade.service.order;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxaWaybillTraceReqDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.ORDER_NOT_FOUND;

@Service
@Slf4j
public class WechatWaybillQueryServiceImpl implements WechatWaybillQueryService {

    private static final String EMPTY_TOKEN = "";

    @Resource
    private TradeOrderMapper orderMapper;
    @Resource
    private TradeOrderItemMapper orderItemMapper;
    @Resource
    private PayOrderApi payOrderApi;
    @Resource
    private DeliveryExpressService deliveryExpressService;
    @Resource
    private SocialClientApi socialClientApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String ensureWechatWaybillToken(Long orderId) {
        return ensureWechatWaybillToken0(null, orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String ensureWechatWaybillToken(Long userId, Long orderId) {
        return ensureWechatWaybillToken0(userId, orderId);
    }

    private String ensureWechatWaybillToken0(Long userId, Long orderId) {
        TradeOrderDO order = orderMapper.selectByIdForUpdate(orderId);
        if (order == null || userId != null && !Objects.equals(userId, order.getUserId())) {
            throw exception(ORDER_NOT_FOUND);
        }
        if (StrUtil.isNotBlank(order.getWechatWaybillToken())) {
            return order.getWechatWaybillToken();
        }
        if (!isOrderEligible(order)) {
            return EMPTY_TOKEN;
        }

        try {
            PayOrderRespDTO payOrder = getPayOrder(order);
            DeliveryExpressDO express = deliveryExpressService.getDeliveryExpress(order.getLogisticsId());
            List<TradeOrderItemDO> items = orderItemMapper.selectListByOrderId(order.getId());
            if (!isTraceDataComplete(payOrder, express, items)) {
                return EMPTY_TOKEN;
            }

            SocialWxaWaybillTraceReqDTO request = buildTraceRequest(order, payOrder, express, items);
            CommonResult<String> result = socialClientApi.traceWxaWaybill(UserTypeEnum.MEMBER.getValue(), request);
            if (result == null || result.isError() || StrUtil.isBlank(result.getData())) {
                logWechatFailure(order.getId(), result == null ? -1 : result.getCode(),
                        result == null ? "empty response" : result.getMsg());
                return EMPTY_TOKEN;
            }
            String token = result.getData();
            orderMapper.updateById(new TradeOrderDO().setId(order.getId()).setWechatWaybillToken(token));
            return token;
        } catch (Exception ex) {
            logWechatFailure(order.getId(), -1, ex.getMessage());
            return EMPTY_TOKEN;
        }
    }

    private boolean isOrderEligible(TradeOrderDO order) {
        return TradeOrderStatusEnum.haveDelivered(order.getStatus())
                && Objects.equals(order.getDeliveryType(), DeliveryTypeEnum.EXPRESS.getType())
                && StrUtil.isNotBlank(order.getLogisticsNo())
                && StrUtil.isNotBlank(order.getReceiverMobile())
                && Objects.equals(order.getPayChannelCode(), PayChannelEnum.WX_LITE.getCode())
                && order.getPayOrderId() != null
                && order.getLogisticsId() != null && order.getLogisticsId() > 0;
    }

    private PayOrderRespDTO getPayOrder(TradeOrderDO order) {
        CommonResult<PayOrderRespDTO> result = payOrderApi.getOrder(order.getPayOrderId());
        return result == null || result.isError() ? null : result.getData();
    }

    private boolean isTraceDataComplete(PayOrderRespDTO payOrder, DeliveryExpressDO express,
                                        List<TradeOrderItemDO> items) {
        if (payOrder == null || !Objects.equals(payOrder.getChannelCode(), PayChannelEnum.WX_LITE.getCode())
                || StrUtil.hasBlank(payOrder.getChannelUserId(), payOrder.getChannelOrderNo())
                || express == null || StrUtil.isBlank(express.getCode())
                || items == null || items.isEmpty()) {
            return false;
        }
        return items.stream().allMatch(item -> StrUtil.isNotBlank(item.getSpuName()) && isPublicHttpUrl(item.getPicUrl()));
    }

    private SocialWxaWaybillTraceReqDTO buildTraceRequest(TradeOrderDO order, PayOrderRespDTO payOrder,
                                                           DeliveryExpressDO express, List<TradeOrderItemDO> items) {
        List<SocialWxaWaybillTraceReqDTO.GoodsItem> goods = items.stream()
                .map(item -> new SocialWxaWaybillTraceReqDTO.GoodsItem()
                        .setName(item.getSpuName()).setImageUrl(item.getPicUrl()))
                .toList();
        return new SocialWxaWaybillTraceReqDTO()
                .setOpenid(payOrder.getChannelUserId())
                .setReceiverPhone(order.getReceiverMobile())
                .setWaybillId(order.getLogisticsNo())
                .setTransactionId(payOrder.getChannelOrderNo())
                .setDeliveryId(express.getCode())
                .setOrderDetailPath("pages/order/detail?id=" + order.getId())
                .setGoods(goods);
    }

    private boolean isPublicHttpUrl(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || StrUtil.isBlank(host)) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            if ("localhost".equals(host) || host.endsWith(".localhost") || host.endsWith(".local")
                    || "::1".equals(host)) {
                return false;
            }
            return !isPrivateIpv4(host);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private boolean isPrivateIpv4(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        int[] numbers = new int[4];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (numbers[i] < 0 || numbers[i] > 255) {
                return true;
            }
        }
        return numbers[0] == 0 || numbers[0] == 10 || numbers[0] == 127 || numbers[0] >= 224
                || numbers[0] == 169 && numbers[1] == 254
                || numbers[0] == 172 && numbers[1] >= 16 && numbers[1] <= 31
                || numbers[0] == 192 && numbers[1] == 168
                || numbers[0] == 100 && numbers[1] >= 64 && numbers[1] <= 127;
    }

    private void logWechatFailure(Long orderId, Integer errorCode, String errorMessage) {
        log.warn("[ensureWechatWaybillToken][orderId({}) errorCode({}) errorMessage({})]",
                orderId, errorCode, StrUtil.blankToDefault(errorMessage, "unknown"));
    }

}
