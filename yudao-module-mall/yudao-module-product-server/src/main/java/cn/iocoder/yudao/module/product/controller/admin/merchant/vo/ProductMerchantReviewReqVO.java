package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 商家审核 Request VO")
@Data
public class ProductMerchantReviewReqVO {
    @NotNull(message = "商家编号不能为空")
    private Long id;
    @Schema(description = "驳回原因")
    private String reason;
}
