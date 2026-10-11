package cn.iocoder.yudao.module.product.dal.mysql.merchant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantApplicationPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantApplicationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMerchantApplicationMapper extends BaseMapperX<ProductMerchantApplicationDO> {

    default ProductMerchantApplicationDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<ProductMerchantApplicationDO>()
                .eq(ProductMerchantApplicationDO::getId, id).last("FOR UPDATE"));
    }

    default ProductMerchantApplicationDO selectLatestByMerchantId(Long merchantId) {
        return selectOne(new LambdaQueryWrapperX<ProductMerchantApplicationDO>()
                .eq(ProductMerchantApplicationDO::getMerchantId, merchantId)
                .orderByDesc(ProductMerchantApplicationDO::getVersionNo).last("LIMIT 1"));
    }

    default ProductMerchantApplicationDO selectLatestByMerchantIdForUpdate(Long merchantId) {
        return selectOne(new LambdaQueryWrapperX<ProductMerchantApplicationDO>()
                .eq(ProductMerchantApplicationDO::getMerchantId, merchantId)
                .orderByDesc(ProductMerchantApplicationDO::getVersionNo).last("LIMIT 1 FOR UPDATE"));
    }

    default List<ProductMerchantApplicationDO> selectListByMerchantId(Long merchantId) {
        return selectList(new LambdaQueryWrapperX<ProductMerchantApplicationDO>()
                .eq(ProductMerchantApplicationDO::getMerchantId, merchantId)
                .orderByDesc(ProductMerchantApplicationDO::getVersionNo));
    }

    default ProductMerchantApplicationDO selectLatestByOwnerAccountId(Long ownerAccountId) {
        return selectOne(new LambdaQueryWrapperX<ProductMerchantApplicationDO>()
                .eq(ProductMerchantApplicationDO::getOwnerAccountId, ownerAccountId)
                .orderByDesc(ProductMerchantApplicationDO::getVersionNo).last("LIMIT 1"));
    }

    default PageResult<ProductMerchantApplicationDO> selectPage(ProductMerchantApplicationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductMerchantApplicationDO>()
                .eqIfPresent(ProductMerchantApplicationDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProductMerchantApplicationDO::getType, reqVO.getType())
                .and(reqVO.getBusinessType() != null, wrapper -> wrapper
                        .eq(ProductMerchantApplicationDO::getBusinessType, reqVO.getBusinessType())
                        .or().like(ProductMerchantApplicationDO::getBusinessTypes, "\"" + reqVO.getBusinessType() + "\""))
                .likeIfPresent(ProductMerchantApplicationDO::getMerchantName, reqVO.getMerchantName())
                .orderByDesc(ProductMerchantApplicationDO::getSubmittedTime)
                .orderByDesc(ProductMerchantApplicationDO::getId));
    }

    default Long selectCountByBusinessType(String businessType) {
        return selectList().stream().filter(application -> application.getBusinessTypes() != null
                ? application.getBusinessTypes().contains(businessType)
                : businessType.equals(application.getBusinessType())).count();
    }
}
