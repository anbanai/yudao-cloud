package cn.iocoder.yudao.module.product.controller.admin.group.vo;

import cn.iocoder.yudao.module.product.controller.admin.spu.vo.ProductSpuRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 商品分组成员 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductGroupSpuRespVO extends ProductSpuRespVO {

    @Schema(description = "组内排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer groupSort;

}
