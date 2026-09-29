package cn.iocoder.yudao.module.member.service.benefit;

import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberBenefitDefinitionDO;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberBenefitLedgerDO;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberLevelBenefitDO;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberUserBenefitDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.benefit.vo.MemberBenefitAccountPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.benefit.vo.MemberBenefitLedgerPageReqVO;

import java.util.List;

public interface MemberBenefitAdminService {

    List<MemberBenefitDefinitionDO> getDefinitions();

    Long createDefinition(MemberBenefitDefinitionDO definition);

    void updateDefinition(MemberBenefitDefinitionDO definition);

    void deleteDefinition(Long id);

    List<MemberLevelBenefitDO> getLevelBindings(Long levelId);

    void saveLevelBinding(MemberLevelBenefitDO binding);

    void deleteLevelBinding(Long id);

    List<MemberUserBenefitDO> getUserAccounts(Long userId);

    PageResult<MemberUserBenefitDO> getUserAccountPage(MemberBenefitAccountPageReqVO reqVO);

    List<MemberBenefitLedgerDO> getLedgers(Long userId, Long accountId);

    PageResult<MemberBenefitLedgerDO> getLedgerPage(MemberBenefitLedgerPageReqVO reqVO);

    void grant(Long accountId, Integer quantity, String reason, String bizId, Long operatorId);

    void revoke(Long accountId, String reason, String bizId, Long operatorId);

    void adjust(Long accountId, Integer quantity, String reason, String bizId, Long operatorId);
}
