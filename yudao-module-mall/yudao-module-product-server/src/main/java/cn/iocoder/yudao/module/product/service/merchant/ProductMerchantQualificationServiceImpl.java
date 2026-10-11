package cn.iocoder.yudao.module.product.service.merchant;

import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantQualificationMapper;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantMapper;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Service
@Validated
public class ProductMerchantQualificationServiceImpl implements ProductMerchantQualificationService {

    @Resource
    private ProductMerchantQualificationMapper qualificationMapper;
    @Resource
    private ProductMerchantMapper merchantMapper;
    @Override
    public List<ProductMerchantQualificationDO> getList(Long merchantId) {
        return qualificationMapper.selectListByMerchantId(merchantId);
    }
}
