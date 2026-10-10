package cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay;

import cn.iocoder.yudao.module.pay.enums.fund.PayYeepayMerchantAccountStatusEnum;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** 易宝商户资金账户 DO。 */
@TableName("pay_yeepay_merchant_account")
@KeySequence("pay_yeepay_merchant_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PayYeepayMerchantAccountDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long appId;
    /** 平台商家编号；一个支付应用下只允许一个当前有效的易宝商户账户。 */
    private Long merchantId;
    private String merchantNo;
    /** 易宝入网请求号，用于重试和幂等查询。 */
    private String requestNo;
    /** 易宝入网申请号。 */
    private String applicationNo;
    @ToString.Exclude
    private String signUrl;
    @ToString.Exclude
    private String authUrl;
    /** 仅保存密钥管理/对象存储引用，不保存身份证和银行卡原文。 */
    private String materialsRef;
    private Integer materialsVersion;
    private LocalDateTime lastRequestTime;
    /** 跨实例提交租约，领取后先提交数据库再调用渠道。 */
    private String submissionToken;
    private LocalDateTime submissionExpireTime;
    private String lastError;
    private String merchantName;
    private String accountNo;
    private String accountName;
    private Integer accountType;
    /** 入网状态；与账户启停状态分开保存。 */
    private Integer onboardingStatus;
    private Integer status;
    private String bankName;
    private String bankAccountNo;
    private String contactName;
    private String contactPhone;
    private String channelData;
    private String remark;

    /** 枚举 {@link PayYeepayMerchantAccountStatusEnum}。 */

    /**
     * 兼容早期调用方使用的“平台商家编号”命名。
     */
    public Long getPlatformMerchantId() {
        return merchantId;
    }

    public PayYeepayMerchantAccountDO setPlatformMerchantId(Long platformMerchantId) {
        this.merchantId = platformMerchantId;
        return this;
    }

    /**
     * 兼容早期调用方使用的“注册状态”命名。
     */
    public Integer getRegisterStatus() {
        return onboardingStatus;
    }

    public PayYeepayMerchantAccountDO setRegisterStatus(Integer registerStatus) {
        this.onboardingStatus = registerStatus;
        return this;
    }
}
