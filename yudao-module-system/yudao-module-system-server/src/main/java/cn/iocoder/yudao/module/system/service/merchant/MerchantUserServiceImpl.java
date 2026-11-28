package cn.iocoder.yudao.module.system.service.merchant;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.system.controller.admin.merchant.vo.MerchantUserPageReqVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthRegisterReqVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantUserProfileUpdateReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.MerchantUserDO;
import cn.iocoder.yudao.module.system.dal.mysql.user.MerchantUserMapper;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.*;

@Service
public class MerchantUserServiceImpl implements MerchantUserService {

    @Resource
    private MerchantUserMapper merchantUserMapper;
    @Resource
    private TenantService tenantService;
    @Resource
    private PasswordEncoder passwordEncoder;
    @Resource
    @Lazy
    private OAuth2TokenService oauth2TokenService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantUserDO registerUser(MerchantAuthRegisterReqVO reqVO) {
        validateUsernameUnique(reqVO.getUsername());
        validateMobileUnique(null, reqVO.getMobile());
        if (reqVO.getTenantId() != null) {
            tenantService.validTenant(reqVO.getTenantId());
        }
        MerchantUserDO user = new MerchantUserDO()
                .setUsername(reqVO.getUsername())
                .setNickname(reqVO.getNickname())
                .setMobile(reqVO.getMobile())
                .setPassword(passwordEncoder.encode(reqVO.getPassword()))
                .setStatus(CommonStatusEnum.ENABLE.getStatus());
        user.setTenantId(reqVO.getTenantId());
        merchantUserMapper.insert(user);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantUserDO selectOnboardingPlatform(Long userId, Long tenantId) {
        MerchantUserDO user = merchantUserMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        if (CommonStatusEnum.isDisable(user.getStatus())) {
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        if (user.getMerchantId() != null || user.getTenantId() != null && user.getTenantId() != 0L) {
            throw exception(SYSTEM_MERCHANT_PLATFORM_ALREADY_ASSIGNED);
        }
        tenantService.validTenant(tenantId);
        MerchantUserDO update = new MerchantUserDO().setId(userId);
        update.setTenantId(tenantId);
        merchantUserMapper.updateById(update);
        user.setTenantId(tenantId);
        return user;
    }

    @Override
    public MerchantUserDO getUser(Long id) {
        return merchantUserMapper.selectById(id);
    }

    @Override
    public MerchantUserDO getUserByUsername(String username) {
        return merchantUserMapper.selectByUsername(username);
    }

    @Override
    public PageResult<MerchantUserDO> getUserPage(MerchantUserPageReqVO reqVO) {
        return merchantUserMapper.selectPage(reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long id, Integer status) {
        validateMerchantUserExists(id);
        merchantUserMapper.updateById(new MerchantUserDO().setId(id).setStatus(status));
        if (CommonStatusEnum.isDisable(status)) {
            oauth2TokenService.removeAccessToken(id, UserTypeEnum.MERCHANT.getValue());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserPassword(Long id, String password) {
        validateMerchantUserExists(id);
        merchantUserMapper.updateById(new MerchantUserDO().setId(id)
                .setPassword(passwordEncoder.encode(password)));
        oauth2TokenService.removeAccessToken(id, UserTypeEnum.MERCHANT.getValue());
    }

    private MerchantUserDO validateMerchantUserExists(Long id) {
        MerchantUserDO user = merchantUserMapper.selectById(id);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        return user;
    }

    @Override
    public void updateUserLogin(Long id, String loginIp) {
        merchantUserMapper.updateById(new MerchantUserDO().setId(id)
                .setLoginIp(loginIp).setLoginDate(LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserProfile(Long id, MerchantUserProfileUpdateReqVO reqVO) {
        MerchantUserDO user = merchantUserMapper.selectById(id);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        validateEmailUnique(id, reqVO.getEmail());
        validateMobileUnique(id, reqVO.getMobile());
        merchantUserMapper.updateById(BeanUtils.toBean(reqVO, MerchantUserDO.class).setId(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindMerchantId(Long userId, Long merchantId) {
        MerchantUserDO user = merchantUserMapper.selectById(userId);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        if (user.getMerchantId() != null) {
            if (ObjUtil.notEqual(user.getMerchantId(), merchantId)) {
                throw exception(SYSTEM_MERCHANT_USER_ALREADY_BOUND);
            }
            return;
        }
        if (merchantUserMapper.bindMerchantIdIfUnbound(userId, merchantId) > 0) {
            return;
        }
        // Another request may have won the conditional update between the initial read and this check.
        user = merchantUserMapper.selectById(userId);
        if (user != null && ObjUtil.equal(user.getMerchantId(), merchantId)) {
            return;
        }
        throw exception(SYSTEM_MERCHANT_USER_ALREADY_BOUND);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindApprovedMerchant(Long userId, Long merchantId, Long merchantTenantId) {
        if (merchantTenantId == null || merchantTenantId <= 0 || merchantId == null) {
            throw exception(SYSTEM_MERCHANT_USER_ALREADY_BOUND);
        }
        tenantService.validTenant(merchantTenantId);
        MerchantUserDO user = merchantUserMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw exception(USER_NOT_EXISTS);
        }
        if (user.getMerchantId() != null && !merchantId.equals(user.getMerchantId())
                || !Long.valueOf(0L).equals(user.getTenantId()) && !merchantTenantId.equals(user.getTenantId())) {
            throw exception(SYSTEM_MERCHANT_USER_ALREADY_BOUND);
        }
        if (merchantId.equals(user.getMerchantId()) && merchantTenantId.equals(user.getTenantId())) {
            return;
        }
        MerchantUserDO update = new MerchantUserDO().setId(userId).setMerchantId(merchantId);
        update.setTenantId(merchantTenantId);
        merchantUserMapper.updateById(update);
        // 旧令牌缓存了绑定前的租户和主体信息，下次登录按已审核的归属签发。
        oauth2TokenService.removeAccessToken(userId, UserTypeEnum.MERCHANT.getValue());
    }

    @Override
    public boolean isMerchantBindingValid(Long userId, Long merchantId) {
        MerchantUserDO user = merchantUserMapper.selectById(userId);
        return user != null && CommonStatusEnum.isEnable(user.getStatus())
                && merchantId != null && merchantId.equals(user.getMerchantId());
    }

    @Override
    public boolean isPasswordMatch(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    private void validateEmailUnique(Long id, String email) {
        if (StrUtil.isBlank(email)) {
            return;
        }
        MerchantUserDO user = merchantUserMapper.selectByEmail(email);
        if (user != null && !user.getId().equals(id)) {
            throw exception(USER_EMAIL_EXISTS);
        }
    }

    private void validateUsernameUnique(String username) {
        if (merchantUserMapper.selectByUsername(username) != null) {
            throw exception(USER_USERNAME_EXISTS);
        }
    }

    private void validateMobileUnique(Long id, String mobile) {
        if (StrUtil.isBlank(mobile)) {
            return;
        }
        MerchantUserDO user = merchantUserMapper.selectByMobile(mobile);
        if (user != null && !user.getId().equals(id)) {
            throw exception(USER_MOBILE_EXISTS);
        }
    }

}
