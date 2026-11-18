package cn.iocoder.yudao.module.trade.dal.mysql.order;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mapper
public interface TradeOrderItemMapper extends BaseMapperX<TradeOrderItemDO> {

    @org.apache.ibatis.annotations.Select("SELECT COALESCE(SUM(i.count),0) FROM trade_order_item i INNER JOIN trade_order o ON o.id=i.order_id WHERE o.user_id=#{userId} AND o.tenant_id=#{tenantId} AND o.status<>40 AND COALESCE(i.after_sale_status,0)<>20 AND i.spu_id=#{spuId}")
    int selectPurchasedCountByUserIdAndSpuId(@Param("userId") Long userId, @Param("spuId") Long spuId, @Param("tenantId") Long tenantId);

    default int updateAfterSaleStatus(Long id, Integer oldAfterSaleStatus, Integer newAfterSaleStatus,
                                      Long afterSaleId) {
        return update(new TradeOrderItemDO().setAfterSaleStatus(newAfterSaleStatus).setAfterSaleId(afterSaleId),
                new LambdaUpdateWrapper<>(new TradeOrderItemDO().setId(id).setAfterSaleStatus(oldAfterSaleStatus)));
    }

    default List<TradeOrderItemDO> selectListByOrderId(Long orderId) {
        return selectList(TradeOrderItemDO::getOrderId, orderId);
    }

    default List<TradeOrderItemDO> selectListByOrderId(Collection<Long> orderIds) {
        return selectList(TradeOrderItemDO::getOrderId, orderIds);
    }

    default TradeOrderItemDO selectByIdAndUserId(Long orderItemId, Long loginUserId) {
        return selectOne(new LambdaQueryWrapperX<TradeOrderItemDO>()
                .eq(TradeOrderItemDO::getId, orderItemId)
                .eq(TradeOrderItemDO::getUserId, loginUserId));
    }

    default List<TradeOrderItemDO> selectListByOrderIdAndCommentStatus(Long orderId, Boolean commentStatus) {
        return selectList(new LambdaQueryWrapperX<TradeOrderItemDO>()
                .eq(TradeOrderItemDO::getOrderId, orderId)
                .eq(TradeOrderItemDO::getCommentStatus, commentStatus));
    }

    default int selectProductSumByOrderId(@Param("orderIds") Set<Long> orderIds) {
        // SQL sum 查询
        List<Map<String, Object>> result = selectMaps(new QueryWrapper<TradeOrderItemDO>()
                .select("SUM(count) AS sumCount")
                .in("order_id", orderIds)); // 只计算选中的
        // 获得数量
        return CollUtil.getFirst(result) != null ? MapUtil.getInt(result.get(0), "sumCount") : 0;
    }

}
