package cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.PayYeepayEventDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface PayYeepayEventMapper extends BaseMapperX<PayYeepayEventDO> {

    default PayYeepayEventDO selectByEventId(String eventId) {
        return selectOne(PayYeepayEventDO::getEventId, eventId);
    }

    default PayYeepayEventDO selectByEventNo(String eventNo) {
        return selectOne(PayYeepayEventDO::getEventNo, eventNo);
    }

    default PayYeepayEventDO selectByRequestId(String requestId) {
        return selectOne(PayYeepayEventDO::getRequestId, requestId);
    }

    default List<PayYeepayEventDO> selectListByStatus(Collection<Integer> statuses) {
        return selectList(new LambdaQueryWrapper<PayYeepayEventDO>()
                .in(PayYeepayEventDO::getStatus, statuses)
                .orderByAsc(PayYeepayEventDO::getId));
    }

    /** Atomically claims a callback event for one worker. */
    default boolean claimForProcessing(Long id, LocalDateTime staleBefore) {
        LambdaQueryWrapper<PayYeepayEventDO> wrapper = new LambdaQueryWrapper<PayYeepayEventDO>()
                .eq(PayYeepayEventDO::getId, id)
                .and(condition -> condition
                        .in(PayYeepayEventDO::getStatus, 0, 20)
                        .or(or -> or.eq(PayYeepayEventDO::getStatus, 5)
                                .lt(PayYeepayEventDO::getUpdateTime, staleBefore)));
        return update(new PayYeepayEventDO().setStatus(5).setLastError(""), wrapper) == 1;
    }
}
