package cn.iocoder.yudao.module.product.controller.admin.group.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 商品分组成员批量操作 Request VO")
@Data
public class ProductGroupSpuBatchReqVO {
    @NotNull(message = "分组不能为空")
    private Long groupId;
    @NotEmpty(message = "商品不能为空")
    @Size(max = 1000, message = "单次最多操作 1000 个商品")
    private List<Long> spuIds;
}
