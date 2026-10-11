package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ProductMerchantLegacyBindReqVO {
    @NotNull
    @Positive
    private Long merchantId;
    @NotNull
    @Positive
    private Long accountId;
}
