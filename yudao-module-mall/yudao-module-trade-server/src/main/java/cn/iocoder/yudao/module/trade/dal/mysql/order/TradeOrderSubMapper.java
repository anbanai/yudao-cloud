package cn.iocoder.yudao.module.trade.dal.mysql.order;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.trade.controller.merchant.order.vo.MerchantOrderPageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import org.apache.ibatis.annotations.Mapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;

import java.util.List;

@Mapper
public interface TradeOrderSubMapper extends BaseMapperX<TradeOrderSubDO> {
    default PageResult<TradeOrderSubDO> selectPageByMerchantId(MerchantOrderPageReqVO reqVO, Long merchantId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<TradeOrderSubDO>()
                .eq(TradeOrderSubDO::getMerchantId, merchantId)
                .likeIfPresent(TradeOrderSubDO::getNo, reqVO.getNo())
                .eqIfPresent(TradeOrderSubDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(TradeOrderSubDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(TradeOrderSubDO::getId));
    }

    default TradeOrderSubDO selectByIdAndMerchantId(Long id, Long merchantId) {
        return selectOne(TradeOrderSubDO::getId, id, TradeOrderSubDO::getMerchantId, merchantId);
    }

    default TradeOrderSubDO selectByOrderIdAndMerchantId(Long orderId, Long merchantId) {
        return selectOne(TradeOrderSubDO::getOrderId, orderId, TradeOrderSubDO::getMerchantId, merchantId);
    }

    default List<TradeOrderSubDO> selectListByOrderId(Long orderId) {
        return selectList(TradeOrderSubDO::getOrderId, orderId);
    }

    default int updateStatusByOrderId(Long orderId, Integer oldStatus, Integer newStatus) {
        return update(new TradeOrderSubDO().setStatus(newStatus), new LambdaUpdateWrapper<TradeOrderSubDO>()
                .eq(TradeOrderSubDO::getOrderId, orderId).eq(TradeOrderSubDO::getStatus, oldStatus));
    }

    default int updateByOrderIdAndStatus(Long orderId, Integer oldStatus, TradeOrderSubDO update) {
        return update(update, new LambdaUpdateWrapper<TradeOrderSubDO>()
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
