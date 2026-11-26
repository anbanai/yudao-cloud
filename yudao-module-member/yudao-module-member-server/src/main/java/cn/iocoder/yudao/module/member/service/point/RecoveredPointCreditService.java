package cn.iocoder.yudao.module.member.service.point;

import cn.iocoder.yudao.module.member.dal.dataobject.point.MemberPointBatchDO;
import cn.iocoder.yudao.module.member.dal.dataobject.point.MemberPointRecordDO;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.dal.mysql.point.MemberPointBatchMapper;
import cn.iocoder.yudao.module.member.dal.mysql.point.MemberPointRecordMapper;
import cn.iocoder.yudao.module.member.dal.mysql.user.MemberUserMapper;
import cn.iocoder.yudao.module.member.enums.point.MemberPointRecordStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RecoveredPointCreditService {
    private static final int RECOVERED_POINT_TYPE = 3;
    private final MemberUserMapper users;
    private final MemberPointRecordMapper records;
    private final MemberPointBatchMapper batches;

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public int credit(long userId, long snapshotId, int amount) {
        if (userId <= 0 || snapshotId <= 0 || amount < 0) throw new IllegalArgumentException("无效的领取记录");
        MemberUserDO user = users.selectByIdForUpdate(userId);
        if (user == null) throw new IllegalStateException("领取账户不存在");
        String bizId = Long.toString(snapshotId);
        MemberPointRecordDO previous = records.selectOne(new LambdaQueryWrapper<MemberPointRecordDO>()
                .eq(MemberPointRecordDO::getBizType, RECOVERED_POINT_TYPE).eq(MemberPointRecordDO::getBizId, bizId)
                .eq(MemberPointRecordDO::getUserId, userId).last("FOR UPDATE"));
        if (previous != null) {
            if (!Objects.equals(previous.getPoint(), amount)) throw new IllegalStateException("领取流水金额不一致");
            return 0;
        }
        if (amount == 0) return 0;
        int before = Objects.requireNonNullElse(user.getPoint(), 0);
        int total = Math.addExact(before, amount);
        int remaining = Math.min(amount, Math.max(0, total));
        LocalDateTime received = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        MemberPointRecordDO record = new MemberPointRecordDO().setUserId(userId).setBizId(bizId)
                .setBizType(RECOVERED_POINT_TYPE).setPoint(amount).setTotalPoint(total).setTitle("恢复有赞旧积分")
                .setDescription("恢复有赞旧积分 " + amount).setStatus(MemberPointRecordStatusEnum.EFFECTIVE.getStatus())
                .setEffectiveTime(received);
        if (users.updateById(new MemberUserDO().setId(userId).setPoint(total)) != 1
                || records.insert(record) != 1 || record.getId() == null
                || batches.insert(MemberPointBatchDO.builder().userId(userId).sourceRecordId(record.getId())
                .originalPoint(amount).remainingPoint(remaining).earnedTime(received).expireTime(received.plusYears(1))
                .legacy(0).status(0).build()) != 1) throw new IllegalStateException("恢复积分入账未完成");
        return amount;
    }
}
