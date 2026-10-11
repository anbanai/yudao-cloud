package cn.iocoder.yudao.module.product.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("product_merchant_business_type")
@KeySequence("product_merchant_business_type_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductMerchantBusinessTypeDO extends BaseDO {

    @TableId
    private Long id;
    private String code;
    private String name;
    private Boolean enabled;
    private Integer sort;
}
