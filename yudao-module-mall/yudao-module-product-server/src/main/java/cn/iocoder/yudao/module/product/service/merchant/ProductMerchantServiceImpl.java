package cn.iocoder.yudao.module.product.service.merchant;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantReviewRecordDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantApplicationDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantApplicationMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantReviewRecordMapper;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ProductMerchantServiceImpl implements ProductMerchantService {

    @Resource
    private ProductMerchantMapper merchantMapper;
    @Resource
    private ProductMerchantApplicationMapper applicationMapper;
    @Resource
    private ProductMerchantReviewRecordMapper reviewRecordMapper;

    @Override
    public ProductMerchantDO getMerchant(Long id) {
        return merchantMapper.selectById(id);
    }

    @Override
    public PageResult<ProductMerchantDO> getMerchantPage(ProductMerchantPageReqVO reqVO) {
        return merchantMapper.selectPage(reqVO);
    }

    @Override
    public void approveByApplication(Long id, Long reviewerId, Integer expectedStatus) {
        ProductMerchantDO merchant = validateExists(id);
        if (!java.util.Objects.equals(expectedStatus, merchant.getStatus())) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        int updateCount = merchantMapper.update(new ProductMerchantDO()
                        .setStatus(ProductMerchantStatusEnum.APPROVED.getStatus())
                        .setReviewerId(reviewerId).setReviewTime(LocalDateTime.now()).setRejectReason(null),
                new LambdaUpdateWrapper<ProductMerchantDO>()
                        .eq(ProductMerchantDO::getId, id)
                        .eq(ProductMerchantDO::getStatus, expectedStatus));
        if (updateCount == 0) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freezeMerchant(Long id, Long reviewerId) {
        ProductMerchantDO merchant = validateExists(id);
        if (!ProductMerchantStatusEnum.APPROVED.getStatus().equals(merchant.getStatus())) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        int updateCount = merchantMapper.update(new ProductMerchantDO()
                        .setStatus(ProductMerchantStatusEnum.FROZEN.getStatus())
                        .setReviewerId(reviewerId).setReviewTime(now),
                new LambdaUpdateWrapper<ProductMerchantDO>()
                        .eq(ProductMerchantDO::getId, id)
                        .eq(ProductMerchantDO::getStatus, ProductMerchantStatusEnum.APPROVED.getStatus()));
        if (updateCount != 1) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        appendStatusRecord(merchant, reviewerId, "FREEZE", ProductMerchantStatusEnum.FROZEN.getStatus(), now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreezeMerchant(Long id, Long reviewerId) {
        ProductMerchantDO merchant = validateExists(id);
        if (!ProductMerchantStatusEnum.FROZEN.getStatus().equals(merchant.getStatus())) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        int updateCount = merchantMapper.update(new ProductMerchantDO()
                        .setStatus(ProductMerchantStatusEnum.APPROVED.getStatus())
                        .setReviewerId(reviewerId).setReviewTime(now),
                new LambdaUpdateWrapper<ProductMerchantDO>()
                        .eq(ProductMerchantDO::getId, id)
                        .eq(ProductMerchantDO::getStatus, ProductMerchantStatusEnum.FROZEN.getStatus()));
        if (updateCount != 1) {
            throw exception(MERCHANT_REVIEW_STATUS_ERROR);
        }
        appendStatusRecord(merchant, reviewerId, "UNFREEZE", ProductMerchantStatusEnum.APPROVED.getStatus(), now);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStoreProfile(Long merchantId, String storeName, String logoUrl, String description) {
        validateExists(merchantId);
        merchantMapper.updateById(new ProductMerchantDO().setId(merchantId).setStoreName(storeName)
                .setLogoUrl(logoUrl).setDescription(description));
    }

    @Override
    public void validateMerchantAvailable(Long id) {
        ProductMerchantDO merchant = merchantMapper.selectAvailableById(id);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        if (!ProductMerchantStatusEnum.isAvailable(merchant.getStatus())) {
            throw exception(MERCHANT_NOT_AVAILABLE);
        }
    }

    @Override
    public void validateMerchantForManagement(Long id) {
        ProductMerchantDO merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        if (!java.util.Objects.equals(merchant.getTenantId(), TenantContextHolder.getRequiredTenantId())) {
            throw exception(MERCHANT_TENANT_MISMATCH);
        }
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

    private void appendStatusRecord(ProductMerchantDO merchant, Long reviewerId, String action,
                                    Integer statusTo, LocalDateTime now) {
        ProductMerchantApplicationDO application = applicationMapper.selectLatestByMerchantId(merchant.getId());
        reviewRecordMapper.insert(new ProductMerchantReviewRecordDO().setMerchantId(merchant.getId())
                .setApplicationId(application == null ? null : application.getId())
                .setVersionNo(application == null ? null : application.getVersionNo())
                .setAction(action).setStatusFrom(merchant.getStatus()).setStatusTo(statusTo)
                .setOperatorId(reviewerId).setOperatorType(UserTypeEnum.ADMIN.getValue())
                .setReviewerId(reviewerId).setReviewTime(now));
    }

}
