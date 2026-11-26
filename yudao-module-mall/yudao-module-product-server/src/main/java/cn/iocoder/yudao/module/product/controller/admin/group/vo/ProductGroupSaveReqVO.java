package cn.iocoder.yudao.module.product.controller.admin.group.vo;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 商品分组新增/更新 Request VO")
@Data
public class ProductGroupSaveReqVO {
    @Schema(description = "编号", example = "1")
    private Long id;
    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "新品")
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 64, message = "分组名称长度不能超过 64 个字符")
    private String name;
    @Schema(description = "排序", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "排序不能为空")
    private Integer sort;
    @Schema(description = "状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    @InEnum(CommonStatusEnum.class)
    private Integer status;
    @Schema(description = "备注")
    private String remark;
}
