package cn.iocoder.yudao.module.member.controller.app.benefits.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户 App - 会员权益")
public class AppMemberBenefitRespVO {
    private String code;
    private String name;
    private String description;
}
