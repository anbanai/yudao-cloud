package cn.iocoder.yudao.module.product.dal.mysql.merchant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPayOnboardingPageReqVO;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantPayOnboardingDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductMerchantPayOnboardingMapper extends BaseMapperX<ProductMerchantPayOnboardingDO> {

    default PageResult<ProductMerchantPayOnboardingDO> selectPage(ProductMerchantPayOnboardingPageReqVO reqVO, Long tenantId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductMerchantPayOnboardingDO>()
                .eq(ProductMerchantPayOnboardingDO::getTenantId, tenantId)
                .eqIfPresent(ProductMerchantPayOnboardingDO::getMerchantId, reqVO.getMerchantId())
                .eqIfPresent(ProductMerchantPayOnboardingDO::getId, reqVO.getOnboardingId())
                .eqIfPresent(ProductMerchantPayOnboardingDO::getState, reqVO.getState())
                .orderByDesc(ProductMerchantPayOnboardingDO::getId));
    }

    default ProductMerchantPayOnboardingDO selectByIdAndTenant(Long id, Long tenantId, boolean lock) {
        LambdaQueryWrapperX<ProductMerchantPayOnboardingDO> query = new LambdaQueryWrapperX<ProductMerchantPayOnboardingDO>()
                .eq(ProductMerchantPayOnboardingDO::getId, id)
                .eq(ProductMerchantPayOnboardingDO::getTenantId, tenantId);
        if (lock) {
            query.last("FOR UPDATE");
        }
        return selectOne(query);
    }

    default List<ProductMerchantPayOnboardingDO> selectByMerchantId(Long merchantId, Long tenantId) {
        return selectList(new LambdaQueryWrapperX<ProductMerchantPayOnboardingDO>()
                .eq(ProductMerchantPayOnboardingDO::getMerchantId, merchantId)
                .eq(ProductMerchantPayOnboardingDO::getTenantId, tenantId));
    }

    default List<ProductMerchantPayOnboardingDO> selectDue(Long tenantId) {
        return selectList(new LambdaQueryWrapperX<ProductMerchantPayOnboardingDO>()
                .eq(ProductMerchantPayOnboardingDO::getTenantId, tenantId)
                .in(ProductMerchantPayOnboardingDO::getState, List.of("READY", "RETRY", "SUBMITTED", "ACTION_REQUIRED"))
                .le(ProductMerchantPayOnboardingDO::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(ProductMerchantPayOnboardingDO::getNextRetryTime).last("LIMIT 100"));
    }
}
