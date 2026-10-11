package cn.iocoder.yudao.module.product.controller.admin.merchant.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductMerchantApplicationPageReqVO extends PageParam {

    private Integer status;
    private String type;
    private String businessType;
    private String merchantName;
}
