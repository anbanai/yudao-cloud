package cn.iocoder.yudao.module.product.controller.admin.group.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 商品分组精简 Response VO")
@Data
public class ProductGroupSimpleRespVO {
    private Long id;
    private String name;
    private Integer status;
}
