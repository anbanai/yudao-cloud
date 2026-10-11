package cn.iocoder.yudao.module.product.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("product_merchant_qualification_requirement")
@KeySequence("product_merchant_qualification_requirement_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductMerchantQualificationRequirementDO extends BaseDO {

    @TableId
    private Long id;
    private String businessType;
    private String code;
    private String name;
    private Boolean required;
    private Boolean certificateNoRequired;
    private String certificateNoPattern;
    private Boolean validityRequired;
    private Boolean enabled;
    private Integer sort;
}
