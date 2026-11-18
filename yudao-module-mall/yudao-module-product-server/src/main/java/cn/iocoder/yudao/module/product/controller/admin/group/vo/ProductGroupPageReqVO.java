package cn.iocoder.yudao.module.product.controller.admin.group.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductGroupPageReqVO extends PageParam {
    private String name;
    private Integer status;
}
