package cn.iocoder.yudao.module.product.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.product.framework.merchant.core.MerchantSensitiveTypeHandler;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantStatusEnum;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

/** 平台商家 DO。 */
@TableName(value = "product_merchant", autoResultMap = true)
@KeySequence("product_merchant_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductMerchantDO extends TenantBaseDO {

    @TableId
    private Long id;
    private String name;
    private String storeName;
    private String logoUrl;
    private String description;
    private String companyName;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class)
    @ToString.Exclude
    private String creditCode;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class)
    @ToString.Exclude
    private String legalPerson;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class)
    @ToString.Exclude
    private String contactName;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class)
    @ToString.Exclude
    private String contactMobile;
    private Long ownerAccountId;
    private String businessType;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> businessTypes;
    private Integer status;
    private Integer approvedRevision;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String rejectReason;
}
