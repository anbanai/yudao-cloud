package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationDO;

import java.util.List;

public interface ProductMerchantQualificationService {
    List<ProductMerchantQualificationDO> getList(Long merchantId);
}
