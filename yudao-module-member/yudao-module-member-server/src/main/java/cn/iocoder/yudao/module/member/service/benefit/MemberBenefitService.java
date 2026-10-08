package cn.iocoder.yudao.module.member.service.benefit;
import cn.iocoder.yudao.module.member.controller.app.benefit.vo.AppMemberBenefitOverviewRespVO;
import cn.iocoder.yudao.module.member.controller.app.benefit.vo.AppMemberBenefitRespVO;
import java.util.List;
public interface MemberBenefitService {
    AppMemberBenefitOverviewRespVO getOverview(Long userId);
    List<AppMemberBenefitRespVO> getRecords(Long userId);
    List<AppMemberBenefitRespVO> getLevelBenefits(Long levelId);
    void claim(Long userId, Long accountId, String requestId);
    void use(Long userId, Long accountId, String bizId);
    void processPeriods();
}
