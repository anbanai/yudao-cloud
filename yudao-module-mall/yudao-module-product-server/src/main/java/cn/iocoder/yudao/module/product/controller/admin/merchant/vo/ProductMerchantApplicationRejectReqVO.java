package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProductMerchantApplicationRejectReqVO {

    @NotNull
    private Long applicationId;
    @NotBlank
    private String reason;
}
