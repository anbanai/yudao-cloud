package cn.iocoder.yudao.module.product.controller.app.group.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户 App - 商品分组精简 Response VO")
public class AppProductGroupSimpleRespVO {
    private Long id;
    private String name;
}
