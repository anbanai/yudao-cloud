package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductMerchantBusinessTypePageReqVO extends PageParam {

    private String code;
    private String name;
    private Boolean enabled;
}
