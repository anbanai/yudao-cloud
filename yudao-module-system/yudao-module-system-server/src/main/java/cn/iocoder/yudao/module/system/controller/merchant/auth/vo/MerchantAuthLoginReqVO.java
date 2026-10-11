package cn.iocoder.yudao.module.system.controller.merchant.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Schema(description = "商家认证 - 登录 Request VO")
@Data
public class MerchantAuthLoginReqVO {

    @Schema(description = "账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "seller100")
    @NotBlank(message = "账号不能为空")
    private String username;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "secret123")
    @NotBlank(message = "密码不能为空")
    private String password;

}
