package cn.iocoder.yudao.module.product.dal.mysql.merchant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantApplicationQualificationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMerchantApplicationQualificationMapper extends BaseMapperX<ProductMerchantApplicationQualificationDO> {

    default List<ProductMerchantApplicationQualificationDO> selectListByApplicationId(Long applicationId) {
        return selectList(new LambdaQueryWrapperX<ProductMerchantApplicationQualificationDO>()
                .eq(ProductMerchantApplicationQualificationDO::getApplicationId, applicationId)
                .orderByAsc(ProductMerchantApplicationQualificationDO::getId));
    }

    default void deleteByApplicationId(Long applicationId) {
        delete(new LambdaQueryWrapperX<ProductMerchantApplicationQualificationDO>()
                .eq(ProductMerchantApplicationQualificationDO::getApplicationId, applicationId));
    }
}
