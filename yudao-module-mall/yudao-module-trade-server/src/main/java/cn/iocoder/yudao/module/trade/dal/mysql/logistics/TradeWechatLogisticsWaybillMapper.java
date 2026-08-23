package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeWechatLogisticsWaybillDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TradeWechatLogisticsWaybillMapper extends BaseMapperX<TradeWechatLogisticsWaybillDO> {

    default TradeWechatLogisticsWaybillDO selectByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapperX<TradeWechatLogisticsWaybillDO>()
                .eq(TradeWechatLogisticsWaybillDO::getOrderId, orderId));
    }

    default TradeWechatLogisticsWaybillDO selectByOrderIdForUpdate(Long orderId) {
        return selectOne(new LambdaQueryWrapperX<TradeWechatLogisticsWaybillDO>()
                .eq(TradeWechatLogisticsWaybillDO::getOrderId, orderId).last("FOR UPDATE"));
    }

    default TradeWechatLogisticsWaybillDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<TradeWechatLogisticsWaybillDO>()
                .eq(TradeWechatLogisticsWaybillDO::getId, id).last("FOR UPDATE"));
    }

    default TradeWechatLogisticsWaybillDO selectByWechatOrderId(String wechatOrderId) {
        return selectOne(new LambdaQueryWrapperX<TradeWechatLogisticsWaybillDO>()
                .eq(TradeWechatLogisticsWaybillDO::getWechatOrderId, wechatOrderId));
    }

    default List<TradeWechatLogisticsWaybillDO> selectListByPendingPrint() {
        return selectList(new LambdaQueryWrapperX<TradeWechatLogisticsWaybillDO>()
                .eq(TradeWechatLogisticsWaybillDO::getStatus, "CREATED")
                .eq(TradeWechatLogisticsWaybillDO::getPrintStatus, "PENDING")
                .orderByAsc(TradeWechatLogisticsWaybillDO::getId));
    }

    default List<TradeWechatLogisticsWaybillDO> selectListForTraceSync() {
        return selectList(new LambdaQueryWrapperX<TradeWechatLogisticsWaybillDO>()
                .eq(TradeWechatLogisticsWaybillDO::getStatus, "CREATED")
                .isNotNull(TradeWechatLogisticsWaybillDO::getWaybillId)
                .orderByAsc(TradeWechatLogisticsWaybillDO::getId));
    }
}
