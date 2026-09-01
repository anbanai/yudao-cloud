package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintEventDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TradeLogisticsPrintEventMapper extends BaseMapperX<TradeLogisticsPrintEventDO> {

    default TradeLogisticsPrintEventDO selectByEventId(String eventId) {
        return selectOne(TradeLogisticsPrintEventDO::getEventId, eventId);
    }
}
