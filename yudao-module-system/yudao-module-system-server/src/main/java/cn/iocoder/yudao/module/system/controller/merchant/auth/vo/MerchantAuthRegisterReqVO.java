package cn.iocoder.yudao.module.system.controller.merchant.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Schema(description = "商家认证 - 注册 Request VO")
@Data
public class MerchantAuthRegisterReqVO {

    @Schema(description = "账号", requiredMode = Schema.RequiredMode.REQUIRED, example = "seller100")
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[a-zA-Z0-9]{4,30}$", message = "账号由数字、字母组成")
    @Size(min = 4, max = 30, message = "账号长度为 4-30 个字符")
    private String username;

    @Schema(description = "昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "Tea Seller")
    @NotBlank(message = "昵称不能为空")
    @Size(max = 30, message = "昵称长度不能超过 30 个字符")
    private String nickname;

    @Schema(description = "密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "secret123")
    @NotBlank(message = "密码不能为空")
    @Length(min = 8, max = 64, message = "密码长度为 8-64 位")
    private String password;

    @Schema(description = "手机号", requiredMode = Schema.RequiredMode.REQUIRED, example = "15601691300")
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^[0-9+() -]{11,30}$", message = "手机号格式不正确")
    private String mobile;

}
