package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsTraceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TradeWechatLogisticsTraceMapper extends BaseMapperX<TradeWechatLogisticsTraceDO> {

    default List<TradeWechatLogisticsTraceDO> selectListByWaybillId(Long waybillId) {
        return selectList(new LambdaQueryWrapperX<TradeWechatLogisticsTraceDO>()
                .eq(TradeWechatLogisticsTraceDO::getWaybillId, waybillId)
                .orderByDesc(TradeWechatLogisticsTraceDO::getActionTime));
    }
}
