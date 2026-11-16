package cn.iocoder.yudao.module.product.api.spu.dto;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ProductSpuAccessRespDTO {
    private Long spuId;
    private Boolean visible;
    private Boolean purchasable;
    private Integer effectiveBuyLimit;
    private Integer deliveryDays;
    private String reason;
}
