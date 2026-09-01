package cn.iocoder.yudao.module.product.controller.admin.group.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 商品分组 Response VO")
@Data
public class ProductGroupRespVO {
    private Long id;
    private String name;
    private Integer sort;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
