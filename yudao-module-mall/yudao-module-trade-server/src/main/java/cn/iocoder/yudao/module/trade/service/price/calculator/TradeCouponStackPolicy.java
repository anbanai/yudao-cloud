package cn.iocoder.yudao.module.trade.service.price.calculator;

final class TradeCouponStackPolicy {
    private TradeCouponStackPolicy() { }
    static String exclusion(Boolean canStack, Integer stackLimit, Boolean stackWithPromotion,
                            int appliedCount, boolean previousAllow, boolean hasOtherPromotion) {
        if (hasOtherPromotion && !Boolean.TRUE.equals(stackWithPromotion)) { return "此券不允许与其它营销优惠叠加"; }
        if (appliedCount > 0 && (!Boolean.TRUE.equals(canStack) || !previousAllow)) { return "所选优惠券不允许相互叠加"; }
        if (appliedCount + 1 > (stackLimit == null ? 1 : stackLimit)) { return "超过优惠券叠加数量上限"; }
        return null;
    }
}
