package cn.iocoder.yudao.module.product.controller.admin.group.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 商品分组成员分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductGroupSpuPageReqVO extends PageParam {
    @NotNull(message = "分组不能为空")
    private Long groupId;
    private String keyword;
    private Integer status;
}
