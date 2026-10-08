package cn.iocoder.yudao.module.product.dal.mysql.merchant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMerchantQualificationMapper extends BaseMapperX<ProductMerchantQualificationDO> {

    default List<ProductMerchantQualificationDO> selectListByMerchantId(Long merchantId) {
        return selectList(new LambdaQueryWrapperX<ProductMerchantQualificationDO>()
                .eq(ProductMerchantQualificationDO::getMerchantId, merchantId)
                .orderByDesc(ProductMerchantQualificationDO::getId));
    }
}
