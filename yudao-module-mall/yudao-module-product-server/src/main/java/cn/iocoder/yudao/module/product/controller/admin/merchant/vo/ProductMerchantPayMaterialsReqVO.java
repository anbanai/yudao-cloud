package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.ToString;

import java.util.Map;

@Data
@Schema(description = "管理后台 - 已审核商家的支付入网补充资料")
public class ProductMerchantPayMaterialsReqVO {

    @NotNull
    private Long onboardingId;
    /** 易宝官方 JSON 对象，业务域不接收任何渠道密钥。 */
    @ToString.Exclude
    private Map<String, Object> materials;
    /** 官方字段路径 -> 私有资质文件编号；发送时临时换取短时效 URL。 */
    @ToString.Exclude
    private Map<String, Long> fileReferences;
    private String businessRole = "SHARE_MERCHANT";
}
