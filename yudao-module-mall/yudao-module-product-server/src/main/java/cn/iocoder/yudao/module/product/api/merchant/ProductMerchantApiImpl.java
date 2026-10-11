package cn.iocoder.yudao.module.product.api.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@Validated
public class ProductMerchantApiImpl implements ProductMerchantApi {

    @Resource
    private ProductMerchantService merchantService;

    @Override
    public CommonResult<Boolean> validateMerchantAvailable(Long id) {
        merchantService.validateMerchantAvailable(id);
        return success(true);
    }

}
