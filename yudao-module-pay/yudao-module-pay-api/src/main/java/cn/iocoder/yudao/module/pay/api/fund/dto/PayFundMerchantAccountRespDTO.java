package cn.iocoder.yudao.module.pay.api.fund.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 易宝商家资金账户的脱敏结果。 */
@Data
public class PayFundMerchantAccountRespDTO implements Serializable {

    private Long id;
    private Long merchantId;
    private String requestNo;
    private String applicationNo;
    private String merchantNo;
    /** 协议签署和实名认证地址，供管理后台补资料/签署使用。 */
    @ToString.Exclude
    private String signUrl;
    @ToString.Exclude
    private String authUrl;
    private Integer onboardingStatus;
    private Integer status;
    private String reason;
    private String lastError;
    private LocalDateTime lastRequestTime;
    private Integer materialsVersion;
}
