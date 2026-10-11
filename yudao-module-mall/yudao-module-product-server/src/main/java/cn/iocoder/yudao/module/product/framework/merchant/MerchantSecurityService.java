package cn.iocoder.yudao.module.product.framework.merchant;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantMapper;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import cn.iocoder.yudao.module.system.api.merchant.SystemMerchantUserApi;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.product.enums.ErrorCodeConstants.MERCHANT_NOT_EXISTS;

@Service
public class MerchantSecurityService {

    @Resource
    private ProductMerchantMapper merchantMapper;
    @Resource
    private ProductMerchantService merchantService;
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

    public ProductMerchantDO getOwnedMerchantOrNull() {
        Long accountId = requireMerchantAccountId();
        ProductMerchantDO merchant = merchantMapper.selectByOwnerAccountId(accountId);
        if (merchant != null) {
            var result = systemMerchantUserApi.validateMerchantBinding(accountId, merchant.getId());
            if (result == null || !result.isSuccess() || !Boolean.TRUE.equals(result.getData())) {
                throw new AccessDeniedException("商家账号已禁用或主体绑定无效");
            }
        }
        return merchant;
    }

    /**
     * 获得当前账号绑定的商家主体，不要求主体当前处于可经营状态。
     * 店铺展示资料和入驻申请查询属于账号管理操作，不能与商品、订单等经营操作共用状态门禁。
     */
    public ProductMerchantDO requireBoundMerchant() {
        ProductMerchantDO merchant = getOwnedMerchantOrNull();
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        return merchant;
    }

    public ProductMerchantDO requireOwnedMerchant(Long merchantId) {
        ProductMerchantDO merchant = getOwnedMerchantOrNull();
        if (merchant == null || !merchant.getId().equals(merchantId)) {
            throw new AccessDeniedException("无权访问该商家");
        }
        return merchant;
    }

    public ProductMerchantDO requireOperatingMerchant() {
        ProductMerchantDO merchant = getOwnedMerchantOrNull();
        if (merchant == null) {
            throw exception(MERCHANT_NOT_EXISTS);
        }
        merchantService.validateMerchantAvailable(merchant.getId());
        return merchant;
    }

    public ProductMerchantDO requireOperatingMerchant(Long merchantId) {
        ProductMerchantDO merchant = requireOwnedMerchant(merchantId);
        merchantService.validateMerchantAvailable(merchant.getId());
        return merchant;
    }

}
