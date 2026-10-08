package cn.iocoder.yudao.module.member.service.point;

import cn.iocoder.yudao.module.member.dal.dataobject.point.MemberPointBatchDO;
import cn.iocoder.yudao.module.member.dal.dataobject.point.MemberPointConsumeDO;
import cn.iocoder.yudao.module.member.dal.dataobject.point.MemberPointRecordDO;
import cn.iocoder.yudao.module.member.dal.mysql.point.MemberPointBatchMapper;
import cn.iocoder.yudao.module.member.dal.mysql.point.MemberPointConsumeMapper;
import cn.iocoder.yudao.module.member.dal.mysql.point.MemberPointRecordMapper;
import cn.iocoder.yudao.module.member.enums.point.MemberPointBizTypeEnum;
import cn.iocoder.yudao.module.member.enums.point.MemberPointRecordStatusEnum;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MemberPointBatchServiceImpl implements MemberPointBatchService {
    private final MemberPointBatchMapper batchMapper;
    private final MemberPointConsumeMapper consumeMapper;
    private final MemberUserService userService;
    private final MemberPointRecordMapper recordMapper;

    @Override
    public void grant(MemberPointRecordDO record) {
        if (record == null || record.getId() == null || record.getPoint() == null || record.getPoint() <= 0) return;
        if (batchMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberPointBatchDO>()
                .eq(MemberPointBatchDO::getSourceRecordId, record.getId())) != null) return;
        LocalDateTime earned = record.getEffectiveTime() == null ? LocalDateTime.now() : record.getEffectiveTime();
        try {
            batchMapper.insert(MemberPointBatchDO.builder().userId(record.getUserId()).sourceRecordId(record.getId())
                    .originalPoint(record.getPoint()).remainingPoint(record.getPoint()).earnedTime(earned)
                    .expireTime(earned.plusYears(1)).legacy(0).status(0).build());
        } catch (DataIntegrityViolationException duplicate) {
            // The unique source-record constraint makes concurrent delivery idempotent.
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(Long userId, Long recordId, int quantity, String bizId) {
        if (quantity <= 0 || consumeMapper.existsByBizId(userId, bizId)) return;
        int left = quantity;
        List<MemberPointBatchDO> batches = batchMapper.selectAvailable(userId, LocalDateTime.now());
        for (MemberPointBatchDO batch : batches) {
            if (left == 0) break;
            int take = Math.min(left, batch.getRemainingPoint());
            if (batchMapper.decreaseRemaining(batch.getId(), take) == 1) {
                consumeMapper.insert(MemberPointConsumeDO.builder().userId(userId).batchId(batch.getId())
                        .recordId(recordId).quantity(take).bizId(bizId).restored(0).build());
                left -= take;
            }
        }
        if (left > 0) {
            // Users created before expiring batches was introduced may only have a balance
            // on member_user. Keep that balance spendable through an unexpired legacy batch.
            MemberPointBatchDO legacy = batchMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberPointBatchDO>()
                    .eq(MemberPointBatchDO::getUserId, userId).eq(MemberPointBatchDO::getLegacy, 1)
                    .eq(MemberPointBatchDO::getStatus, 0).orderByAsc(MemberPointBatchDO::getId).last("limit 1"));
            if (legacy == null) {
                legacy = MemberPointBatchDO.builder().userId(userId).originalPoint(left).remainingPoint(left)
                        .earnedTime(LocalDateTime.now()).expireTime(null).legacy(1).status(0).build();
                batchMapper.insert(legacy);
            }
            int take = Math.min(left, legacy.getRemainingPoint());
            if (take <= 0 || batchMapper.decreaseRemaining(legacy.getId(), take) != 1) {
                throw new IllegalStateException("积分批次余额不足");
            }
            consumeMapper.insert(MemberPointConsumeDO.builder().userId(userId).batchId(legacy.getId())
                    .recordId(recordId).quantity(take).bizId(bizId).restored(0).build());
            left -= take;
        }
        if (left > 0) throw new IllegalStateException("积分批次余额不足");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restore(Long userId, Long recordId, int quantity, String bizId) {
        if (quantity <= 0) return;
        int left = quantity;
        for (MemberPointConsumeDO consume : consumeMapper.selectUnrestored(userId, bizId)) {
            if (left <= 0) break;
            int restore = Math.min(left, consume.getQuantity() - Objects.requireNonNullElse(consume.getRestored(), 0));
            if (restore <= 0) continue;
            if (batchMapper.increaseRemaining(consume.getBatchId(), restore) != 1) {
                throw new IllegalStateException("积分批次不可恢复");
            }
            if (consumeMapper.markRestored(consume.getId(), restore) != 1) {
                throw new IllegalStateException("积分消费明细已被恢复");
            }
            left -= restore;
        }
        if (left > 0) {
            // Keep a refund usable even when the original legacy migration has not run yet.
            MemberPointBatchDO batch = MemberPointBatchDO.builder().userId(userId).sourceRecordId(recordId)
                    .originalPoint(left).remainingPoint(left).earnedTime(LocalDateTime.now()).expireTime(null)
                    .legacy(1).status(0).build();
            batchMapper.insert(batch);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int rollbackGrant(Long userId, Long sourceRecordId, int quantity, String bizId) {
        if (quantity <= 0) return 0;
        int left = quantity;
        LocalDateTime now = LocalDateTime.now();
        // Refund only the original grant batch. Never consume another reward batch to
        // compensate for a missing or already-spent original grant.
        List<MemberPointBatchDO> candidates = batchMapper.selectRollbackCandidates(userId, sourceRecordId, now);
        for (MemberPointBatchDO batch : candidates) {
            if (left <= 0) break;
            int available = Objects.requireNonNullElse(batch.getRemainingPoint(), 0);
            int take = Math.min(left, available);
            if (take <= 0 || batchMapper.decreaseRemaining(batch.getId(), take) != 1) continue;
            left -= take;
        }
        // If the user spent more points than remain in the grant batches, the aggregate
        // account already records the resulting debt. Do not create a new batch or alter
        // another user's balance to make the batch totals appear artificially complete.
        return quantity - left;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expire() {
        LocalDateTime now = LocalDateTime.now();
        List<MemberPointBatchDO> batches = batchMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MemberPointBatchDO>()
                .eq(MemberPointBatchDO::getStatus, 0).le(MemberPointBatchDO::getExpireTime, now)
                .gt(MemberPointBatchDO::getRemainingPoint, 0));
        for (MemberPointBatchDO batch : batches) {
            int remaining = Objects.requireNonNullElse(batch.getRemainingPoint(), 0);
            if (remaining <= 0 || batchMapper.expire(batch.getId(), now) != 1) continue;
            userService.getUserForUpdate(batch.getUserId());
            if (!userService.updateUserPoint(batch.getUserId(), -remaining)) {
                throw new IllegalStateException("积分余额与批次不一致");
            }
            MemberPointRecordDO record = new MemberPointRecordDO().setUserId(batch.getUserId()).setPoint(-remaining)
                    .setBizType(MemberPointBizTypeEnum.POINT_EXPIRE.getType()).setBizId("EXPIRE:" + batch.getId())
                    .setTitle(MemberPointBizTypeEnum.POINT_EXPIRE.getName())
                    .setDescription("积分过期扣除 " + remaining + " 积分")
                    .setTotalPoint(userService.getUser(batch.getUserId()).getPoint())
                    .setStatus(MemberPointRecordStatusEnum.EFFECTIVE.getStatus()).setEffectiveTime(now);
            recordMapper.insert(record);
        }
    }
}
