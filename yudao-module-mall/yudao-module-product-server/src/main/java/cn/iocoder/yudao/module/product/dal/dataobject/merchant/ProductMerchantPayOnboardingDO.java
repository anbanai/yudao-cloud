package cn.iocoder.yudao.module.product.dal.dataobject.merchant;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.iocoder.yudao.module.product.framework.merchant.core.MerchantSensitiveTypeHandler;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** 与商户审核同事务保存的支付入网任务。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "product_merchant_pay_onboarding", autoResultMap = true)
@KeySequence("product_merchant_pay_onboarding_seq")
public class ProductMerchantPayOnboardingDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long merchantId;
    private LocalDateTime merchantApprovalTime;
    private String appKey;
    private String requestNo;
    private String businessRole;
    private String state;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class)
    @ToString.Exclude
    private String materialsJson;
    private Integer materialsVersion;
    private Integer attemptCount;
    private Boolean requestAccepted;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime nextRetryTime;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lastError;
    private Integer onboardingStatus;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    @ToString.Exclude
    private String signUrl;
    @TableField(typeHandler = MerchantSensitiveTypeHandler.class, updateStrategy = FieldStrategy.ALWAYS)
    @ToString.Exclude
    private String authUrl;
}
