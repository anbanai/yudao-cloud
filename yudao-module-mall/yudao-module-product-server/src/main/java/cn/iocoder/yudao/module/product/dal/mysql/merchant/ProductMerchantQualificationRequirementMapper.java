package cn.iocoder.yudao.module.product.dal.mysql.merchant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationRequirementDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMerchantQualificationRequirementMapper extends BaseMapperX<ProductMerchantQualificationRequirementDO> {

    default List<ProductMerchantQualificationRequirementDO> selectListByBusinessType(String businessType) {
        return selectList(new LambdaQueryWrapperX<ProductMerchantQualificationRequirementDO>()
                .eq(ProductMerchantQualificationRequirementDO::getBusinessType, businessType)
                .orderByAsc(ProductMerchantQualificationRequirementDO::getSort)
                .orderByAsc(ProductMerchantQualificationRequirementDO::getId));
    }

    default List<ProductMerchantQualificationRequirementDO> selectEnabledListByBusinessType(String businessType) {
        return selectList(new LambdaQueryWrapperX<ProductMerchantQualificationRequirementDO>()
                .eq(ProductMerchantQualificationRequirementDO::getBusinessType, businessType)
                .eq(ProductMerchantQualificationRequirementDO::getEnabled, true)
                .orderByAsc(ProductMerchantQualificationRequirementDO::getSort)
                .orderByAsc(ProductMerchantQualificationRequirementDO::getId));
    }

    default void deleteByBusinessType(String businessType) {
        delete(new LambdaQueryWrapperX<ProductMerchantQualificationRequirementDO>()
                .eq(ProductMerchantQualificationRequirementDO::getBusinessType, businessType));
    }
}
