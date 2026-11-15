package cn.iocoder.yudao.module.promotion.dal.dataobject.coupon;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("promotion_coupon_exchange_record")
@KeySequence("promotion_coupon_exchange_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CouponExchangeRecordDO extends BaseDO {
    @TableId
    private Long id;
    private Long couponId;
    private Long userId;
    private Long spuId;
    private Long skuId;
    private Integer status;
    private Long orderId;
    private LocalDateTime fulfilledTime;
    private LocalDateTime cancelTime;
    private String idempotencyKey;
}
