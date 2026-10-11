package cn.iocoder.yudao.module.system.service.merchant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.util.monitor.TracerUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.common.util.servlet.ServletUtils;
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
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_BAD_CREDENTIALS;
import static cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED;

@Service
public class MerchantAuthServiceImpl implements MerchantAuthService {

    @Resource
    private MerchantUserService merchantUserService;
    @Resource
    private LoginLogService loginLogService;
    @Resource
    private OAuth2TokenService oauth2TokenService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantAuthLoginRespVO register(MerchantAuthRegisterReqVO reqVO) {
        MerchantUserDO user = merchantUserService.registerUser(reqVO);
        return createTokenAfterLoginSuccess(user, reqVO.getUsername());
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
    public MerchantUserProfileRespVO getProfile(Long userId) {
        MerchantUserDO user = merchantUserService.getUser(userId);
        if (user == null) {
            throw exception(cn.iocoder.yudao.module.system.enums.ErrorCodeConstants.USER_NOT_EXISTS);
        }
        return BeanUtils.toBean(user, MerchantUserProfileRespVO.class);
    }

    @Override
    public void updateProfile(Long userId, MerchantUserProfileUpdateReqVO reqVO) {
        merchantUserService.updateUserProfile(userId, reqVO);
    }

    private MerchantAuthLoginRespVO createTokenAfterLoginSuccess(MerchantUserDO user, String username) {
        createLoginLog(user, username, LoginResultEnum.SUCCESS);
        OAuth2AccessTokenDO accessTokenDO = oauth2TokenService.createAccessToken(user.getId(),
                UserTypeEnum.MERCHANT.getValue(), OAuth2ClientConstants.CLIENT_ID_DEFAULT, null);
        user = merchantUserService.getUser(user.getId());
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
