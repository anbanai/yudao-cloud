package cn.iocoder.yudao.module.promotion.dal.mysql.coupon;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponExchangeRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CouponExchangeRecordMapper extends BaseMapperX<CouponExchangeRecordDO> {
    default CouponExchangeRecordDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<CouponExchangeRecordDO>().eq(CouponExchangeRecordDO::getIdempotencyKey, key));
    }
}
