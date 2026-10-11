package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ProductMerchantBusinessTypeSaveReqVO {

    private Long id;
    @NotBlank
    private String code;
    @NotBlank
    private String name;
    private Boolean enabled = Boolean.TRUE;
    private Integer sort = 0;
    @Valid
    private List<ProductMerchantQualificationRequirementSaveReqVO> requirements;
}
