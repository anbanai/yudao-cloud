package cn.iocoder.yudao.module.product.enums.merchant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 商家入驻状态。 */
@Getter
@AllArgsConstructor
public enum ProductMerchantStatusEnum {

    DRAFT(0, "草稿"),
    PENDING(10, "待审核"),
    APPROVED(20, "已通过"),
    REJECTED(30, "已驳回"),
    FROZEN(40, "已冻结");

    private final Integer status;
    private final String name;

    public static boolean isAvailable(Integer status) {
        return APPROVED.status.equals(status);
    }
}
