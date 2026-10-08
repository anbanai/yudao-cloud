package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantQualificationSaveReqVO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationDO;
import jakarta.validation.Valid;

import java.util.List;

public interface ProductMerchantQualificationService {
    Long createQualification(@Valid ProductMerchantQualificationSaveReqVO reqVO);
    void updateQualification(@Valid ProductMerchantQualificationSaveReqVO reqVO);
    void deleteQualification(Long id);
    List<ProductMerchantQualificationDO> getList(Long merchantId);
    void approveQualification(Long id, Long reviewerId);
    void rejectQualification(Long id, String reason, Long reviewerId);
}
