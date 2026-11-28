package cn.iocoder.yudao.module.member.service.youzan;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.dal.mysql.point.MemberPointRecordMapper;
import cn.iocoder.yudao.module.member.service.importing.YouzanCustomerImportRules;
import cn.iocoder.yudao.module.member.service.level.MemberLevelService;
import cn.iocoder.yudao.module.member.service.point.RecoveredPointCreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@RequiredArgsConstructor
public class YouzanAssetClaimService {
    private final Environment environment;
    private final MemberUserMapper users;
    private final YouzanClaimStore snapshots;
    private final RecoveredPointCreditService points;
    private final MemberLevelService levels;
    private final MemberPointRecordMapper records;

    public record Result(String status, int addedPoint) { }

    @Transactional(rollbackFor = Exception.class)
    public Result recover(Long userId, String verifiedMobile) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        String mobile = YouzanCustomerImportRules.normalizeMobile(verifiedMobile);
        Long source = environment.getProperty("yudao.member.youzan.claim-source." + tenant, Long.class);
        String secret = environment.getProperty("mybatis-plus.encryptor.password");
        if (TenantContextHolder.isIgnore() || userId == null || mobile == null || !StringUtils.hasText(secret)) throw exception(FORBIDDEN);
        String hash = YouzanCustomerImportRules.keyedHash(mobile, secret);
        MemberUserDO user = users.selectByIdForUpdate(userId);
        if (user == null || !tenant.equals(user.getTenantId()) || !CommonStatusEnum.isEnable(user.getStatus())
                || (StringUtils.hasText(user.getMobile()) && !mobile.equals(user.getMobile()))) throw exception(FORBIDDEN);
        var matches = snapshots.lockByMobile(hash);
        var eligible = matches.stream().filter(snapshot -> !snapshot.deleted()
                && (snapshot.merchantId() == null || Objects.equals(source, snapshot.merchantId()) && source > 0)).toList();
        if (eligible.isEmpty()) return new Result("NOT_FOUND", 0);

        var snapshot = eligible.get(0);
        String encryptedMobile = EncryptTypeHandler.encrypt(mobile);
        if (eligible.stream().anyMatch(row -> !Objects.equals(row.fingerprint(), snapshot.fingerprint())
                || row.points() != snapshot.points() || !Objects.equals(row.levelId(), snapshot.levelId()))) {
            throw new IllegalStateException("手机号匹配的来源资产不一致，请核对后再领取");
        }
        boolean alreadyClaimed = false;
        for (var row : matches) {
            if (!encryptedMobile.equals(row.encryptedMobile())) throw exception(FORBIDDEN);
            if (row.status() == 1 && row.dated() && userId.equals(row.claimant())) {
                alreadyClaimed = true;
                continue;
            }
            if (row.status() != 0 || row.dated() || row.claimant() != null || row.points() < 0) throw exception(FORBIDDEN);
            // Old receipts and even soft-deleted snapshots remain evidence of a prior payment.
            if (row.id() != snapshot.id() && records.findHistoricalRecovery(tenant, Long.toString(row.id())) != null) {
                throw new IllegalStateException("待领快照已有恢复积分流水");
            }
        }
        if (alreadyClaimed) return new Result("ALREADY_RESTORED", 0);
        int addedPoint = points.credit(userId, snapshot.id(), snapshot.points());
        if (!StringUtils.hasText(user.getMobile())) {
            if (users.selectByMobile(mobile) != null) throw exception(FORBIDDEN);
            try {
                if (users.updateById(new MemberUserDO().setId(userId).setMobile(mobile)) != 1) throw exception(FORBIDDEN);
            } catch (org.springframework.dao.DuplicateKeyException conflict) {
                throw exception(FORBIDDEN);
            }
        }
        levels.restoreHigherLevel(userId, snapshot.levelId());
        snapshots.markClaimed(snapshot.id(), hash, userId);
        return new Result("RESTORED", addedPoint);
    }
}
