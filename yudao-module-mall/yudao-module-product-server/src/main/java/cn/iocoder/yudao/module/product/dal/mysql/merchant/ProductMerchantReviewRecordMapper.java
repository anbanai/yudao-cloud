package cn.iocoder.yudao.module.product.dal.mysql.merchant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantReviewRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMerchantReviewRecordMapper extends BaseMapperX<ProductMerchantReviewRecordDO> {

    default List<ProductMerchantReviewRecordDO> selectListByMerchantId(Long merchantId) {
        return selectList(new LambdaQueryWrapperX<ProductMerchantReviewRecordDO>()
                .eq(ProductMerchantReviewRecordDO::getMerchantId, merchantId)
                .orderByDesc(ProductMerchantReviewRecordDO::getReviewTime)
                .orderByDesc(ProductMerchantReviewRecordDO::getId));
    }
}
