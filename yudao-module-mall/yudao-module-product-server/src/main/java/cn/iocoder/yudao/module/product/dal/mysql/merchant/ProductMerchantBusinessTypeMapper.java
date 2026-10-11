package cn.iocoder.yudao.module.product.dal.mysql.merchant;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantBusinessTypeDO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantBusinessTypePageReqVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductMerchantBusinessTypeMapper extends BaseMapperX<ProductMerchantBusinessTypeDO> {

    default ProductMerchantBusinessTypeDO selectByCode(String code) {
        return selectOne(ProductMerchantBusinessTypeDO::getCode, code);
    }

    default List<ProductMerchantBusinessTypeDO> selectListByEnabled(Boolean enabled) {
        return selectList(new LambdaQueryWrapperX<ProductMerchantBusinessTypeDO>()
                .eqIfPresent(ProductMerchantBusinessTypeDO::getEnabled, enabled)
                .orderByAsc(ProductMerchantBusinessTypeDO::getSort)
                .orderByAsc(ProductMerchantBusinessTypeDO::getId));
    }

    default PageResult<ProductMerchantBusinessTypeDO> selectPage(ProductMerchantBusinessTypePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ProductMerchantBusinessTypeDO>()
                .likeIfPresent(ProductMerchantBusinessTypeDO::getCode, reqVO.getCode())
                .likeIfPresent(ProductMerchantBusinessTypeDO::getName, reqVO.getName())
                .eqIfPresent(ProductMerchantBusinessTypeDO::getEnabled, reqVO.getEnabled())
                .orderByAsc(ProductMerchantBusinessTypeDO::getSort)
                .orderByAsc(ProductMerchantBusinessTypeDO::getId));
    }
}
