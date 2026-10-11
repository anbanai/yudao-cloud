package cn.iocoder.yudao.module.product.dal.mysql.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantPageReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMerchantMapper extends BaseMapperX<ProductMerchantDO> {

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
}
