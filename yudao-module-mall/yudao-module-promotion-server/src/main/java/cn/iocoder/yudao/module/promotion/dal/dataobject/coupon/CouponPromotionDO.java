package cn.iocoder.yudao.module.promotion.dal.dataobject.coupon;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("promotion_coupon_promotion")
@KeySequence("promotion_coupon_promotion_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CouponPromotionDO extends BaseDO {
    @TableId
    private Long id;
    private Long templateId;
    private String source;
    private String channel;
    private String shortCode;
    private String miniProgramPath;
    private Integer visitCount;
    private Integer takeCount;
    private String idempotencyKey;
}
