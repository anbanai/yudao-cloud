package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypePageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypeRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypeSaveReqVO;

import java.util.List;

public interface ProductMerchantBusinessTypeService {

    List<ProductMerchantBusinessTypeRespVO> getList(Boolean enabledOnly);
    PageResult<ProductMerchantBusinessTypeRespVO> getPage(ProductMerchantBusinessTypePageReqVO reqVO);
    Long save(ProductMerchantBusinessTypeSaveReqVO reqVO);
    void delete(Long id);
}
