package cn.iocoder.yudao.module.member.controller.admin.benefit.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 会员权益流水分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class MemberBenefitLedgerPageReqVO extends PageParam {
    @Schema(description = "用户编号", example = "1024")
    private Long userId;
    @Schema(description = "权益账户编号", example = "2048")
    private Long accountId;
}
