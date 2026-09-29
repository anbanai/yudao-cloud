package cn.iocoder.yudao.module.trade.dal.mysql.aftersale;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleReturnTraceDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AfterSaleReturnTraceMapper extends BaseMapperX<AfterSaleReturnTraceDO> {

    default List<AfterSaleReturnTraceDO> selectListByShipmentId(Long shipmentId) {
        return selectList(new LambdaQueryWrapper<AfterSaleReturnTraceDO>()
                .eq(AfterSaleReturnTraceDO::getReturnShipmentId, shipmentId)
                .orderByDesc(AfterSaleReturnTraceDO::getOccurredTime)
                .orderByDesc(AfterSaleReturnTraceDO::getId));
    }

    default boolean existsByEventId(Long shipmentId, String eventId) {
        return exists(new LambdaQueryWrapper<AfterSaleReturnTraceDO>()
                .eq(AfterSaleReturnTraceDO::getReturnShipmentId, shipmentId)
                .eq(AfterSaleReturnTraceDO::getEventId, eventId));
    }
}
