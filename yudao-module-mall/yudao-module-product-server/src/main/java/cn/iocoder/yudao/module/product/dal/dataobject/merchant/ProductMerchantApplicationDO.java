package cn.iocoder.yudao.module.product.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.yudao.module.product.framework.merchant.core.MerchantSensitiveTypeHandler;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

/** Immutable submitted application version. Only a DRAFT row may be edited. */
@TableName(value = "product_merchant_application", autoResultMap = true)
@KeySequence("product_merchant_application_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ProductMerchantApplicationDO extends BaseDO {

    @TableId
    private Long id;
    private Long merchantId;
    private Long ownerAccountId;
    private Integer versionNo;
    private String type;
    private Integer status;
    private String businessType;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> businessTypes;
    private String merchantName;
    private String storeName;
    private String logoUrl;
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
    private LocalDateTime submittedTime;
    private String agreementVersion;
    private String privacyPolicyVersion;
    private Boolean consentAccepted;
    private LocalDateTime consentTime;
    private String consentIp;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String rejectReason;
}
