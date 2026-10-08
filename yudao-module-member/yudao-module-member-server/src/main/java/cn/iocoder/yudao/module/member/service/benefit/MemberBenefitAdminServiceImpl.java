package cn.iocoder.yudao.module.member.service.benefit;

import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberBenefitDefinitionDO;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberBenefitLedgerDO;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberLevelBenefitDO;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.MemberUserBenefitDO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.member.controller.admin.benefit.vo.MemberBenefitAccountPageReqVO;
import cn.iocoder.yudao.module.member.controller.admin.benefit.vo.MemberBenefitLedgerPageReqVO;
import cn.iocoder.yudao.module.member.dal.mysql.benefit.MemberBenefitDefinitionMapper;
import cn.iocoder.yudao.module.member.dal.mysql.benefit.MemberBenefitLedgerMapper;
import cn.iocoder.yudao.module.member.dal.mysql.benefit.MemberLevelBenefitMapper;
import cn.iocoder.yudao.module.member.dal.mysql.benefit.MemberUserBenefitMapper;
import cn.iocoder.yudao.module.member.dal.mysql.level.MemberLevelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemberBenefitAdminServiceImpl implements MemberBenefitAdminService {

    @Resource
    private MemberBenefitDefinitionMapper definitionMapper;
    @Resource
    private MemberLevelBenefitMapper levelBenefitMapper;
    @Resource
    private MemberUserBenefitMapper userBenefitMapper;
    @Resource
    private MemberBenefitLedgerMapper ledgerMapper;
    @Resource
    private MemberLevelMapper levelMapper;

    @Override
    public List<MemberBenefitDefinitionDO> getDefinitions() {
        return definitionMapper.selectList(new LambdaQueryWrapper<MemberBenefitDefinitionDO>()
                .orderByAsc(MemberBenefitDefinitionDO::getSort).orderByDesc(MemberBenefitDefinitionDO::getId));
    }

    @Override
    @Transactional
    public Long createDefinition(MemberBenefitDefinitionDO definition) {
        validateDefinition(definition);
        // Never accept persistence metadata from the request body.
        definition.setId(null).setCreator(null).setUpdater(null)
                .setCreateTime(null).setUpdateTime(null).setDeleted(null);
        definitionMapper.insert(definition);
        return definition.getId();
    }

    @Override
    @Transactional
    public void updateDefinition(MemberBenefitDefinitionDO definition) {
        if (definition.getId() == null) {
            throw new IllegalArgumentException("权益定义不存在");
        }
        validateDefinition(definition);
        MemberBenefitDefinitionDO existing = definitionMapper.selectById(definition.getId());
        if (existing == null) {
            throw new IllegalArgumentException("权益定义不存在");
        }
        existing.setName(definition.getName()).setType(definition.getType())
                .setDescription(definition.getDescription()).setIcon(definition.getIcon())
                .setActionType(definition.getActionType()).setCouponTemplateId(definition.getCouponTemplateId())
                .setStatus(definition.getStatus()).setSort(definition.getSort());
        definitionMapper.updateById(existing);
    }

    @Override
    @Transactional
    public void deleteDefinition(Long id) {
        if (definitionMapper.selectById(id) == null) {
            throw new IllegalArgumentException("权益定义不存在");
        }
        definitionMapper.deleteById(id);
    }

    @Override
    public List<MemberLevelBenefitDO> getLevelBindings(Long levelId) {
        LambdaQueryWrapper<MemberLevelBenefitDO> query = new LambdaQueryWrapper<MemberLevelBenefitDO>()
                .orderByAsc(MemberLevelBenefitDO::getSort).orderByDesc(MemberLevelBenefitDO::getId);
        if (levelId != null) {
            query.eq(MemberLevelBenefitDO::getLevelId, levelId);
        }
        return levelBenefitMapper.selectList(query);
    }

    @Override
    @Transactional
    public void saveLevelBinding(MemberLevelBenefitDO binding) {
        validateBinding(binding);
        if (binding.getId() == null) {
            binding.setCreator(null).setUpdater(null)
                    .setCreateTime(null).setUpdateTime(null).setDeleted(null);
            levelBenefitMapper.insert(binding);
        } else {
            MemberLevelBenefitDO existing = levelBenefitMapper.selectById(binding.getId());
            if (existing == null) {
                throw new IllegalArgumentException("等级权益绑定不存在");
            }
            existing.setLevelId(binding.getLevelId()).setBenefitId(binding.getBenefitId())
                    .setQuantity(binding.getQuantity()).setPeriodType(binding.getPeriodType())
                    .setClaimType(binding.getClaimType()).setValidityDays(binding.getValidityDays())
                    .setBirthdayAdvanceDays(binding.getBirthdayAdvanceDays()).setStatus(binding.getStatus())
                    .setSort(binding.getSort());
            levelBenefitMapper.updateById(existing);
        }
    }

    @Override
    @Transactional
    public void deleteLevelBinding(Long id) {
        levelBenefitMapper.deleteById(id);
    }

    @Override
    public List<MemberUserBenefitDO> getUserAccounts(Long userId) {
        LambdaQueryWrapper<MemberUserBenefitDO> query = new LambdaQueryWrapper<MemberUserBenefitDO>()
                .orderByDesc(MemberUserBenefitDO::getId);
        if (userId != null) {
            query.eq(MemberUserBenefitDO::getUserId, userId);
        }
        return userBenefitMapper.selectList(query);
    }

    @Override
    public PageResult<MemberUserBenefitDO> getUserAccountPage(MemberBenefitAccountPageReqVO reqVO) {
        LambdaQueryWrapper<MemberUserBenefitDO> query = new LambdaQueryWrapper<MemberUserBenefitDO>()
                .eq(reqVO.getUserId() != null, MemberUserBenefitDO::getUserId, reqVO.getUserId())
                .orderByDesc(MemberUserBenefitDO::getId);
        return userBenefitMapper.selectPage(reqVO, query);
    }

    @Override
    public List<MemberBenefitLedgerDO> getLedgers(Long userId, Long accountId) {
        LambdaQueryWrapper<MemberBenefitLedgerDO> query = new LambdaQueryWrapper<MemberBenefitLedgerDO>()
                .orderByDesc(MemberBenefitLedgerDO::getId);
        if (userId != null) {
            query.eq(MemberBenefitLedgerDO::getUserId, userId);
        }
        if (accountId != null) {
            query.eq(MemberBenefitLedgerDO::getAccountId, accountId);
        }
        return ledgerMapper.selectList(query);
    }

    @Override
    public PageResult<MemberBenefitLedgerDO> getLedgerPage(MemberBenefitLedgerPageReqVO reqVO) {
        LambdaQueryWrapper<MemberBenefitLedgerDO> query = new LambdaQueryWrapper<MemberBenefitLedgerDO>()
                .eq(reqVO.getUserId() != null, MemberBenefitLedgerDO::getUserId, reqVO.getUserId())
                .eq(reqVO.getAccountId() != null, MemberBenefitLedgerDO::getAccountId, reqVO.getAccountId())
                .orderByDesc(MemberBenefitLedgerDO::getId);
        return ledgerMapper.selectPage(reqVO, query);
    }

    @Override
    @Transactional
    public void grant(Long accountId, Integer quantity, String reason, String bizId, Long operatorId) {
        if (quantity == null || quantity <= 0) throw new IllegalArgumentException("补发数量必须大于 0");
        if (ledgerMapper.existsByBizId(bizId, 6)) return;
        MemberUserBenefitDO account = requireAccount(accountId);
        if (userBenefitMapper.grant(accountId, quantity) != 1) throw new IllegalStateException("权益账户不可补发");
        insertLedger(account, 6, quantity, bizId, reason, operatorId);
    }

    @Override
    @Transactional
    public void revoke(Long accountId, String reason, String bizId, Long operatorId) {
        if (ledgerMapper.existsByBizId(bizId, 5)) return;
        MemberUserBenefitDO account = requireAccount(accountId);
        int quantity = account.getAvailableQuantity() == null ? 0 : account.getAvailableQuantity();
        if (quantity <= 0) throw new IllegalStateException("权益账户没有可撤销额度");
        if (userBenefitMapper.revoke(accountId, quantity) != 1) throw new IllegalStateException("权益账户不可撤销");
        insertLedger(account, 5, quantity, bizId, reason, operatorId);
    }

    @Override
    @Transactional
    public void adjust(Long accountId, Integer quantity, String reason, String bizId, Long operatorId) {
        if (quantity == null || quantity == 0) throw new IllegalArgumentException("调整数量不能为 0");
        if (ledgerMapper.existsByBizId(bizId, 7)) return;
        MemberUserBenefitDO account = requireAccount(accountId);
        if (userBenefitMapper.adjust(accountId, quantity) != 1) throw new IllegalStateException("调整后可用额度不能为负数");
        insertLedger(account, 7, quantity, bizId, reason, operatorId);
    }

    private MemberUserBenefitDO requireAccount(Long accountId) {
        MemberUserBenefitDO account = userBenefitMapper.selectById(accountId);
        if (account == null) throw new IllegalArgumentException("权益账户不存在");
        return account;
    }

    private void validateDefinition(MemberBenefitDefinitionDO definition) {
        if (definition == null || definition.getCode() == null || definition.getCode().isBlank()
                || definition.getName() == null || definition.getName().isBlank()) {
            throw new IllegalArgumentException("权益编码和名称不能为空");
        }
        if (definition.getType() == null || definition.getType() < 1 || definition.getType() > 4) {
            throw new IllegalArgumentException("权益类型不合法");
        }
        if (definition.getActionType() == null || definition.getActionType() < 0 || definition.getActionType() > 2) {
            throw new IllegalArgumentException("权益动作类型不合法");
        }
        if (definition.getActionType() == 1 && (definition.getCouponTemplateId() == null
                || definition.getCouponTemplateId() <= 0)) {
            throw new IllegalArgumentException("券类权益必须配置优惠券模板");
        }
        if (definition.getStatus() == null || (definition.getStatus() != 0 && definition.getStatus() != 1)) {
            throw new IllegalArgumentException("权益状态不合法");
        }
    }

    private void validateBinding(MemberLevelBenefitDO binding) {
        if (binding == null || binding.getLevelId() == null || levelMapper.selectById(binding.getLevelId()) == null) {
            throw new IllegalArgumentException("会员等级不存在");
        }
        if (binding.getBenefitId() == null || definitionMapper.selectById(binding.getBenefitId()) == null) {
            throw new IllegalArgumentException("权益定义不存在");
        }
        if (binding.getQuantity() == null || binding.getQuantity() <= 0) {
            throw new IllegalArgumentException("权益数量必须大于 0");
        }
        if (binding.getPeriodType() == null || binding.getPeriodType() < 1 || binding.getPeriodType() > 4) {
            throw new IllegalArgumentException("权益周期不合法");
        }
        if (binding.getClaimType() == null || (binding.getClaimType() != 0 && binding.getClaimType() != 1)) {
            throw new IllegalArgumentException("权益领取方式不合法");
        }
        if (binding.getValidityDays() == null || binding.getValidityDays() <= 0) {
            throw new IllegalArgumentException("权益有效期必须大于 0");
        }
        if (binding.getBirthdayAdvanceDays() != null
                && (binding.getBirthdayAdvanceDays() < 0 || binding.getBirthdayAdvanceDays() > 365)) {
            throw new IllegalArgumentException("生日提前天数范围为 0 到 365");
        }
    }

    private void insertLedger(MemberUserBenefitDO account, int eventType, int quantity,
                              String bizId, String reason, Long operatorId) {
        validateOperation(bizId, reason);
        ledgerMapper.insert(MemberBenefitLedgerDO.builder().userId(account.getUserId()).accountId(account.getId())
                .benefitId(account.getBenefitId()).eventType(eventType).quantity(quantity).bizId(bizId)
                .reason(reason).operatorId(operatorId).build());
    }

    private void validateOperation(String bizId, String reason) {
        if (bizId == null || bizId.isBlank() || bizId.length() > 64) {
            throw new IllegalArgumentException("业务单号不能为空且长度不能超过 64 个字符");
        }
        if (reason == null || reason.isBlank() || reason.length() > 255) {
            throw new IllegalArgumentException("操作原因不能为空且长度不能超过 255 个字符");
        }
    }
}
