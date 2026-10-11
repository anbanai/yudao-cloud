package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ProductMerchantApplicationDraftReqVO {

    @Size(max = 10)
    private List<String> businessTypes;
    @Size(max = 100)
    private String name;
    @Size(max = 100)
    private String storeName;
    @Size(max = 1024)
    private String logoUrl;
    @Size(max = 200)
    private String companyName;
    @Size(max = 64)
    private String creditCode;
    @Size(max = 100)
    private String legalPerson;
    @Size(max = 100)
    private String contactName;
    @Size(max = 32)
    private String contactMobile;
    @Valid
    @Size(max = 50)
    private List<ProductMerchantApplicationQualificationReqVO> qualifications;
}
