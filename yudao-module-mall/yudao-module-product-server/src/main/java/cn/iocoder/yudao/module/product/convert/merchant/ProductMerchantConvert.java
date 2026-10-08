package cn.iocoder.yudao.module.product.convert.merchant;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
public interface ProductMerchantConvert {
    ProductMerchantConvert INSTANCE = Mappers.getMapper(ProductMerchantConvert.class);
    ProductMerchantDO convert(ProductMerchantSaveReqVO bean);
    ProductMerchantRespVO convert(ProductMerchantDO bean);
    PageResult<ProductMerchantRespVO> convertPage(PageResult<ProductMerchantDO> page);
}
