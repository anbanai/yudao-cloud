package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductMerchantApplicationDetailRespVO {

    private Long id;
    private Long merchantId;
    private Long ownerAccountId;
    /** 当前关联账号，历史申请快照的提交账号保持不变。 */
    private Long currentOwnerAccountId;
    private Integer versionNo;
    private String type;
    private Integer status;
    /** 当前商家经营状态，用于区分申请版本状态与经营门禁状态。 */
    private Integer merchantStatus;
    private String businessType;
    private List<String> businessTypes;
    private String name;
    private String storeName;
    private String logoUrl;
    private String companyName;
    private String creditCode;
    private String legalPerson;
    private String contactName;
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
    private List<ProductMerchantApplicationQualificationRespVO> qualifications;
}
