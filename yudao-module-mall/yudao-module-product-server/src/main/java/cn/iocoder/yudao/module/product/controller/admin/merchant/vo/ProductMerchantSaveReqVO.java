package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "管理后台 - 商家新增/更新 Request VO")
@Data
public class ProductMerchantSaveReqVO {

    @Schema(description = "商家编号", example = "1")
    private Long id;
    @Schema(description = "商家名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商家名称不能为空")
    private String name;
    @Schema(description = "店铺名称")
    private String storeName;
    @Schema(description = "店铺 logo")
    private String logoUrl;
    @Schema(description = "企业主体名称")
    private String companyName;
    @Schema(description = "统一社会信用代码")
    private String creditCode;
    @Schema(description = "法人")
    private String legalPerson;
    @Schema(description = "联系人")
    private String contactName;
    @Schema(description = "联系手机")
    private String contactMobile;
}
