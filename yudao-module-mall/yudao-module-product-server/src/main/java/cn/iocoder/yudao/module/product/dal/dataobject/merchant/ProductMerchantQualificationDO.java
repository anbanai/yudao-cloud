package cn.iocoder.yudao.module.product.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.product.framework.merchant.core.MerchantSensitiveTypeHandler;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantQualificationStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDate;

/** 商家资质 DO。 */
@TableName(value = "product_merchant_qualification", autoResultMap = true)
@KeySequence("product_merchant_qualification_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMerchantQualificationDO extends BaseDO {

    @TableId
    private Long id;
    private Long merchantId;
    private String type;
    private Long fileId;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class)
    @ToString.Exclude
    private String certificateNo;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class)
    @ToString.Exclude
    private String fileUrl;
    private LocalDate validStart;
    private LocalDate validEnd;
    private Integer status;
    private Long reviewerId;
    private String reviewReason;
}
