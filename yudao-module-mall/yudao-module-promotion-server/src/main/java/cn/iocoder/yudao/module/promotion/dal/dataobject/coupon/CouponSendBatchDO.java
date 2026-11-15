package cn.iocoder.yudao.module.promotion.dal.dataobject.coupon;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("promotion_coupon_send_batch")
@KeySequence("promotion_coupon_send_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class CouponSendBatchDO extends BaseDO {
    @TableId
    private Long id;
    private Long templateId;
    private Long operatorId;
    private Integer status;
    private String idempotencyKey;
    private Integer totalCount;
    private Integer successCount;
    private Integer failureCount;
    private String mobileSnapshot;
    private String userSnapshot;
    private String failureDetails;
    private String templateSnapshot;
}
