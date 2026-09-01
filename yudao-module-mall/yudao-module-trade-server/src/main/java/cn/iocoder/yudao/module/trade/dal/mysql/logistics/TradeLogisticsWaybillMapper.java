package cn.iocoder.yudao.module.trade.dal.mysql.logistics;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsWaybillDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Collection;

@Mapper
public interface TradeLogisticsWaybillMapper extends BaseMapperX<TradeLogisticsWaybillDO> {

    default TradeLogisticsWaybillDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsWaybillDO>()
                .eq(TradeLogisticsWaybillDO::getId, id).last("FOR UPDATE"));
    }

    default TradeLogisticsWaybillDO selectByOrderIdForUpdate(Long orderId) {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsWaybillDO>()
                .eq(TradeLogisticsWaybillDO::getOrderId, orderId)
                .orderByDesc(TradeLogisticsWaybillDO::getId).last("LIMIT 1 FOR UPDATE"));
    }

    default TradeLogisticsWaybillDO selectActiveByOrderId(Long orderId) {
        return selectOne(new LambdaQueryWrapperX<TradeLogisticsWaybillDO>()
                .eq(TradeLogisticsWaybillDO::getOrderId, orderId)
                .in(TradeLogisticsWaybillDO::getStatus, List.of("CREATING", "CREATED", "UNKNOWN",
                        "CANCELLING", "CANCEL_UNKNOWN"))
                .orderByDesc(TradeLogisticsWaybillDO::getId).last("LIMIT 1"));
    }

    default TradeLogisticsWaybillDO selectByProviderOrderNo(String providerOrderNo) {
        return selectOne(TradeLogisticsWaybillDO::getProviderOrderNo, providerOrderNo);
    }

    default List<TradeLogisticsWaybillDO> selectListAll() {
        return selectList(new LambdaQueryWrapperX<TradeLogisticsWaybillDO>().orderByDesc(TradeLogisticsWaybillDO::getId));
    }

    default List<TradeLogisticsWaybillDO> selectListByStatus(String status) {
        return selectList(TradeLogisticsWaybillDO::getStatus, status);
    }

    default List<TradeLogisticsWaybillDO> selectListByStatuses(Collection<String> statuses) {
        return selectList(new LambdaQueryWrapperX<TradeLogisticsWaybillDO>()
                .in(TradeLogisticsWaybillDO::getStatus, statuses));
    }
}
