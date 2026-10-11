package cn.iocoder.yudao.module.system.controller.merchant.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

@Schema(description = "商家认证 - 更新个人资料 Request VO")
@Data
public class MerchantUserProfileUpdateReqVO {

    @Schema(description = "昵称", example = "Tea Seller")
    @Size(max = 30, message = "昵称长度不能超过 30 个字符")
    private String nickname;

    @Schema(description = "邮箱", example = "seller@example.com")
    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过 50 个字符")
    private String email;

    @Schema(description = "手机号", example = "15601691300")
    @Length(min = 11, max = 30, message = "手机号长度应为 11-30 位")
    private String mobile;

    @Schema(description = "头像地址")
    @URL(message = "头像地址格式不正确")
    @Size(max = 512, message = "头像地址长度不能超过 512 个字符")
    private String avatar;

}
