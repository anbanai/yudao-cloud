package cn.iocoder.yudao.module.product.dal.mysql.merchant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import org.apache.ibatis.annotations.Mapper;

/** 支付入网使用既有商户领域，审核与补料通过商户行锁串行。 */
@Mapper
public interface ProductMerchantPayApprovedMerchantMapper extends BaseMapperX<ProductMerchantDO> {

    default ProductMerchantDO selectByIdForUpdate(Long id) {
        return selectOne(new LambdaQueryWrapperX<ProductMerchantDO>()
                .eq(ProductMerchantDO::getId, id).last("FOR UPDATE"));
    }
}
