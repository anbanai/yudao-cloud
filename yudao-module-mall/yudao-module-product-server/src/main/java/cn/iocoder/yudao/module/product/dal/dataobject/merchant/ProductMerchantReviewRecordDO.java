package cn.iocoder.yudao.module.product.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("product_merchant_review_record")
@KeySequence("product_merchant_review_record_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductMerchantReviewRecordDO extends BaseDO {

    @TableId
    private Long id;
    private Long merchantId;
    private Long applicationId;
    private Integer versionNo;
    private String action;
    private String reason;
    private Integer statusFrom;
    private Integer statusTo;
    private Long operatorId;
    private Integer operatorType;
    private Long reviewerId;
    private LocalDateTime reviewTime;
}
