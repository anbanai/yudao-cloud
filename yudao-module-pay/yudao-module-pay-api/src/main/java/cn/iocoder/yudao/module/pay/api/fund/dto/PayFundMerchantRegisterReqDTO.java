package cn.iocoder.yudao.module.pay.api.fund.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.Map;

/** 平台代商家发起易宝入网请求。敏感资料只在请求链路中传递，不写入日志。 */
@Data
public class PayFundMerchantRegisterReqDTO implements Serializable {

    @NotNull(message = "应用标识不能为空")
    private String appKey;
    @NotNull(message = "平台商家编号不能为空")
    private Long merchantId;
    private String merchantName;
    /** 由平台文件服务生成的安全引用和易宝所需资料。 */
    @ToString.Exclude
    private Map<String, Object> materials;
    private String businessRole;
    /** 重试补资料时复用原申请号。 */
    private String requestNo;
}
