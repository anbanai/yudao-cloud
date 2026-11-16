package cn.iocoder.yudao.module.member.controller.app.benefits.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户 App - 会员等级及对应权益")
public class AppMemberBenefitsLevelRespVO {
    private Integer level;
    private String name;
    private Integer experience;
    private List<AppMemberBenefitRespVO> benefits;
}
