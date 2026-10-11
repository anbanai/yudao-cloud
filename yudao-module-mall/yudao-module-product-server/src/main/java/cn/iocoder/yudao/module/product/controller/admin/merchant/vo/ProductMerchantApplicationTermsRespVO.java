package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ProductMerchantApplicationTermsRespVO {

    private Boolean available;
    private String agreementVersion;
    private String agreementUrl;
    private String privacyPolicyVersion;
    private String privacyPolicyUrl;
}
