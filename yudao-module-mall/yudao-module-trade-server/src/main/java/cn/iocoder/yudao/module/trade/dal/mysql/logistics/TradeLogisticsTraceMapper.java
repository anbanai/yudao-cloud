package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsTraceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TradeLogisticsTraceMapper extends BaseMapperX<TradeLogisticsTraceDO> {

    default List<TradeLogisticsTraceDO> selectListByWaybillId(Long waybillId) {
        return selectList(new LambdaQueryWrapperX<TradeLogisticsTraceDO>()
                .eq(TradeLogisticsTraceDO::getWaybillId, waybillId)
                .orderByDesc(TradeLogisticsTraceDO::getOperateTime));
    }
}
