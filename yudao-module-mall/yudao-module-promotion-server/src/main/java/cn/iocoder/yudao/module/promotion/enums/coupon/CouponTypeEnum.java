package cn.iocoder.yudao.module.promotion.enums.coupon;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CouponTypeEnum {
    DISCOUNT(1), EXCHANGE(2);
    private final int type;
}
