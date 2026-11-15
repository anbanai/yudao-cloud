package cn.iocoder.yudao.module.trade.service.price.calculator;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.promotion.api.coupon.CouponApi;
import cn.iocoder.yudao.module.promotion.api.coupon.dto.CouponRespDTO;
import cn.iocoder.yudao.module.promotion.enums.common.PromotionDiscountTypeEnum;
import cn.iocoder.yudao.module.promotion.enums.common.PromotionProductScopeEnum;
import cn.iocoder.yudao.module.promotion.enums.common.PromotionTypeEnum;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderTypeEnum;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import jakarta.annotation.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.filterList;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.PRICE_CALCULATE_COUPON_CAN_NOT_USE;
import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.PRICE_CALCULATE_COUPON_NOT_MATCH_NORMAL_ORDER;

/**
 * 优惠劵的 {@link TradePriceCalculator} 实现类
 *
 * @author 芋道源码
 */
@Component
@Order(TradePriceCalculator.ORDER_COUPON)
public class TradeCouponPriceCalculator implements TradePriceCalculator {

    @Resource
    private CouponApi couponApi;

    @Override
    public void calculate(TradePriceCalculateReqBO param, TradePriceCalculateRespBO result) {
        java.util.LinkedHashSet<Long> selected = new java.util.LinkedHashSet<>();
        if (param.getCouponId() != null) { selected.add(param.getCouponId()); }
        if (param.getCouponIds() != null) { selected.addAll(param.getCouponIds()); }
        if (selected.size() > 10 || selected.contains(null)) { throw exception(PRICE_CALCULATE_COUPON_CAN_NOT_USE, "最多选择10张有效优惠券"); }
        result.setEffectiveCouponIds(new java.util.ArrayList<>());
        result.setExchangeOrder(false).setAllowPointStack(true);
        result.setCouponExclusionReasons(new java.util.LinkedHashMap<>());
        if (ObjectUtil.notEqual(result.getType(), TradeOrderTypeEnum.NORMAL.getType())) {
            if (!selected.isEmpty()) { throw exception(PRICE_CALCULATE_COUPON_NOT_MATCH_NORMAL_ORDER); }
            return;
        }
        List<CouponRespDTO> coupons = couponApi.getCouponListByUserId(param.getUserId(), CouponStatusEnum.UNUSED.getStatus()).getCheckedData();
        result.setCoupons(calculateCoupons(coupons, result));
        java.util.List<CouponRespDTO> applied = new java.util.ArrayList<>();
        for (Long id : selected) {
            CouponRespDTO coupon = CollUtil.findOne(coupons, item -> item.getId().equals(id));
            TradePriceCalculateRespBO.Coupon eligibility = coupon == null ? null : calculateCoupons(java.util.Collections.singletonList(coupon), result).get(0);
            String reason = eligibility == null ? "优惠券不存在" : Boolean.TRUE.equals(eligibility.getMatch()) ? null : eligibility.getMismatchReason();
            if (reason == null) {
                reason = TradeCouponStackPolicy.exclusion(coupon.getCanStack(), coupon.getStackLimit(), coupon.getStackWithPromotion(),
                        applied.size(), applied.stream().allMatch(c -> Boolean.TRUE.equals(c.getCanStack())
                                && (c.getStackLimit() == null ? 1 : c.getStackLimit()) > applied.size()),
                        result.getItems().stream().anyMatch(i -> Boolean.TRUE.equals(i.getSelected())
                                && ((i.getDiscountPrice() != null && i.getDiscountPrice() > 0) || (i.getVipPrice() != null && i.getVipPrice() > 0))));
            }
            if (reason != null) {
                result.getCouponExclusionReasons().put(id, reason);
                if (Boolean.TRUE.equals(param.getStrictCouponSelection())) { throw exception(PRICE_CALCULATE_COUPON_CAN_NOT_USE, reason); }
                continue;
            }
            List<TradePriceCalculateRespBO.OrderItem> orderItems = filterMatchCouponOrderItems(result, coupon);
            int total = TradePriceCalculatorHelper.calculateTotalPayPrice(orderItems);
            int amount = getCouponPrice(coupon, total);
            List<Integer> shares = TradePriceCalculatorHelper.dividePrice(orderItems, amount);
            TradePriceCalculatorHelper.addPromotion(result, orderItems, id, coupon.getName(), PromotionTypeEnum.COUPON.getType(),
                    StrUtil.format("优惠券：省 {} 元", TradePriceCalculatorHelper.formatPrice(amount)), shares);
            for (int i = 0; i < orderItems.size(); i++) {
                TradePriceCalculateRespBO.OrderItem item = orderItems.get(i);
                item.setCouponPrice((item.getCouponPrice() == null ? 0 : item.getCouponPrice()) + shares.get(i));
                TradePriceCalculatorHelper.recountPayPrice(item);
            }
            TradePriceCalculatorHelper.recountAllPrice(result);
            applied.add(coupon);
            if (Integer.valueOf(2).equals(coupon.getCouponType())) { result.setExchangeOrder(true); }
            if (!Boolean.TRUE.equals(coupon.getStackWithPromotion())) { result.setAllowPointStack(false); }
            result.getEffectiveCouponIds().add(id);
        }
        if (!result.getEffectiveCouponIds().isEmpty()) { result.setCouponId(result.getEffectiveCouponIds().get(0)); }
    }

