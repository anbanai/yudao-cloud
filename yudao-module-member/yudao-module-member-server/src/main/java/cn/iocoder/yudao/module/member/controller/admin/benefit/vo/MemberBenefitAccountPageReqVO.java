package cn.iocoder.yudao.module.member.controller.admin.benefit.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 会员权益账户分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberBenefitAccountPageReqVO extends PageParam {
    @Schema(description = "用户编号", example = "1024")
    private Long userId;
}
