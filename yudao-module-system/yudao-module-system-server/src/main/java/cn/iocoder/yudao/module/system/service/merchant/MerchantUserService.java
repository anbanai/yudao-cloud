package cn.iocoder.yudao.module.system.service.merchant;

import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthRegisterReqVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantUserProfileUpdateReqVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.system.controller.admin.merchant.vo.MerchantUserPageReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.MerchantUserDO;
import jakarta.validation.Valid;

public interface MerchantUserService {

    MerchantUserDO registerUser(@Valid MerchantAuthRegisterReqVO reqVO);

    MerchantUserDO getUser(Long id);

    MerchantUserDO getUserByUsername(String username);

    PageResult<MerchantUserDO> getUserPage(MerchantUserPageReqVO reqVO);

    void updateUserStatus(Long id, Integer status);

    void updateUserPassword(Long id, String password);

    void updateUserLogin(Long id, String loginIp);

    void updateUserProfile(Long id, @Valid MerchantUserProfileUpdateReqVO reqVO);

    void bindMerchantId(Long userId, Long merchantId);
    boolean isMerchantBindingValid(Long userId, Long merchantId);

    boolean isPasswordMatch(String rawPassword, String encodedPassword);

}
