package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.module.product.controller.admin.merchant.vo.ProductMerchantQualificationSaveReqVO;
import cn.iocoder.yudao.module.product.convert.merchant.ProductMerchantQualificationConvert;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantQualificationMapper;
import cn.iocoder.yudao.module.product.enums.merchant.ProductMerchantQualificationStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.MERCHANT_NOT_EXISTS;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.MERCHANT_QUALIFICATION_NOT_EXISTS;

@Service
@Validated
public class ProductMerchantQualificationServiceImpl implements ProductMerchantQualificationService {

    @Resource
    private ProductMerchantQualificationMapper qualificationMapper;
    @Resource
    private ProductMerchantService merchantService;

    @Override
    public Long createQualification(ProductMerchantQualificationSaveReqVO reqVO) {
        if (merchantService.getMerchant(reqVO.getMerchantId()) == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        ProductMerchantQualificationDO qualification = ProductMerchantQualificationConvert.INSTANCE.convert(reqVO);
        qualification.setStatus(ProductMerchantQualificationStatusEnum.PENDING.getStatus());
        qualificationMapper.insert(qualification);
        return qualification.getId();
    }

    @Override
    public void updateQualification(ProductMerchantQualificationSaveReqVO reqVO) {
        validateExists(reqVO.getId());
        ProductMerchantQualificationDO qualification = ProductMerchantQualificationConvert.INSTANCE.convert(reqVO);
        qualification.setStatus(ProductMerchantQualificationStatusEnum.PENDING.getStatus());
        qualificationMapper.updateById(qualification);
    }

    @Override
    public void deleteQualification(Long id) {
        validateExists(id);
        qualificationMapper.deleteById(id);
    }

    @Override
    public List<ProductMerchantQualificationDO> getList(Long merchantId) {
        return qualificationMapper.selectListByMerchantId(merchantId);
    }

    @Override
    public void approveQualification(Long id, Long reviewerId) {
        validateExists(id);
        qualificationMapper.updateById(new ProductMerchantQualificationDO().setId(id)
                .setStatus(ProductMerchantQualificationStatusEnum.APPROVED.getStatus()).setReviewerId(reviewerId));
    }

    @Override
    public void rejectQualification(Long id, String reason, Long reviewerId) {
        validateExists(id);
        qualificationMapper.updateById(new ProductMerchantQualificationDO().setId(id)
                .setStatus(ProductMerchantQualificationStatusEnum.REJECTED.getStatus())
                .setReviewerId(reviewerId).setReviewReason(reason));
    }

    private ProductMerchantQualificationDO validateExists(Long id) {
        ProductMerchantQualificationDO qualification = qualificationMapper.selectById(id);
        if (qualification == null) {
            throw exception(MERCHANT_QUALIFICATION_NOT_EXISTS);
        }
        return qualification;
    }
}
