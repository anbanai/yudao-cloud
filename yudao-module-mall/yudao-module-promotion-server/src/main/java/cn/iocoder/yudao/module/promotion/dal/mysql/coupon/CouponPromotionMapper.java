package cn.iocoder.yudao.module.promotion.dal.mysql.coupon;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponPromotionDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CouponPromotionMapper extends BaseMapperX<CouponPromotionDO> {
    default CouponPromotionDO selectByIdempotencyKey(String key) {
        return selectOne(new LambdaQueryWrapperX<CouponPromotionDO>().eq(CouponPromotionDO::getIdempotencyKey, key));
    }
    default CouponPromotionDO selectByShortCode(String code) {
        return selectOne(new LambdaQueryWrapperX<CouponPromotionDO>().eq(CouponPromotionDO::getShortCode, code));
    }
}