    /**
     * 计算用户的优惠劵列表（可用 + 不可用）
     *
     * @param coupons 优惠劵
     * @param result 计算结果
     * @return 优惠劵列表
     */
    private List<TradePriceCalculateRespBO.Coupon> calculateCoupons(List<CouponRespDTO> coupons,
                                                                    TradePriceCalculateRespBO result) {
        return convertList(coupons, coupon -> {
            TradePriceCalculateRespBO.Coupon matchCoupon = BeanUtils.toBean(coupon, TradePriceCalculateRespBO.Coupon.class);
            if (!LocalDateTimeUtils.isBetween(coupon.getValidStartTime(), coupon.getValidEndTime())) {
                return matchCoupon.setMatch(false).setMismatchReason("优惠券不在有效期内");
            }
            if (Integer.valueOf(2).equals(coupon.getCouponType())) {
                java.util.List<TradePriceCalculateRespBO.OrderItem> selectedItems = filterList(result.getItems(), i -> Boolean.TRUE.equals(i.getSelected()));
                if (selectedItems.size() != 1 || selectedItems.get(0).getCount() != 1
                        || !java.util.Objects.equals(selectedItems.get(0).getSpuId(), coupon.getExchangeSpuId())
                        || !java.util.Objects.equals(selectedItems.get(0).getSkuId(), coupon.getExchangeSkuId())) {
                    return matchCoupon.setMatch(false).setMismatchReason("兑换券仅能兑换绑定SKU的一件商品");
                }
                return matchCoupon.setMatch(true);
            }
            // 1.1 优惠劵未到使用时间
            if (LocalDateTimeUtils.afterNow(coupon.getValidStartTime())) {
                return matchCoupon.setMatch(false).setMismatchReason("优惠劵未到使用时间");
            }
            // 1.2 优惠劵没有匹配的商品
            List<TradePriceCalculateRespBO.OrderItem> orderItems = filterMatchCouponOrderItems(result, coupon);
            if (CollUtil.isEmpty(orderItems)) {
                return matchCoupon.setMatch(false).setMismatchReason("优惠劵没有匹配的商品");
            }
            // 1.3 差 %1$,.2f 元可用优惠劵
            Integer totalPayPrice = TradePriceCalculatorHelper.calculateTotalPayPrice(orderItems);
            if (totalPayPrice < coupon.getUsePrice()) {
                return matchCoupon.setMatch(false)
                        .setMismatchReason(String.format("差 %1$,.2f 元可用优惠劵", (coupon.getUsePrice() - totalPayPrice) / 100D));
            }
            // 1.4 优惠金额超过订单金额
            Integer couponPrice = getCouponPrice(coupon, totalPayPrice);
            if (couponPrice <= 0 || totalPayPrice <= 0 || couponPrice >= totalPayPrice) {
                return matchCoupon.setMatch(false).setMismatchReason("优惠金额超过订单金额");
            }

            // 2. 满足条件
            return matchCoupon.setMatch(true);
        });
    }

    private Integer getCouponPrice(CouponRespDTO coupon, Integer totalPayPrice) {
        if (Integer.valueOf(2).equals(coupon.getCouponType())) { return totalPayPrice; }
        if (PromotionDiscountTypeEnum.PRICE.getType().equals(coupon.getDiscountType())) { // 减价
            return Math.min(coupon.getDiscountPrice(), totalPayPrice);
        } else if (PromotionDiscountTypeEnum.PERCENT.getType().equals(coupon.getDiscountType())) { // 打折
            int couponPrice = totalPayPrice - (totalPayPrice * coupon.getDiscountPercent() / 100);
            return coupon.getDiscountLimitPrice() == null ? couponPrice
                    : Math.min(couponPrice, coupon.getDiscountLimitPrice()); // 优惠上限
        }
        throw new IllegalArgumentException(String.format("优惠劵(%s) 的优惠类型不正确", coupon));
    }

    /**
     * 获得优惠劵可使用的订单项（商品）列表
     *
     * @param result 计算结果
     * @param coupon 优惠劵
     * @return 订单项（商品）列表
     */
    private List<TradePriceCalculateRespBO.OrderItem> filterMatchCouponOrderItems(TradePriceCalculateRespBO result,
                                                                                  CouponRespDTO coupon) {
        Predicate<TradePriceCalculateRespBO.OrderItem> matchPredicate = TradePriceCalculateRespBO.OrderItem::getSelected;
        if (PromotionProductScopeEnum.SPU.getScope().equals(coupon.getProductScope())) {
            matchPredicate = matchPredicate // 额外加如下条件
                    .and(orderItem -> coupon.getProductScopeValues().contains(orderItem.getSpuId()));
        } else if (PromotionProductScopeEnum.CATEGORY.getScope().equals(coupon.getProductScope())) {
            matchPredicate = matchPredicate // 额外加如下条件
                    .and(orderItem -> coupon.getProductScopeValues().contains(orderItem.getCategoryId()));
        }
        return filterList(result.getItems(), matchPredicate);
    }

}
