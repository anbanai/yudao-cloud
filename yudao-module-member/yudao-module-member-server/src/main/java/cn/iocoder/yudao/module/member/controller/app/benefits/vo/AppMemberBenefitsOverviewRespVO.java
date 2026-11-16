package cn.iocoder.yudao.module.member.controller.app.benefits.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户 App - 会员权益概览")
public class AppMemberBenefitsOverviewRespVO {
    private Integer level;
    private String levelName;
    private Integer point;
    private Integer experience;
    private Integer nextExperience;
    private Integer pointTradeGiveMultiplier = 1000;
    private List<AppMemberBenefitRespVO> benefits;
    private List<AppMemberBenefitsLevelRespVO> levels;
}
