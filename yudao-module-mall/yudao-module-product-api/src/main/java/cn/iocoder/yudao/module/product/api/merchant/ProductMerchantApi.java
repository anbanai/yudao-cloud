package cn.iocoder.yudao.module.product.api.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = ApiConstants.NAME)
public interface ProductMerchantApi {

    String PREFIX = ApiConstants.PREFIX + "/merchant";

    @GetMapping(PREFIX + "/validate-available")
    @Parameter(name = "id", description = "商家编号", required = true)
    CommonResult<Boolean> validateMerchantAvailable(@RequestParam("id") Long id);

}
