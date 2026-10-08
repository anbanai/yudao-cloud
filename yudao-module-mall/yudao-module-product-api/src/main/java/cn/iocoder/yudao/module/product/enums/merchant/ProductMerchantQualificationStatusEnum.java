package cn.iocoder.yudao.module.product.enums.merchant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 商家资质审核状态。 */
@Getter
@AllArgsConstructor
public enum ProductMerchantQualificationStatusEnum {

    PENDING(10, "待审核"),
    APPROVED(20, "已通过"),
    REJECTED(30, "已驳回");

    private final Integer status;
    private final String name;
}
