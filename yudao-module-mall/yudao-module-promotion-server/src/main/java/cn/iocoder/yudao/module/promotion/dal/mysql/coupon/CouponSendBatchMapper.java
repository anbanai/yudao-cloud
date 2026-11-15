package cn.iocoder.yudao.module.promotion.dal.mysql.coupon;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponSendBatchDO;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponSendBatchStatusEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CouponSendBatchMapper extends BaseMapperX<CouponSendBatchDO> {
    default CouponSendBatchDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<CouponSendBatchDO>().eq(CouponSendBatchDO::getIdempotencyKey, key));
    }

    default int markFailedIfPending(Long id, Long operatorId, int failureCount, String failureDetails) {
        return update(null, new LambdaUpdateWrapper<CouponSendBatchDO>()
                .eq(CouponSendBatchDO::getId, id)
                .eq(CouponSendBatchDO::getOperatorId, operatorId)
                .eq(CouponSendBatchDO::getStatus, CouponSendBatchStatusEnum.PENDING.getStatus())
                .set(CouponSendBatchDO::getStatus, CouponSendBatchStatusEnum.FAILED.getStatus())
                .set(CouponSendBatchDO::getSuccessCount, 0)
                .set(CouponSendBatchDO::getFailureCount, failureCount)
                .set(CouponSendBatchDO::getFailureDetails, failureDetails));
    }
}
