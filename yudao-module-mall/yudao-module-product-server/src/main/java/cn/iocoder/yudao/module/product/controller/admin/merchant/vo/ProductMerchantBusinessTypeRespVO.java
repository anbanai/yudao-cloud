package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import lombok.Data;

import java.util.List;

@Data
public class ProductMerchantBusinessTypeRespVO {

    private Long id;
    private String code;
    private String name;
    private Boolean enabled;
    private Integer sort;
    private List<ProductMerchantQualificationRequirementSaveReqVO> requirements;
}
