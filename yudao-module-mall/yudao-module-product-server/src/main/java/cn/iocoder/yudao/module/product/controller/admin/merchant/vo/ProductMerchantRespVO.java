package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商家 Response VO")
@Data
public class ProductMerchantRespVO extends ProductMerchantSaveReqVO {
    private Integer status;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String rejectReason;
    private LocalDateTime createTime;
}
