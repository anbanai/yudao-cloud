package cn.iocoder.yudao.module.system.service.merchant;

import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthRegisterReqVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantUserProfileRespVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantUserProfileUpdateReqVO;
import jakarta.validation.Valid;

public interface MerchantAuthService {

    MerchantAuthLoginRespVO register(@Valid MerchantAuthRegisterReqVO reqVO);

    MerchantAuthLoginRespVO login(@Valid MerchantAuthLoginReqVO reqVO);

    MerchantAuthLoginRespVO refreshToken(String refreshToken);

    void logout(String accessToken);

    MerchantUserProfileRespVO getProfile(Long userId);

    void updateProfile(Long userId, @Valid MerchantUserProfileUpdateReqVO reqVO);

}
