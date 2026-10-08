package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantReviewReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import jakarta.validation.Valid;

public interface ProductMerchantService {
    Long createMerchant(@Valid ProductMerchantSaveReqVO reqVO);
    void updateMerchant(@Valid ProductMerchantSaveReqVO reqVO);
    ProductMerchantDO getMerchant(Long id);
    PageResult<ProductMerchantDO> getMerchantPage(ProductMerchantPageReqVO reqVO);
    void submitReview(Long id);
    void approveMerchant(Long id, Long reviewerId);
    void rejectMerchant(@Valid ProductMerchantReviewReqVO reqVO, Long reviewerId);
    void freezeMerchant(Long id, Long reviewerId);
    void unfreezeMerchant(Long id, Long reviewerId);
    void validateMerchantAvailable(Long id);
}
