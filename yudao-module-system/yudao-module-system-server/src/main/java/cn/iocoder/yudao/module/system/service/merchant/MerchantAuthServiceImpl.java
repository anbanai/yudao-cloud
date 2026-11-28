package cn.iocoder.yudao.module.system.service.merchant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.system.api.logger.dto.LoginLogCreateReqDTO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthLoginRespVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthRegisterReqVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantUserProfileRespVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantUserProfileUpdateReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.MerchantUserDO;
import cn.iocoder.yudao.module.system.enums.logger.LoginLogTypeEnum;
import cn.iocoder.yudao.module.system.enums.logger.LoginResultEnum;
import cn.iocoder.yudao.module.system.enums.oauth2.OAuth2ClientConstants;
import cn.iocoder.yudao.module.system.service.logger.LoginLogService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;

@Service
public class MerchantAuthServiceImpl implements MerchantAuthService {

    @Value("${yudao.tenant.enable:true}")
    private boolean tenantEnabled = true;

    @Resource
    private MerchantUserService merchantUserService;
    @Resource
    private TenantService tenantService;
    @Resource
    private LoginLogService loginLogService;
    @Resource
    private OAuth2TokenService oauth2TokenService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantAuthLoginRespVO register(MerchantAuthRegisterReqVO reqVO) {
        MerchantUserDO user = merchantUserService.registerUser(reqVO);
        return createTokenAfterLoginSuccess(merchantUserService.getUser(user.getId()), reqVO.getUsername());
    }

    @Override
    public MerchantAuthLoginRespVO login(MerchantAuthLoginReqVO reqVO) {
        MerchantUserDO user = merchantUserService.getUserByUsername(reqVO.getUsername());
        if (user == null || !merchantUserService.isPasswordMatch(reqVO.getPassword(), user.getPassword())) {
            createLoginLog(user, reqVO.getUsername(), LoginResultEnum.BAD_CREDENTIALS);
            throw exception(AUTH_LOGIN_BAD_CREDENTIALS);
        }
        if (CommonStatusEnum.isDisable(user.getStatus())) {
            createLoginLog(user, reqVO.getUsername(), LoginResultEnum.USER_DISABLED);
            throw exception(AUTH_LOGIN_USER_DISABLED);
        }
        return createTokenAfterLoginSuccess(user, reqVO.getUsername());
    }

    @Override
    public MerchantAuthLoginRespVO refreshToken(String refreshToken) {
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.refreshAccessToken(refreshToken,
                OAuth2ClientConstants.CLIENT_ID_DEFAULT, UserTypeEnum.MERCHANT.getValue());
        MerchantUserDO user = merchantUserService.getUser(accessTokenDO.getUserId());
        if (tenantEnabled && user != null && !Objects.equals(
                assignedTenant(user.getTenantId()), assignedTenant(accessTokenDO.getTenantId()))) {
            oauth2TokenService.removeAccessToken(accessTokenDO.getAccessToken());
            throw exception(GlobalErrorCodeConstants.UNAUTHORIZED);
        }
        return toLoginResp(accessTokenDO, user);
    }

    @Override
    public void logout(String accessToken) {
        OAuth2AccessTokenDO token = oauth2TokenService.getAccessToken(accessToken);
        if (token == null || !UserTypeEnum.MERCHANT.getValue().equals(token.getUserType())) {
            return;
        }
        token = oauth2TokenService.removeAccessToken(accessToken);
        if (token != null) {
            createLoginLog(merchantUserService.getUser(token.getUserId()), null, LoginResultEnum.SUCCESS,
                    LoginLogTypeEnum.LOGOUT_SELF.getType());
        }
    }

