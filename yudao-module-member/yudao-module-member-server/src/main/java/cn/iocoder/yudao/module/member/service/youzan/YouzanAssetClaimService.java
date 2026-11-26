package cn.iocoder.yudao.module.member.service.youzan;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.importing.YouzanCustomerImportRules;
import cn.iocoder.yudao.module.member.service.level.MemberLevelService;
import cn.iocoder.yudao.module.member.service.point.RecoveredPointCreditService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    public record Result(String status, int addedPoint) { }

    @Transactional(rollbackFor = Exception.class)
    public Result recover(Long userId, String verifiedMobile) {
        Long tenant = TenantContextHolder.getRequiredTenantId();
        String mobile = YouzanCustomerImportRules.normalizeMobile(verifiedMobile);
        Long source = environment.getProperty("yudao.member.youzan.claim-source." + tenant, Long.class);
        String secret = environment.getProperty("mybatis-plus.encryptor.password");
        if (TenantContextHolder.isIgnore() || userId == null || mobile == null || source == null
                || source <= 0 || !StringUtils.hasText(secret)) throw exception(FORBIDDEN);
        String hash = YouzanCustomerImportRules.keyedHash(mobile, secret);
        MemberUserDO user = users.selectByIdForUpdate(userId);
        if (user == null || !tenant.equals(user.getTenantId()) || !CommonStatusEnum.isEnable(user.getStatus())
                || (StringUtils.hasText(user.getMobile()) && !mobile.equals(user.getMobile()))) throw exception(FORBIDDEN);
        var match = snapshots.lock(source, hash);
        if (match.isEmpty()) return new Result("NOT_FOUND", 0);
        var snapshot = match.get();
        if (!EncryptTypeHandler.encrypt(mobile).equals(snapshot.encryptedMobile())) throw exception(FORBIDDEN);
        if (snapshot.status() == 1 && snapshot.dated() && userId.equals(snapshot.claimant())) {
            return new Result("ALREADY_RESTORED", 0);
        }
        if (snapshot.status() != 0 || snapshot.dated() || snapshot.claimant() != null || snapshot.points() < 0) {
            throw exception(FORBIDDEN);
        }
        if (!StringUtils.hasText(user.getMobile())) {
            if (users.selectByMobile(mobile) != null) throw exception(FORBIDDEN);
            try {
                if (users.updateById(new MemberUserDO().setId(userId).setMobile(mobile)) != 1) throw exception(FORBIDDEN);
            } catch (org.springframework.dao.DuplicateKeyException conflict) {
                throw exception(FORBIDDEN);
            }
        }
        if (points.credit(userId, snapshot.id(), snapshot.points()) != snapshot.points()) {
            throw new IllegalStateException("快照与已有积分流水不一致");
        }
        levels.restoreHigherLevel(userId, snapshot.levelId());
        snapshots.markClaimed(snapshot.id(), source, hash, userId);
        return new Result("RESTORED", snapshot.points());
    }
}
