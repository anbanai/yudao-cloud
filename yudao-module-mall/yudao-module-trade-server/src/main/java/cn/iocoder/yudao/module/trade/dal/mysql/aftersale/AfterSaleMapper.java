package cn.iocoder.yudao.module.trade.dal.mysql.aftersale;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.trade.controller.admin.aftersale.vo.AfterSalePageReqVO;
import cn.iocoder.yudao.module.trade.controller.app.aftersale.vo.AppAfterSalePageReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.aftersale.AfterSaleDO;
import cn.iocoder.yudao.module.trade.enums.aftersale.ReturnShipmentStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;

@Mapper
public interface AfterSaleMapper extends BaseMapperX<AfterSaleDO> {

    default PageResult<AfterSaleDO> selectPage(AfterSalePageReqVO reqVO) {
        LambdaQueryWrapperX<AfterSaleDO> wrapper = new LambdaQueryWrapperX<AfterSaleDO>()
                .eqIfPresent(AfterSaleDO::getUserId, reqVO.getUserId())
                .likeIfPresent(AfterSaleDO::getNo, reqVO.getNo())
                .eqIfPresent(AfterSaleDO::getStatus, reqVO.getStatus())
                .eqIfPresent(AfterSaleDO::getType, reqVO.getType())
                .eqIfPresent(AfterSaleDO::getWay, reqVO.getWay())
                .likeIfPresent(AfterSaleDO::getOrderNo, reqVO.getOrderNo())
                .likeIfPresent(AfterSaleDO::getSpuName, reqVO.getSpuName())
                .betweenIfPresent(AfterSaleDO::getCreateTime, reqVO.getCreateTime());
        if (reqVO.getReturnShipmentStatus() != null && !reqVO.getReturnShipmentStatus().isBlank()) {
            ReturnShipmentStatusEnum shipmentStatus;
            try {
                shipmentStatus = ReturnShipmentStatusEnum.valueOf(reqVO.getReturnShipmentStatus());
            } catch (IllegalArgumentException exception) {
                return PageResult.empty();
            }
            wrapper.apply("id IN (SELECT after_sale_id FROM trade_after_sale_return_shipment WHERE status = {0})",
                    shipmentStatus.name());
        }
        return selectPage(reqVO, wrapper.orderByDesc(AfterSaleDO::getId));
    }

    default PageResult<AfterSaleDO> selectPageByMerchantId(AfterSalePageReqVO reqVO, Long merchantId) {
        LambdaQueryWrapperX<AfterSaleDO> wrapper = new LambdaQueryWrapperX<AfterSaleDO>()
                .eqIfPresent(AfterSaleDO::getUserId, reqVO.getUserId())
                .likeIfPresent(AfterSaleDO::getNo, reqVO.getNo())
                .eqIfPresent(AfterSaleDO::getStatus, reqVO.getStatus())
                .eqIfPresent(AfterSaleDO::getType, reqVO.getType())
                .eqIfPresent(AfterSaleDO::getWay, reqVO.getWay())
                .likeIfPresent(AfterSaleDO::getOrderNo, reqVO.getOrderNo())
                .likeIfPresent(AfterSaleDO::getSpuName, reqVO.getSpuName())
                .betweenIfPresent(AfterSaleDO::getCreateTime, reqVO.getCreateTime());
        wrapper.apply("order_item_id IN (SELECT id FROM trade_order_item WHERE merchant_id = {0})", merchantId);
        if (reqVO.getReturnShipmentStatus() != null && !reqVO.getReturnShipmentStatus().isBlank()) {
            ReturnShipmentStatusEnum shipmentStatus;
            try {
                shipmentStatus = ReturnShipmentStatusEnum.valueOf(reqVO.getReturnShipmentStatus());
            } catch (IllegalArgumentException exception) {
                return PageResult.empty();
            }
            wrapper.apply("id IN (SELECT after_sale_id FROM trade_after_sale_return_shipment WHERE status = {0})",
                    shipmentStatus.name());
        }
        return selectPage(reqVO, wrapper.orderByDesc(AfterSaleDO::getId));
    }

    default PageResult<AfterSaleDO> selectPage(Long userId, AppAfterSalePageReqVO pageReqVO) {
        return selectPage(pageReqVO, new LambdaQueryWrapperX<AfterSaleDO>()
                .eq(AfterSaleDO::getUserId, userId)
                .inIfPresent(AfterSaleDO::getStatus, pageReqVO.getStatuses())
                .orderByDesc(AfterSaleDO::getId));
    }

    default int updateByIdAndStatus(Long id, Integer status, AfterSaleDO update) {
        return update(update, new LambdaUpdateWrapper<AfterSaleDO>()
                .eq(AfterSaleDO::getId, id).eq(AfterSaleDO::getStatus, status));
    }

    /** Atomically updates a seller-owned after-sale row. The merchant is resolved from the order item snapshot. */
    default int updateByIdAndStatusAndMerchantId(Long id, Integer status, Long merchantId, AfterSaleDO update) {
        return update(update, new LambdaUpdateWrapper<AfterSaleDO>()
                .eq(AfterSaleDO::getId, id).eq(AfterSaleDO::getStatus, status)
                .apply("order_item_id IN (SELECT id FROM trade_order_item WHERE merchant_id = {0})", merchantId));
    }

    default AfterSaleDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(AfterSaleDO::getId, id,
                AfterSaleDO::getUserId, userId);
    }

    default AfterSaleDO selectByIdAndMerchantId(Long id, Long merchantId) {
        return selectOne(new LambdaQueryWrapperX<AfterSaleDO>()
                .eq(AfterSaleDO::getId, id)
                .apply("order_item_id IN (SELECT id FROM trade_order_item WHERE merchant_id = {0})", merchantId));
    }

    default AfterSaleDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<AfterSaleDO>()
                .eq(AfterSaleDO::getId, id).last("FOR UPDATE"));
    }

    default Long selectCountByUserIdAndStatus(Long userId, Collection<Integer> statuses) {
        return selectCount(new LambdaQueryWrapperX<AfterSaleDO>()
                .eq(AfterSaleDO::getUserId, userId)
                .in(AfterSaleDO::getStatus, statuses));
    }

}
