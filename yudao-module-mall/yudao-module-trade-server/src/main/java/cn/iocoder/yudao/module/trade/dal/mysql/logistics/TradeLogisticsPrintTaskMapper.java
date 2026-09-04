package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintTaskDO;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
public interface TradeLogisticsPrintTaskMapper extends BaseMapperX<TradeLogisticsPrintTaskDO> {

    default TradeLogisticsPrintTaskDO selectByJobIdForUpdate(String jobId) {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsPrintTaskDO>()
                .eq(TradeLogisticsPrintTaskDO::getJobId, jobId).last("FOR UPDATE"));
    }

    default TradeLogisticsPrintTaskDO selectClaimable(Long deviceId, LocalDateTime now) {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsPrintTaskDO>()
                .eq(TradeLogisticsPrintTaskDO::getDeviceId, deviceId)
                .and(wrapper -> wrapper.eq(TradeLogisticsPrintTaskDO::getStatus, LogisticsPrintTaskStatusEnum.PENDING.name())
                        .or(nested -> nested.eq(TradeLogisticsPrintTaskDO::getStatus,
                                        LogisticsPrintTaskStatusEnum.DISPATCHED.name())
                                .lt(TradeLogisticsPrintTaskDO::getLeaseExpireTime, now)))
                .orderByAsc(TradeLogisticsPrintTaskDO::getId).last("LIMIT 1 FOR UPDATE"));
    }

    default List<TradeLogisticsPrintTaskDO> selectListAll() {
        return selectList(new LambdaQueryWrapperX<TradeLogisticsPrintTaskDO>().orderByDesc(TradeLogisticsPrintTaskDO::getId));
    }

    default TradeLogisticsPrintTaskDO selectLatestByWaybillId(Long waybillId) {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsPrintTaskDO>()
                .eq(TradeLogisticsPrintTaskDO::getWaybillId, waybillId)
                .orderByDesc(TradeLogisticsPrintTaskDO::getId).last("LIMIT 1"));
    }

    default TradeLogisticsPrintTaskDO selectLatestByWaybillIdForUpdate(Long waybillId) {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsPrintTaskDO>()
                .eq(TradeLogisticsPrintTaskDO::getWaybillId, waybillId)
                .orderByDesc(TradeLogisticsPrintTaskDO::getId).last("LIMIT 1 FOR UPDATE"));
    }

    default List<TradeLogisticsPrintTaskDO> selectListByWaybillIds(Collection<Long> waybillIds) {
        return selectList(new LambdaQueryWrapperX<TradeLogisticsPrintTaskDO>()
                .in(TradeLogisticsPrintTaskDO::getWaybillId, waybillIds)
                .orderByDesc(TradeLogisticsPrintTaskDO::getId));
    }

    default List<TradeLogisticsPrintTaskDO> selectAcceptedExpired(LocalDateTime before) {
        return selectList(new LambdaQueryWrapperX<TradeLogisticsPrintTaskDO>()
                .eq(TradeLogisticsPrintTaskDO::getStatus, LogisticsPrintTaskStatusEnum.ACCEPTED.name())
                .lt(TradeLogisticsPrintTaskDO::getAcceptedTime, before));
    }

    default List<TradeLogisticsPrintTaskDO> selectListByStatus(String status) {
        return selectList(TradeLogisticsPrintTaskDO::getStatus, status);
    }

    default int markAcceptedExpiredUnknown(Long id, LocalDateTime acceptedBefore, String error) {
        return update(new TradeLogisticsPrintTaskDO()
                        .setStatus(LogisticsPrintTaskStatusEnum.UNKNOWN.name()).setLastError(error),
                new LambdaUpdateWrapper<TradeLogisticsPrintTaskDO>()
                        .eq(TradeLogisticsPrintTaskDO::getId, id)
                        .eq(TradeLogisticsPrintTaskDO::getStatus, LogisticsPrintTaskStatusEnum.ACCEPTED.name())
                        .lt(TradeLogisticsPrintTaskDO::getAcceptedTime, acceptedBefore));
    }
}
