package cn.iocoder.yudao.module.trade.framework.merchant;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.product.api.merchant.ProductMerchantApi;
import cn.iocoder.yudao.module.system.api.merchant.SystemMerchantUserApi;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Service
public class MerchantSecurityService {

    @Resource
    private ProductMerchantApi productMerchantApi;
    @Resource
    private SystemMerchantUserApi systemMerchantUserApi;

    public Long requireMerchantAccountId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        if (loginUser == null || !UserTypeEnum.MERCHANT.getValue().equals(loginUser.getUserType())
                || loginUser.getId() == null) {
            throw new AccessDeniedException("商家身份无效");
        }
        return loginUser.getId();
    }

    public Long requireOperatingMerchantId() {
        LoginUser loginUser = SecurityFrameworkUtils.getLoginUser();
        requireMerchantAccountId();
        String merchantIdValue = loginUser.getInfo() == null ? null : loginUser.getInfo().get("merchantId");
        if (merchantIdValue == null) {
            throw new AccessDeniedException("商家账号尚未绑定经营主体");
        }
        Long merchantId;
        try {
            merchantId = Long.valueOf(merchantIdValue);
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("商家身份无效");
        }
        CommonResult<Boolean> bindingResult = systemMerchantUserApi.validateMerchantBinding(loginUser.getId(), merchantId);
        if (bindingResult == null
                || !bindingResult.isSuccess()
                || !Boolean.TRUE.equals(bindingResult.getData())) {
            throw new AccessDeniedException("商家账号与经营主体不匹配");
        }
        CommonResult<Boolean> availableResult = productMerchantApi.validateMerchantAvailable(merchantId);
        if (availableResult == null
                || !availableResult.isSuccess()
                || !Boolean.TRUE.equals(availableResult.getData())) {
            throw new AccessDeniedException("商家当前不可经营");
        }
        return merchantId;
    }

}
