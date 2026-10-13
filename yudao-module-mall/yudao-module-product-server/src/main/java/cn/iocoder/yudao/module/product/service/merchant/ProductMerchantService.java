package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;

public interface ProductMerchantService {
    ProductMerchantDO getMerchant(Long id);
    /**
     * 查询交易链路使用的商家，显式保留平台全局租户与当前租户数据。
     */
    ProductMerchantDO getMerchantForTrade(Long id);
    PageResult<ProductMerchantDO> getMerchantPage(ProductMerchantPageReqVO reqVO);
    /**
     * Approves an application after checking the merchant is still in the expected review state.
     * The expected state is part of the transition contract so a stale review cannot approve a
     * different application type after another operation changed the merchant status.
     */
    void approveByApplication(Long id, Long reviewerId, Integer expectedStatus);
    void freezeMerchant(Long id, Long reviewerId);
    void unfreezeMerchant(Long id, Long reviewerId);
    void updateStoreProfile(Long merchantId, String storeName, String logoUrl, String description);
    void validateMerchantAvailable(Long id);
    /** 校验后台经营数据使用的商家属于当前租户且处于可经营状态。 */
    void validateMerchantForManagement(Long id);
}
