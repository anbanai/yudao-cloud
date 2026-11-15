package cn.iocoder.yudao.module.promotion.controller.admin.coupon.vo;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
/** Exchange uses the normal trade order endpoint (couponId + exactly one bound SKU). */
@Data
public class CouponExchangeReqVO {
    @NotNull private Long couponId;
    @NotNull private Long skuId;
}
