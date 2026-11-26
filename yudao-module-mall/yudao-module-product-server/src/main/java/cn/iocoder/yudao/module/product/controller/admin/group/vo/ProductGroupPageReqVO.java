package cn.iocoder.yudao.module.product.controller.admin.group.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 商品分组分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductGroupPageReqVO extends PageParam {
    @Schema(description = "名称")
    private String name;
    @Schema(description = "状态")
    private Integer status;
}
