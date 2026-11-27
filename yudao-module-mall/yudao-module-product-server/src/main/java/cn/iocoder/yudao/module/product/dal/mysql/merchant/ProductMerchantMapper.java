package cn.iocoder.yudao.module.product.dal.mysql.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantStatusEnum;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMerchantMapper extends BaseMapperX<ProductMerchantDO> {

    String PLATFORM_MERCHANT_NAME = "平台自营商家";

    default PageResult<ProductMerchantDO> selectPage(ProductMerchantPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductMerchantDO>()
                .likeIfPresent(ProductMerchantDO::getName, reqVO.getName())
                .eqIfPresent(ProductMerchantDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ProductMerchantDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ProductMerchantDO::getId));
    }

    default ProductMerchantDO selectByName(String name) {
        return selectOne(ProductMerchantDO::getName, name);
    }

    default ProductMerchantDO selectByOwnerAccountId(Long ownerAccountId) {
        return selectOne(ProductMerchantDO::getOwnerAccountId, ownerAccountId);
    }

    default ProductMerchantDO selectByOwnerAccountIdForUpdate(Long ownerAccountId) {
        return selectOne(new LambdaQueryWrapperX<ProductMerchantDO>()
                .eq(ProductMerchantDO::getOwnerAccountId, ownerAccountId)
                .last("FOR UPDATE"));
    }

    default ProductMerchantDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<ProductMerchantDO>()
                .eq(ProductMerchantDO::getId, id)
                .last("FOR UPDATE"));
    }

    default PageResult<ProductMerchantDO> selectSelectablePage(PageParam pageParam, Long tenantId) {
        LambdaQueryWrapperX<ProductMerchantDO> query = managementMerchantQuery(tenantId);
        query.select(ProductMerchantDO::getId, ProductMerchantDO::getName, ProductMerchantDO::getStoreName)
                .eq(ProductMerchantDO::getStatus, ProductMerchantStatusEnum.APPROVED.getStatus())
                .orderByDesc(ProductMerchantDO::getId);
        return TenantUtils.executeIgnore(() -> selectPage(pageParam, query));
    }

    default ProductMerchantDO selectForManagement(Long id, Long tenantId) {
        if (id == null) {
            return null;
        }
        LambdaQueryWrapperX<ProductMerchantDO> query = managementMerchantQuery(tenantId);
        query.select(ProductMerchantDO::getId, ProductMerchantDO::getStatus)
                .eq(ProductMerchantDO::getId, id);
        return TenantUtils.executeIgnore(() -> selectOne(query));
    }

    private LambdaQueryWrapperX<ProductMerchantDO> managementMerchantQuery(Long tenantId) {
        LambdaQueryWrapperX<ProductMerchantDO> query = new LambdaQueryWrapperX<>();
        // 历史商品和品牌由迁移脚本统一关联到全局的平台自营商家。
        query.and(tenantId != null, scope -> scope.eq(ProductMerchantDO::getTenantId, tenantId)
                .or(platform -> platform.eq(ProductMerchantDO::getTenantId, 0L)
                        .eq(ProductMerchantDO::getName, PLATFORM_MERCHANT_NAME)
                        .isNull(ProductMerchantDO::getOwnerAccountId)));
        return query;
    }

    /**
     * 查询交易可用的商家主体。交易链路需要显式保留平台全局租户和当前租户。
     */
    default ProductMerchantDO selectAvailableById(Long id) {
        if (id == null) {
            return null;
        }
        return TenantUtils.executeIgnore(() -> selectOne(new LambdaQueryWrapperX<ProductMerchantDO>()
                .eq(ProductMerchantDO::getId, id)
                .apply("product_merchant.tenant_id IN (0, {0})", TenantContextHolder.getRequiredTenantId())));
    }
}
