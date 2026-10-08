package cn.iocoder.yudao.module.member.controller.app.benefit.vo;
import lombok.Data;
import lombok.experimental.Accessors;
import java.util.List;
@Data
@Accessors(chain = true)
public class AppMemberBenefitOverviewRespVO {
    private String levelName;
    private Integer level;
    private Integer pointTradeGiveMultiplier;
    private Integer point;
    private List<AppMemberBenefitRespVO> benefits;
}
