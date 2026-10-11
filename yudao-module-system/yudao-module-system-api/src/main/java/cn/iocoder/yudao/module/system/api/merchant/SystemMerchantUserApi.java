package cn.iocoder.yudao.module.system.api.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import static cn.iocoder.yudao.module.system.api.merchant.SystemMerchantUserApi.PREFIX;

@FeignClient(name = ApiConstants.NAME)
public interface SystemMerchantUserApi {

    String PREFIX = ApiConstants.PREFIX + "/merchant-user";

    @PostMapping(PREFIX + "/bind-merchant-id")
    @Operation(summary = "将商家编号绑定到商家账号")
    @Parameter(name = "userId", description = "系统商家账号编号", required = true)
    @Parameter(name = "merchantId", description = "商品服务商家编号", required = true)
    CommonResult<Boolean> bindMerchantId(@RequestParam("userId") Long userId,
                                         @RequestParam("merchantId") Long merchantId);

    @PostMapping(PREFIX + "/can-bind-merchant")
    @Operation(summary = "校验商家账号可关联指定主体")
    CommonResult<Boolean> canBindMerchant(@RequestParam("userId") Long userId,
                                          @RequestParam("merchantId") Long merchantId);

    @PostMapping(PREFIX + "/validate-merchant-binding")
    @Operation(summary = "校验商家账号与商家主体绑定关系")
    CommonResult<Boolean> validateMerchantBinding(@RequestParam("userId") Long userId,
                                                  @RequestParam("merchantId") Long merchantId);

}