    @Override
    public MerchantUserProfileRespVO getProfile(Long userId, String website) {
        MerchantUserDO user = merchantUserService.getUser(userId);
        if (user == null) {
            throw exception(cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.USER_NOT_EXISTS);
        }
        MerchantUserProfileRespVO profile = BeanUtils.toBean(user, MerchantUserProfileRespVO.class);
        if (tenantEnabled && assignedTenant(user.getTenantId()) == null) {
            if (user.getMerchantId() != null) {
                // 已关联的历史主体须由平台核对归属，不能重新选择。
                profile.setOnboardingPlatforms(List.of());
                return profile;
            }
            profile.setOnboardingPlatforms(tenantService.getTenantListByStatus(CommonStatusEnum.ENABLE.getStatus()).stream()
                    .filter(tenant -> tenant.getExpireTime() != null && !DateUtils.isExpired(tenant.getExpireTime()))
                    .map(tenant -> new MerchantUserProfileRespVO.Platform(tenant.getId(), tenant.getName(),
                            website != null && tenant.getWebsites() != null
                                    && tenant.getWebsites().stream().anyMatch(website::equalsIgnoreCase)))
                    .toList());
        }
        return profile;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantAuthLoginRespVO selectPlatform(Long userId, Long tenantId) {
        if (!tenantEnabled) {
            throw exception(GlobalErrorCodeConstants.BAD_REQUEST);
        }
        MerchantUserDO user = merchantUserService.selectOnboardingPlatform(userId, tenantId);
        oauth2TokenService.removeAccessToken(userId, UserTypeEnum.MERCHANT.getValue());
        return createTokenAfterLoginSuccess(user, user.getUsername());
    }

    @Override
    public void updateProfile(Long userId, MerchantUserProfileUpdateReqVO reqVO) {
        merchantUserService.updateUserProfile(userId, reqVO);
    }

    private MerchantAuthLoginRespVO createTokenAfterLoginSuccess(MerchantUserDO user, String username) {
        if (!tenantEnabled) {
            return issueLoginToken(user, username);
        }
        // 商家登录入口忽略租户，签发令牌时按账号归属写入访问令牌与刷新令牌。
        Long previousTenant = TenantContextHolder.getTenantId();
        boolean previousIgnore = TenantContextHolder.isIgnore();
        try {
            Long tenantId = assignedTenant(user.getTenantId());
            TenantContextHolder.setTenantId(tenantId);
            TenantContextHolder.setIgnore(tenantId == null);
            return issueLoginToken(user, username);
        } finally {
            TenantContextHolder.setTenantId(previousTenant);
            TenantContextHolder.setIgnore(previousIgnore);
        }
    }

    private static Long assignedTenant(Long tenantId) {
        return tenantId != null && tenantId > 0 ? tenantId : null;
    }

    private MerchantAuthLoginRespVO issueLoginToken(MerchantUserDO user, String username) {
        createLoginLog(user, username, LoginResultEnum.SUCCESS);
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.createAccessToken(user.getId(),
                UserTypeEnum.MERCHANT.getValue(), OAuth2ClientConstants.CLIENT_ID_DEFAULT, null);
        return toLoginResp(accessTokenDO, user);
    }

    private MerchantAuthLoginRespVO toLoginResp(OAuth2AccessTokenDO accessTokenDO, MerchantUserDO user) {
        MerchantAuthLoginRespVO respVO = BeanUtils.toBean(accessTokenDO, MerchantAuthLoginRespVO.class);
        respVO.setMerchantId(user != null ? user.getMerchantId() : null);
        return respVO;
    }

    private void createLoginLog(MerchantUserDO user, String username, LoginResultEnum result) {
        createLoginLog(user, username, result, LoginLogTypeEnum.LOGIN_USERNAME.getType());
        if (user != null && result == LoginResultEnum.SUCCESS) {
            merchantUserService.updateUserLogin(user.getId(), ServletUtils.getClientIP());
        }
    }

    private void createLoginLog(MerchantUserDO user, String username, LoginResultEnum result, Integer logType) {
        LoginLogCreateReqDTO reqDTO = new LoginLogCreateReqDTO();
        reqDTO.setLogType(logType);
        reqDTO.setTraceId(TracerUtils.getTraceId());
        reqDTO.setUserId(user != null ? user.getId() : null);
        reqDTO.setUserType(UserTypeEnum.MERCHANT.getValue());
        reqDTO.setUsername(username != null ? username : (user != null ? user.getUsername() : null));
        reqDTO.setUserAgent(ServletUtils.getUserAgent());
        reqDTO.setUserIp(ServletUtils.getClientIP());
        reqDTO.setResult(result.getResult());
        loginLogService.createLoginLog(reqDTO);
    }

}
