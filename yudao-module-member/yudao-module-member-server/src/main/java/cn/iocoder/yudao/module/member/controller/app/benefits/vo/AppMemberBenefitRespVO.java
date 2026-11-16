package cn.iocoder.yudao.module.member.controller.app.benefits.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户 App - 会员权益")
public class AppMemberBenefitRespVO {
    private String code;
    private String name;
    private String description;
    private Integer actionType = 0;
    private Integer claimType = 0;
    private Integer availableQuantity = 0;
    private Boolean claimable = false;
    private Integer status = 0;
    private String periodEnd;
}
