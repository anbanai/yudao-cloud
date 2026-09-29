package cn.iocoder.yudao.module.member.controller.admin.benefit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 会员权益账户调整 Request VO")
@Data
public class MemberBenefitAccountAdjustReqVO {
    @NotNull(message = "权益账户不能为空")
    private Long accountId;
    @NotNull(message = "调整数量不能为空")
    private Integer quantity;
    @NotBlank(message = "调整原因不能为空")
    private String reason;
    @NotBlank(message = "业务单号不能为空")
    private String bizId;
}
