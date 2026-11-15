package cn.iocoder.yudao.module.promotion.dal.dataobject.coupon;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
@TableName("promotion_coupon_promotion_take")
@Data
@EqualsAndHashCode(callSuper = true)
public class CouponPromotionTakeDO extends BaseDO {
    @TableId private Long id;
    private Long promotionId;
    private Long userId;
    private Long couponId;
}
