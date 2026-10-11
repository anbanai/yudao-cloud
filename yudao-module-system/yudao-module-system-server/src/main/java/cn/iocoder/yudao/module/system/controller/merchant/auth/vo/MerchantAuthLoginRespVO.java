package cn.iocoder.yudao.module.system.controller.merchant.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "商家认证 - 登录 Response VO")
@Data
public class MerchantAuthLoginRespVO {

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long userId;

    @Schema(description = "商家编号", example = "2048")
    private Long merchantId;

    @Schema(description = "访问令牌", requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessToken;

    @Schema(description = "刷新令牌", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;

    @Schema(description = "过期时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime expiresTime;

}
