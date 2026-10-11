package cn.iocoder.yudao.module.system.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

@Schema(description = "管理后台 - 商家账号密码重置 Request VO")
@Data
public class MerchantUserUpdatePasswordReqVO {

    @Schema(description = "账号编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "账号编号不能为空")
    private Long id;

    @Schema(description = "新密码", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456")
    @NotEmpty(message = "密码不能为空")
    @Length(min = 4, max = 16, message = "密码长度为 4-16 位")
    private String password;

}
