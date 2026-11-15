package cn.iocoder.yudao.module.trade.service.order.handler;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.promotion.api.coupon.CouponApi;
import cn.iocoder.yudao.module.promotion.api.coupon.dto.CouponUseReqDTO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 优惠劵的 {@link TradeOrderHandler} 实现类
 *
 * @author 芋道源码
 */
@Component
@Slf4j
public class TradeCouponOrderHandler implements TradeOrderHandler {

    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private TradeOrderUpdateService orderUpdateService;
    @Resource
    private TradeOrderQueryService orderQueryService;

    @Resource
    private CouponApi couponApi;

    @Override
    public void afterOrderCreate(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        if (couponIds(order).isEmpty()) {
            return;
        }
        // Register before the RPC so a timeout after a remote commit is compensated as well.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCompletion(int status) {
                            if (status != STATUS_ROLLED_BACK) { return; }
                            try { couponApi.returnCoupons(couponIds(order), order.getId()).checkError(); }
                            catch (Exception ex) { log.error("[couponRollback][order({}) 即时退券失败，孤立扣券任务将重试]", order.getId(), ex); }
                        }
                    });
        }
        // 不在前置扣减的原因，是因为优惠劵要记录使用的订单号
        couponApi.useCoupons(couponIds(order), order.getUserId(), order.getId()).checkError();
    }

    private List<Long> couponIds(TradeOrderDO order) {
        return order.getCouponIds() != null && !order.getCouponIds().isEmpty() ? order.getCouponIds()
                : order.getCouponId() == null || order.getCouponId() <= 0 ? java.util.Collections.emptyList() : java.util.Collections.singletonList(order.getCouponId());
    }

    @Override
    public void beforeOrderCreate(TradeOrderDO order, List<TradeOrderItemDO> items) {
        if (!couponIds(order).isEmpty() && order.getPayPrice() == 0) {
            order.setPayStatus(true).setPayTime(java.time.LocalDateTime.now())
                    .setStatus(cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum.UNDELIVERED.getStatus());
        }
    }

    @Override
    public void afterDeliveryOrder(TradeOrderDO order) { couponApi.fulfillExchange(order.getId()).checkError(); }

    @Override
    public void afterCancelOrderItem(TradeOrderDO order, TradeOrderItemDO item) {
        // Exchange orders contain exactly one bound SKU, so full item refund returns the exchange coupon.
        if (order.getProductCount() == 1 && !couponIds(order).isEmpty()) {
            couponApi.returnCoupons(couponIds(order), order.getId()).checkError();
        }
    }

    @Override
    public void afterPayOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        if (CollUtil.isEmpty(order.getGiveCouponTemplateCounts())) {
            return;
        }
        // 赠送优惠券
        try {
            List<Long> couponIds = couponApi.takeCouponsByAdmin(order.getGiveCouponTemplateCounts(), order.getUserId()).getCheckedData();
            if (CollUtil.isEmpty(couponIds)) {
                return;
            }
            orderUpdateService.updateOrderGiveCouponIds(order.getUserId(), order.getId(), couponIds);
        } catch (Exception e) {
            log.error("[afterPayOrder][order({}) 赠送优惠券({})失败，需要手工补偿]", order.getId(), order.getGiveCouponTemplateCounts(), e);
        }
    }

    @Override
    public void afterCancelOrder(TradeOrderDO order, List<TradeOrderItemDO> orderItems) {
        // 情况一：退还订单使用的优惠券
        if (!couponIds(order).isEmpty()) {
            // 退回优惠劵
            couponApi.returnCoupons(couponIds(order), order.getId()).checkError();
        }
        // 情况二：收回赠送的优惠券
        if (CollUtil.isEmpty(order.getGiveCouponIds())) {
            return;
        }
        couponApi.invalidateCouponsByAdmin(order.getGiveCouponIds(), order.getUserId()).checkError();
    }

}
