package cn.iocoder.yudao.module.member.controller.admin.benefit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 会员权益账户操作 Request VO")
@Data
public class MemberBenefitAccountOperateReqVO {
    @NotNull(message = "权益账户不能为空")
    private Long accountId;
    @NotBlank(message = "操作原因不能为空")
    private String reason;
    @NotBlank(message = "业务单号不能为空")
    private String bizId;
}
