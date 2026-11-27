package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 商家精简信息 Response VO")
@Data
public class ProductMerchantSimpleRespVO {

    private Long id;

    private String name;

    private String storeName;
}
