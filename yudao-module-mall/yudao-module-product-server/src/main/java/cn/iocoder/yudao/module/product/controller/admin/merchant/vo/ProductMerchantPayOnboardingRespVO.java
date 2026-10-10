package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

/** 仅展示流程状态和待办，绝不回显提交的敏感资料。 */
@Data
public class ProductMerchantPayOnboardingRespVO {

    private Long onboardingId;
    private Long merchantId;
    private String merchantName;
    private LocalDateTime merchantApprovalTime;
    private String businessRole;
    private String state;
    private Integer materialsVersion;
    private Integer onboardingStatus;
    private Integer attemptCount;
    private LocalDateTime nextRetryTime;
    private String lastError;
    private List<String> missingMaterials;
    @ToString.Exclude
    private String signUrl;
    @ToString.Exclude
    private String authUrl;
}
