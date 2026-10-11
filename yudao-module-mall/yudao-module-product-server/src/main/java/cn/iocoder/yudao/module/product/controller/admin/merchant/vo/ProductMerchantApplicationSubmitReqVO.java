package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ProductMerchantApplicationSubmitReqVO {

    @NotBlank(message = "商家协议版本不能为空")
    private String agreementVersion;
    @NotBlank(message = "隐私政策版本不能为空")
    private String privacyPolicyVersion;
    @NotNull(message = "请确认已阅读并同意协议")
    @AssertTrue(message = "请确认已阅读并同意协议")
    private Boolean accepted;
}
