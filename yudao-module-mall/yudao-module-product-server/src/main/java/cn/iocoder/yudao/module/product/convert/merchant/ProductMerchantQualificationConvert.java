package cn.iocoder.yudao.module.product.convert.merchant;

import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantQualificationRespVO;
import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantQualificationSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ProductMerchantQualificationConvert {
    ProductMerchantQualificationConvert INSTANCE = Mappers.getMapper(ProductMerchantQualificationConvert.class);
    ProductMerchantQualificationDO convert(ProductMerchantQualificationSaveReqVO bean);
    ProductMerchantQualificationRespVO convert(ProductMerchantQualificationDO bean);
    List<ProductMerchantQualificationRespVO> convertList(List<ProductMerchantQualificationDO> list);
}
