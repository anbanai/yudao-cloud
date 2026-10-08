package cn.iocoder.yudao.module.trade.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.List;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

@Mapper
public interface TradeOrderSubMapper extends BaseMapperX<TradeOrderSubDO> {
    default List<TradeOrderSubDO> selectListByOrderId(Long orderId) {
        return selectList(TradeOrderSubDO::getOrderId, orderId);
    }

    default int updateStatusByOrderId(Long orderId, Integer oldStatus, Integer newStatus) {
        return update(new TradeOrderSubDO().setStatus(newStatus), new LambdaUpdateWrapper<TradeOrderSubDO>()
                .eq(TradeOrderSubDO::getOrderId, orderId).eq(TradeOrderSubDO::getStatus, oldStatus));
    }

    default int updateByIdAndStatus(Long id, Integer status, TradeOrderSubDO update) {
        return update(update, new LambdaUpdateWrapper<TradeOrderSubDO>()
                .eq(TradeOrderSubDO::getId, id).eq(TradeOrderSubDO::getStatus, status));
    }

    default int updateByOrderId(Long orderId, TradeOrderSubDO update) {
        return update(update, new LambdaUpdateWrapper<TradeOrderSubDO>()
                .eq(TradeOrderSubDO::getOrderId, orderId));
    }

    default int deleteByOrderId(Long orderId) {
        return delete(new LambdaUpdateWrapper<TradeOrderSubDO>()
                .eq(TradeOrderSubDO::getOrderId, orderId));
    }
}
