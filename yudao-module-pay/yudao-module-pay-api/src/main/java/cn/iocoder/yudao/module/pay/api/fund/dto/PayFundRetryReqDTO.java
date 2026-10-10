package cn.iocoder.yudao.module.pay.api.fund.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/** 人工资金操作必须记录操作人、原因和外部凭证。管理入口覆盖 operatorId。 */
@Data
public class PayFundRetryReqDTO {
    @NotNull @Positive
    private Long targetId;
    private Long operatorId;
    @NotBlank @Size(max = 512)
    private String reason;
    @NotBlank @Size(max = 1024)
    private String externalEvidence;
}
