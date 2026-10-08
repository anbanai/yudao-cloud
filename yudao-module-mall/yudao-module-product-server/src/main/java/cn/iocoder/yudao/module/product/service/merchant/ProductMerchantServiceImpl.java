package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPageReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantReviewReqVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantSaveReqVO;
import cn.iocoder.yudao.module.product.convert.merchant.ProductMerchantConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantMapper;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ProductMerchantServiceImpl implements ProductMerchantService {

    @Resource
    private ProductMerchantMapper merchantMapper;

    @Override
    public Long createMerchant(ProductMerchantSaveReqVO reqVO) {
        validateNameUnique(null, reqVO.getName());
        ProductMerchantDO merchant = ProductMerchantConvert.INSTANCE.convert(reqVO);
        merchant.setStatus(ProductMerchantStatusEnum.DRAFT.getStatus());
        merchantMapper.insert(merchant);
        return merchant.getId();
    }

    @Override
    public void updateMerchant(ProductMerchantSaveReqVO reqVO) {
        ProductMerchantDO merchant = validateExists(reqVO.getId());
        validateNameUnique(reqVO.getId(), reqVO.getName());
        ProductMerchantDO update = ProductMerchantConvert.INSTANCE.convert(reqVO);
        update.setStatus(merchant.getStatus());
        merchantMapper.updateById(update);
    }

    @Override
    public ProductMerchantDO getMerchant(Long id) {
        return merchantMapper.selectById(id);
    }

    @Override
    public PageResult<ProductMerchantDO> getMerchantPage(ProductMerchantPageReqVO reqVO) {
        return merchantMapper.selectPage(reqVO);
    }

    @Override
    public void submitReview(Long id) {
        ProductMerchantDO merchant = validateExists(id);
        if (!ProductMerchantStatusEnum.DRAFT.getStatus().equals(merchant.getStatus())
                && !ProductMerchantStatusEnum.REJECTED.getStatus().equals(merchant.getStatus())) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        merchantMapper.updateById(new ProductMerchantDO().setId(id)
                .setStatus(ProductMerchantStatusEnum.PENDING.getStatus()).setRejectReason(null));
    }

    @Override
    public void approveMerchant(Long id, Long reviewerId) {
        ProductMerchantDO merchant = validateExists(id);
        if (!ProductMerchantStatusEnum.PENDING.getStatus().equals(merchant.getStatus())) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        merchantMapper.updateById(new ProductMerchantDO().setId(id)
                .setStatus(ProductMerchantStatusEnum.APPROVED.getStatus())
                .setReviewerId(reviewerId).setReviewTime(LocalDateTime.now()).setRejectReason(null));
    }

    @Override
    public void rejectMerchant(ProductMerchantReviewReqVO reqVO, Long reviewerId) {
        ProductMerchantDO merchant = validateExists(reqVO.getId());
        if (!ProductMerchantStatusEnum.PENDING.getStatus().equals(merchant.getStatus())) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        merchantMapper.updateById(new ProductMerchantDO().setId(reqVO.getId())
                .setStatus(ProductMerchantStatusEnum.REJECTED.getStatus())
                .setReviewerId(reviewerId).setReviewTime(LocalDateTime.now()).setRejectReason(reqVO.getReason()));
    }

    @Override
    public void freezeMerchant(Long id, Long reviewerId) {
        validateExists(id);
        merchantMapper.updateById(new ProductMerchantDO().setId(id)
                .setStatus(ProductMerchantStatusEnum.FROZEN.getStatus()).setReviewerId(reviewerId));
    }

    @Override
    public void unfreezeMerchant(Long id, Long reviewerId) {
        validateExists(id);
        merchantMapper.updateById(new ProductMerchantDO().setId(id)
                .setStatus(ProductMerchantStatusEnum.APPROVED.getStatus()).setReviewerId(reviewerId));
    }

    @Override
    public void validateMerchantAvailable(Long id) {
        ProductMerchantDO merchant = validateExists(id);
        if (!ProductMerchantStatusEnum.isAvailable(merchant.getStatus())) {
            throw exception(MERCHANT_NOT_AVAILABLE);
        }
    }

    private ProductMerchantDO validateExists(Long id) {
        ProductMerchantDO merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        return merchant;
    }

    private void validateNameUnique(Long id, String name) {
        ProductMerchantDO merchant = merchantMapper.selectByName(name);
        if (merchant != null && !merchant.getId().equals(id)) {
            throw exception(MERCHANT_NAME_EXISTS);
        }
    }
}
