package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationDetailRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationDraftReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationSubmitReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantReviewRecordRespVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantApplicationDO;
import jakarta.validation.Valid;

import java.util.List;

public interface ProductMerchantApplicationService {

    void bindLegacyAccount(Long merchantId, Long accountId, Long operatorId);
    Long saveDraft(Long ownerAccountId, @Valid ProductMerchantApplicationDraftReqVO reqVO);
    ProductMerchantApplicationDetailRespVO getMine(Long ownerAccountId);
    List<ProductMerchantApplicationDetailRespVO> getHistory(Long ownerAccountId);
    void submit(Long ownerAccountId, @Valid ProductMerchantApplicationSubmitReqVO reqVO, String consentIp);
    PageResult<ProductMerchantApplicationDetailRespVO> getPage(ProductMerchantApplicationPageReqVO reqVO);
    ProductMerchantApplicationDetailRespVO get(Long applicationId);
    List<ProductMerchantApplicationDetailRespVO> getMerchantHistory(Long merchantId);
    void approve(Long applicationId, Long reviewerId);
    void reject(Long applicationId, String reason, Long reviewerId);
    List<ProductMerchantReviewRecordRespVO> getReviewRecords(Long merchantId);
}
