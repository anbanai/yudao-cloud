package cn.iocoder.yudao.module.promotion.controller.admin.coupon.vo;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class CouponSendBatchReqVO {
    @NotNull private Long templateId;
    private List<String> mobiles;
    private List<Long> userIds;
    private List<Long> tagIds;
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 64) private String idempotencyKey;
    private Boolean submit;
}
