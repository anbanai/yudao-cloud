package cn.iocoder.yudao.module.product.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.product.framework.merchant.core.MerchantSensitiveTypeHandler;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 入网补料的不可变版本审计，敏感正文和私有文件引用一并加密。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "product_merchant_pay_material_revision", autoResultMap = true)
@KeySequence("product_merchant_pay_material_revision_seq")
public class ProductMerchantPayMaterialRevisionDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long onboardingId;
    private Long merchantId;
    private Integer materialsVersion;
    private String businessRole;
    private Long operatorId;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class)
    @ToString.Exclude
    private String materialsJson;
}
