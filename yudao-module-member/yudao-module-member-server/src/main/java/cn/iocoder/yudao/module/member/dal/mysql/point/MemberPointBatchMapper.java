package cn.iocoder.yudao.module.member.dal.mysql.point;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.member.dal.dataobject.point.MemberPointBatchDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MemberPointBatchMapper extends BaseMapperX<MemberPointBatchDO> {
    default List<MemberPointBatchDO> selectAvailable(Long userId, LocalDateTime now) {
        return selectList(new LambdaQueryWrapperX<MemberPointBatchDO>()
                .eq(MemberPointBatchDO::getUserId, userId)
                .eq(MemberPointBatchDO::getStatus, 0)
                .gt(MemberPointBatchDO::getRemainingPoint, 0)
                .and(w -> w.isNull(MemberPointBatchDO::getExpireTime)
                        .or().gt(MemberPointBatchDO::getExpireTime, now))
                .orderByAsc(MemberPointBatchDO::getLegacy)
                .orderByAsc(MemberPointBatchDO::getExpireTime)
                .orderByAsc(MemberPointBatchDO::getId));
    }

    default int decreaseRemaining(Long id, int quantity) {
        return update(null, new LambdaUpdateWrapper<MemberPointBatchDO>()
                .eq(MemberPointBatchDO::getId, id)
                .eq(MemberPointBatchDO::getStatus, 0)
                .ge(MemberPointBatchDO::getRemainingPoint, quantity)
                .setSql("remaining_point = remaining_point - " + quantity));
    }

    default int expire(Long id, LocalDateTime now) {
        return update(null, new LambdaUpdateWrapper<MemberPointBatchDO>()
                .eq(MemberPointBatchDO::getId, id)
                .eq(MemberPointBatchDO::getStatus, 0)
                .le(MemberPointBatchDO::getExpireTime, now)
                .gt(MemberPointBatchDO::getRemainingPoint, 0)
                .set(MemberPointBatchDO::getRemainingPoint, 0)
                .set(MemberPointBatchDO::getStatus, 1));
    }

    default int increaseRemaining(Long id, int quantity) {
        return update(null, new LambdaUpdateWrapper<MemberPointBatchDO>()
                .eq(MemberPointBatchDO::getId, id)
                .eq(MemberPointBatchDO::getStatus, 0)
                .setSql("remaining_point = remaining_point + " + quantity));
    }

    default List<MemberPointBatchDO> selectRollbackCandidates(Long userId, Long sourceRecordId,
                                                               LocalDateTime now) {
        // A missing source record must never fall back to another reward batch.
        // Returning no candidates preserves unrelated balances and leaves the
        // aggregate account debt visible for reconciliation.
        if (sourceRecordId == null) {
            return java.util.Collections.emptyList();
        }
        LambdaQueryWrapper<MemberPointBatchDO> wrapper = new LambdaQueryWrapper<MemberPointBatchDO>()
                .eq(MemberPointBatchDO::getUserId, userId)
                .eq(MemberPointBatchDO::getLegacy, 0)
                .eq(MemberPointBatchDO::getStatus, 0)
                .gt(MemberPointBatchDO::getRemainingPoint, 0);
        wrapper.and(w -> w.isNull(MemberPointBatchDO::getExpireTime)
                .or().gt(MemberPointBatchDO::getExpireTime, now));
        wrapper.orderByAsc(MemberPointBatchDO::getSourceRecordId)
                .orderByAsc(MemberPointBatchDO::getExpireTime)
                .orderByAsc(MemberPointBatchDO::getId);
        wrapper.eq(MemberPointBatchDO::getSourceRecordId, sourceRecordId);
        return selectList(wrapper);
    }
}
