package cn.iocoder.yudao.module.system.api.merchant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.system.service.merchant.MerchantUserService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.enums.UserTypeEnum.MERCHANT;

@RestController
@Validated
public class SystemMerchantUserApiImpl implements SystemMerchantUserApi {

    @Resource
    private MerchantUserService merchantUserService;
    @Resource
    private OAuth2TokenService oauth2TokenService;

    @Override
    @TenantIgnore
    public CommonResult<Boolean> bindMerchantId(Long userId, Long merchantId) {
        merchantUserService.bindMerchantId(userId, merchantId);
        oauth2TokenService.refreshAccessTokenUserInfo(userId, MERCHANT.getValue());
        return success(true);
    }

    @Override
    @TenantIgnore
    public CommonResult<Boolean> canBindMerchant(Long userId, Long merchantId) {
        var user = merchantUserService.getUser(userId);
        return success(user != null && cn.iocoder.yudao.framework.common.enums.CommonStatusEnum.isEnable(user.getStatus())
                && merchantId != null && (user.getMerchantId() == null || merchantId.equals(user.getMerchantId())));
    }

    @Override
    @TenantIgnore
    public CommonResult<Boolean> validateMerchantBinding(Long userId, Long merchantId) {
        return success(merchantUserService.isMerchantBindingValid(userId, merchantId));
    }

}
