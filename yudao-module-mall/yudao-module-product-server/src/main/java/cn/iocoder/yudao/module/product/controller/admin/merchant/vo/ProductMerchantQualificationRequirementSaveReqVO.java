package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductMerchantQualificationRequirementSaveReqVO {

    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private Boolean required = Boolean.TRUE;
    private Boolean certificateNoRequired = Boolean.FALSE;
    @Size(max = 128)
    private String certificateNoPattern;
    private Boolean validityRequired = Boolean.FALSE;
    private Boolean enabled = Boolean.TRUE;
    private Integer sort = 0;
}
