package cn.iocoder.yudao.module.member.service.benefit;

import cn.iocoder.yudao.module.member.controller.app.benefit.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.benefit.*;
import cn.iocoder.yudao.module.member.dal.dataobject.level.MemberLevelDO;
import cn.iocoder.yudao.module.member.dal.dataobject.level.MemberLevelRecordDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.benefit.*;
import cn.iocoder.yudao.module.member.dal.mysql.level.MemberLevelRecordMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.service.level.MemberLevelService;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.promotion.api.coupon.CouponApi;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MemberBenefitServiceImpl implements MemberBenefitService {
    private static final int CLAIMABLE = 0;
    private static final int STATUS_ENABLE = 0;
    private static final int PERIOD_ONCE = 1;
    private static final int PERIOD_QUARTER = 2;
    private static final int PERIOD_YEAR = 3;
    private static final int PERIOD_BIRTHDAY = 4;
    private static final int STATUS_EXPIRED = 1;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    @Resource private MemberUserService userService;
    @Resource private MemberLevelService levelService;
    @Resource private MemberBenefitDefinitionMapper definitionMapper;
    @Resource private MemberLevelBenefitMapper levelBenefitMapper;
    @Resource private MemberUserBenefitMapper userBenefitMapper;
    @Resource private MemberBenefitLedgerMapper ledgerMapper;
    @Resource private MemberUserMapper userMapper;
    @Resource private MemberLevelRecordMapper levelRecordMapper;
    @Resource private CouponApi couponApi;

    @Override @Transactional
    public AppMemberBenefitOverviewRespVO getOverview(Long userId) {
        MemberUserDO user = userService.getUser(userId);
        MemberLevelDO level = user == null ? null : levelService.getLevel(user.getLevelId());
        Long levelId = level == null ? null : level.getId();
        List<AppMemberBenefitRespVO> benefits = levelId == null ? Collections.emptyList() : getLevelBenefitsForUser(userId, levelId);
        return new AppMemberBenefitOverviewRespVO().setLevelName(level == null ? "普通会员" : level.getName())
                .setLevel(level == null ? 0 : level.getLevel()).setPoint(user == null ? 0 : user.getPoint())
                .setPointTradeGiveMultiplier(level == null || level.getPointTradeGiveMultiplier() == null ? 1000 : level.getPointTradeGiveMultiplier())
                .setBenefits(benefits);
    }

    @Override public List<AppMemberBenefitRespVO> getRecords(Long userId) {
        List<MemberUserBenefitDO> accounts = userBenefitMapper.selectList(new LambdaQueryWrapper<MemberUserBenefitDO>()
                .eq(MemberUserBenefitDO::getUserId, userId).orderByDesc(MemberUserBenefitDO::getId));
        return toVO(accounts);
    }

    @Override public List<AppMemberBenefitRespVO> getLevelBenefits(Long levelId) {
        return toVO(levelBenefitMapper.selectList(new LambdaQueryWrapper<MemberLevelBenefitDO>()
                .eq(MemberLevelBenefitDO::getLevelId, levelId).eq(MemberLevelBenefitDO::getStatus, STATUS_ENABLE)));
    }

    private List<AppMemberBenefitRespVO> getLevelBenefitsForUser(Long userId, Long levelId) {
        List<MemberLevelBenefitDO> bindings = levelBenefitMapper.selectList(new LambdaQueryWrapper<MemberLevelBenefitDO>()
                .eq(MemberLevelBenefitDO::getLevelId, levelId).eq(MemberLevelBenefitDO::getStatus, STATUS_ENABLE));
        List<MemberUserBenefitDO> accounts = new ArrayList<>();
        for (MemberLevelBenefitDO binding : bindings) {
            LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
            MemberUserDO user = userService.getUser(userId);
            if (Objects.equals(binding.getPeriodType(), PERIOD_BIRTHDAY)
                    && !isBirthdayWindow(user, binding.getBirthdayAdvanceDays(), now)) {
                continue;
            }
            LocalDateTime start = periodStart(binding.getPeriodType(), now, user, userId, levelId);
            LambdaQueryWrapper<MemberUserBenefitDO> accountQuery = new LambdaQueryWrapper<MemberUserBenefitDO>()
                    .eq(MemberUserBenefitDO::getUserId, userId).eq(MemberUserBenefitDO::getLevelId, levelId)
                    .eq(MemberUserBenefitDO::getBenefitId, binding.getBenefitId());
            if (!Objects.equals(binding.getPeriodType(), PERIOD_ONCE)) {
                accountQuery.eq(MemberUserBenefitDO::getPeriodStart, start);
            }
            MemberUserBenefitDO account = userBenefitMapper.selectOne(accountQuery.orderByDesc(MemberUserBenefitDO::getId).last("LIMIT 1"));
            boolean inserted = false;
            if (account == null) {
                account = MemberUserBenefitDO.builder().userId(userId).levelId(levelId).benefitId(binding.getBenefitId())
                        .periodStart(start).periodEnd(periodEnd(binding.getPeriodType(), start, binding.getValidityDays(), now))
                        .grantedQuantity(binding.getQuantity()).availableQuantity(binding.getQuantity()).usedQuantity(0).claimedQuantity(0)
                        .claimType(binding.getClaimType()).claimStatus(CLAIMABLE).status(STATUS_ENABLE).build();
                try {
                    userBenefitMapper.insert(account);
                    inserted = true;
                } catch (Exception ignored) {
                    account = userBenefitMapper.selectOne(new LambdaQueryWrapper<MemberUserBenefitDO>()
                            .eq(MemberUserBenefitDO::getUserId, userId).eq(MemberUserBenefitDO::getLevelId, levelId)
                            .eq(MemberUserBenefitDO::getBenefitId, binding.getBenefitId())
                            .eq(MemberUserBenefitDO::getPeriodStart, start));
                }
                if (inserted && account != null && account.getId() != null
                        && !ledgerMapper.existsByBizId("GRANT:" + account.getId(), 1)) {
                    ledgerMapper.insert(MemberBenefitLedgerDO.builder().userId(userId).accountId(account.getId())
                            .benefitId(account.getBenefitId()).eventType(1).quantity(account.getGrantedQuantity())
                            .bizId("GRANT:" + account.getId()).reason("周期权益发放").build());
                }
            }
            if (account != null) accounts.add(account);
        }
        return toVO(accounts);
    }

    @Override @Transactional
    public void claim(Long userId, Long accountId, String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > 32) {
            throw new IllegalArgumentException("领取请求号不能为空且长度不能超过 32 个字符");
        }
        MemberUserBenefitDO account = userBenefitMapper.selectByIdForUpdate(accountId);
        if (account == null || !userId.equals(account.getUserId())) throw new IllegalArgumentException("权益账户不存在");
        MemberBenefitDefinitionDO definition = definitionMapper.selectById(account.getBenefitId());
        if (!Objects.equals(account.getClaimType(), 1) || definition == null
                || !Objects.equals(definition.getStatus(), STATUS_ENABLE)
                || !Objects.equals(definition.getType(), 3) || definition.getCouponTemplateId() == null) {
            throw new IllegalStateException("该权益尚未配置可领取的优惠券");
        }
        if (Objects.equals(account.getStatus(), STATUS_EXPIRED)
                || account.getPeriodEnd() == null || !account.getPeriodEnd().isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new IllegalStateException("权益已过期");
        }
        String claimBizId = "BCLM:" + accountId + ":" + requestId;
        if (ledgerMapper.existsByBizId(claimBizId, 2)) return;
        // Issue remotely first with a stable business key. A retry after local rollback
        // reuses the same key and the coupon service returns the existing coupon.
        Long couponId = couponApi.takeCouponForBenefit(definition.getCouponTemplateId(), userId, claimBizId).getCheckedData();
        if (couponId == null) {
            throw new IllegalStateException("优惠券发放失败，请稍后重试");
        }
        if (userBenefitMapper.claim(accountId, LocalDateTime.now(BUSINESS_ZONE)) != 1) {
            throw new IllegalStateException("权益已领取或额度不足");
        }
        ledgerMapper.insert(MemberBenefitLedgerDO.builder().userId(userId).accountId(accountId)
                .benefitId(account.getBenefitId()).eventType(2).quantity(1).bizId(claimBizId)
                .reason("用户领取权益").build());
    }

    @Override
    @Transactional
    public void use(Long userId, Long accountId, String bizId) {
        if (bizId == null || bizId.isBlank() || bizId.length() > 64) {
            throw new IllegalArgumentException("业务单号不能为空且长度不能超过 64 个字符");
        }
        if (ledgerMapper.existsByBizId(bizId, 3)) return;
        MemberUserBenefitDO account = userBenefitMapper.selectByIdForUpdate(accountId);
        if (account == null || !userId.equals(account.getUserId())) throw new IllegalArgumentException("权益账户不存在");
        MemberBenefitDefinitionDO definition = definitionMapper.selectById(account.getBenefitId());
        if (definition == null || !Objects.equals(definition.getActionType(), 2)) {
            throw new IllegalStateException("该权益无需申请使用");
        }
        if (Objects.equals(account.getStatus(), STATUS_EXPIRED)
                || account.getPeriodEnd() == null || !account.getPeriodEnd().isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new IllegalStateException("权益已过期");
        }
        if (userBenefitMapper.use(accountId) != 1) throw new IllegalStateException("权益额度不足");
        ledgerMapper.insert(MemberBenefitLedgerDO.builder().userId(userId).accountId(accountId)
                .benefitId(account.getBenefitId()).eventType(3).quantity(1).bizId(bizId)
                .reason("使用会员权益").build());
    }

    @Override
    public void processPeriods() {
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        long lastUserId = 0;
        while (true) {
            List<MemberUserDO> users = userMapper.selectList(new LambdaQueryWrapper<MemberUserDO>()
                    .gt(MemberUserDO::getId, lastUserId).orderByAsc(MemberUserDO::getId).last("LIMIT 500"));
            if (users.isEmpty()) break;
            for (MemberUserDO user : users) {
                lastUserId = user.getId();
                try {
                    MemberLevelDO level = levelService.getLevel(user.getLevelId());
                    if (level != null) getLevelBenefitsForUser(user.getId(), level.getId());
                } catch (RuntimeException ex) {
                    log.warn("[processPeriods][userId({})] failed", user.getId(), ex);
                }
            }
        }
        long lastAccountId = 0;
        while (true) {
            List<MemberUserBenefitDO> expired = userBenefitMapper.selectList(new LambdaQueryWrapper<MemberUserBenefitDO>()
                    .gt(MemberUserBenefitDO::getId, lastAccountId).eq(MemberUserBenefitDO::getStatus, STATUS_ENABLE)
                    .le(MemberUserBenefitDO::getPeriodEnd, now).orderByAsc(MemberUserBenefitDO::getId).last("LIMIT 500"));
            if (expired.isEmpty()) break;
            for (MemberUserBenefitDO account : expired) {
                lastAccountId = account.getId();
                try {
                    if (userBenefitMapper.expire(account.getId(), now) == 1) {
                        int remaining = account.getAvailableQuantity() == null ? 0 : account.getAvailableQuantity();
                        if (remaining > 0) ledgerMapper.insert(MemberBenefitLedgerDO.builder().userId(account.getUserId())
                                .accountId(account.getId()).benefitId(account.getBenefitId()).eventType(4)
                                .quantity(remaining).bizId("EXPIRE:" + account.getId()).reason("权益到期").build());
                    }
                } catch (RuntimeException ex) {
                    log.warn("[processPeriods][accountId({})] expire failed", account.getId(), ex);
                }
            }
        }
    }

    private List<AppMemberBenefitRespVO> toVO(List<?> list) {
        if (list == null) return Collections.emptyList();
        Set<Long> ids = list.stream().map(v -> v instanceof MemberUserBenefitDO ? ((MemberUserBenefitDO)v).getBenefitId() : ((MemberLevelBenefitDO)v).getBenefitId()).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, MemberBenefitDefinitionDO> definitions = ids.isEmpty() ? Collections.emptyMap() : definitionMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(MemberBenefitDefinitionDO::getId, v -> v));
        return list.stream().map(v -> {
            Long id; MemberUserBenefitDO account = null; MemberLevelBenefitDO binding = null;
            if (v instanceof MemberUserBenefitDO a) { account = a; id = a.getBenefitId(); } else { binding = (MemberLevelBenefitDO)v; id = binding.getBenefitId(); }
            MemberBenefitDefinitionDO definition = definitions.get(id);
            return new AppMemberBenefitRespVO().setAccountId(account == null ? null : account.getId())
                    .setCode(definition == null ? null : definition.getCode()).setName(definition == null ? "会员权益" : definition.getName())
                    .setType(definition == null ? null : definition.getType())
                    .setActionType(definition == null ? null : definition.getActionType())
                    .setDescription(definition == null ? null : definition.getDescription())
                    .setIcon(definition == null ? null : definition.getIcon()).setQuantity(account == null ? binding.getQuantity() : account.getGrantedQuantity())
                    .setAvailableQuantity(account == null ? binding.getQuantity() : account.getAvailableQuantity()).setUsedQuantity(account == null ? 0 : account.getUsedQuantity())
                    .setClaimType(account == null ? binding.getClaimType() : account.getClaimType())
                    .setClaimStatus(account == null ? CLAIMABLE : account.getClaimStatus()).setStatus(account == null ? binding.getStatus() : account.getStatus())
                    .setPeriodStart(account == null ? null : account.getPeriodStart()).setPeriodEnd(account == null ? null : account.getPeriodEnd())
                    .setClaimable(account != null && Objects.equals(account.getClaimType(), 1)
                            && definition != null && Objects.equals(definition.getType(), 3)
                            && definition.getCouponTemplateId() != null && Objects.equals(definition.getStatus(), STATUS_ENABLE)
                            && account.getAvailableQuantity() != null && account.getAvailableQuantity() > 0
                            && account.getPeriodEnd() != null && account.getPeriodEnd().isAfter(LocalDateTime.now(BUSINESS_ZONE))
                            && CLAIMABLE == account.getClaimStatus());
        }).toList();
    }
    private LocalDateTime periodStart(Integer type, LocalDateTime now, MemberUserDO user, Long userId, Long levelId) {
        if (Objects.equals(type, PERIOD_ONCE)) {
            MemberLevelRecordDO upgradedAt = levelRecordMapper.selectOne(new LambdaQueryWrapper<MemberLevelRecordDO>()
                    .eq(MemberLevelRecordDO::getUserId, userId)
                    .eq(MemberLevelRecordDO::getLevelId, levelId)
                    .orderByDesc(MemberLevelRecordDO::getCreateTime)
                    .last("LIMIT 1"));
            if (upgradedAt != null && upgradedAt.getCreateTime() != null) {
                return upgradedAt.getCreateTime();
            }
            return user != null && user.getCreateTime() != null ? user.getCreateTime() : now;
        }
        if (Objects.equals(type, PERIOD_QUARTER)) return now.withMonth(((now.getMonthValue()-1)/3)*3+1).withDayOfMonth(1).toLocalDate().atStartOfDay();
        if (Objects.equals(type, PERIOD_YEAR)) {
            LocalDate anniversary = user != null && user.getCreateTime() != null
                    ? user.getCreateTime().toLocalDate() : now.toLocalDate();
            LocalDate start = anniversary.withYear(now.getYear());
            if (start.isAfter(now.toLocalDate())) start = start.minusYears(1);
            return start.atStartOfDay();
        }
        if (Objects.equals(type, PERIOD_BIRTHDAY) && user != null && user.getBirthday() != null) {
            LocalDate birthday = birthdayInYear(user.getBirthday().toLocalDate(), now.getYear());
            if (birthday.isAfter(now.toLocalDate())) birthday = birthday.minusYears(1);
            return birthday.atStartOfDay();
        }
        return now.toLocalDate().atStartOfDay();
    }
    private LocalDateTime periodEnd(Integer type, LocalDateTime start, Integer days, LocalDateTime now) {
        if (Objects.equals(type, PERIOD_QUARTER)) return start.plusMonths(3).minusNanos(1);
        if (Objects.equals(type, PERIOD_YEAR)) return start.plusYears(1).minusNanos(1);
        if (Objects.equals(type, PERIOD_ONCE)) return now.plusDays(days == null || days <= 0 ? 365 : days).minusNanos(1);
        if (Objects.equals(type, PERIOD_BIRTHDAY)) return start.plusYears(1).minusNanos(1);
        return start.plusDays(days == null || days <= 0 ? 365 : days).minusNanos(1);
    }

    private boolean isBirthdayWindow(MemberUserDO user, Integer advanceDays, LocalDateTime now) {
        if (user == null || user.getBirthday() == null) return false;
        LocalDate birthday = birthdayInYear(user.getBirthday().toLocalDate(), now.getYear());
        int advance = Math.max(0, Objects.requireNonNullElse(advanceDays, 0));
        LocalDate today = now.toLocalDate();
        return !today.isBefore(birthday.minusDays(advance)) && !today.isAfter(birthday);
    }

    private LocalDate birthdayInYear(LocalDate birthday, int year) {
        if (birthday.getMonthValue() == 2 && birthday.getDayOfMonth() == 29 && !Year.isLeap(year)) {
            return LocalDate.of(year, 2, 28);
        }
        return birthday.withYear(year);
    }
}
