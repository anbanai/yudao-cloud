package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商家分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductMerchantPageReqVO extends PageParam {
    @Schema(description = "商家名称")
    private String name;
    @Schema(description = "状态")
    private Integer status;
    @Schema(description = "创建时间")
    private LocalDateTime[] createTime;
}
