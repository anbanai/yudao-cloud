package cn.iocoder.yudao.module.product.controller.admin.group.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "管理后台 - 批量追加或移出商品分组 Request VO")
public class ProductGroupSpuUpdateReqVO {
    @NotEmpty @Size(max = 200)
    private List<@NotNull @Positive Long> spuIds;
    @NotEmpty @Size(max = 15)
    private List<@NotNull @Positive Long> groupIds;
    @NotNull @Pattern(regexp = "add|remove")
    private String operation;
}
