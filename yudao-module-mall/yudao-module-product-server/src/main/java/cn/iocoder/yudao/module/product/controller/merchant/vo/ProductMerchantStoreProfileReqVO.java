package cn.iocoder.yudao.module.product.controller.merchant.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductMerchantStoreProfileReqVO {

    @NotBlank
    @Size(max = 128)
    private String storeName;

    @Size(max = 1024)
    private String logoUrl;

    @Size(max = 512)
    private String description;
}
