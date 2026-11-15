package cn.iocoder.yudao.module.promotion.controller.admin.coupon.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;

@Data
public class CouponPromotionCreateReqVO {
    @NotNull private Long templateId;
    private String source;
    private String channel;
    private String miniProgramPath;
    @Schema(description = "幂等键") @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 64) private String idempotencyKey;
}
