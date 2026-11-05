package cn.iocoder.yudao.module.product.controller.admin.category.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 商品分类新增/更新 Request VO")
@Data
public class ProductCategorySaveReqVO {

    private static final int DEFAULT_DISPLAY_PRODUCT_COUNT = 6;

    @Schema(description = "分类编号", example = "2")
    private Long id;

    @Schema(description = "父分类编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "父分类编号不能为空")
    private Long parentId;

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "办公文具")
    @NotBlank(message = "分类名称不能为空")
    private String name;

    @Schema(description = "移动端分类图", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "移动端分类图不能为空")
    private String picUrl;

    @Schema(description = "小程序展示商品数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "6")
    @Min(value = 1, message = "小程序展示商品数量必须大于等于 1")
    @Max(value = 50, message = "小程序展示商品数量必须小于等于 50")
    private Integer displayProductCount = DEFAULT_DISPLAY_PRODUCT_COUNT;

    @Schema(description = "分类排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer sort;

    @Schema(description = "开启状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "开启状态不能为空")
    private Integer status;

    @Schema(description = "分类描述", example = "描述")
    private String description;

}
