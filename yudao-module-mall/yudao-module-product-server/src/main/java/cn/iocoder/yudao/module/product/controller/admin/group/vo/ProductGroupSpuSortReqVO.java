package cn.iocoder.yudao.module.product.controller.admin.group.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "管理后台 - 商品分组成员排序 Request VO")
public class ProductGroupSpuSortReqVO {
    @NotNull private Long groupId;
    @NotNull private Long spuId;
    @NotNull private Integer sort;
}
