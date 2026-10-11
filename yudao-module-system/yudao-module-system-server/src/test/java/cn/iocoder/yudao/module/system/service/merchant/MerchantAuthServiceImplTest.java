package cn.iocoder.yudao.module.system.service.merchant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthLoginReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.MerchantUserDO;
import cn.iocoder.yudao.module.system.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.system.service.logger.LoginLogService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MerchantAuthServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private MerchantAuthServiceImpl merchantAuthService;

    @Mock
    private MerchantUserService merchantUserService;
    @Mock
    private LoginLogService loginLogService;
    @Mock
    private OAuth2TokenService oauth2TokenService;

    @Test
    void loginSuccessReturnsMerchantIdAndMerchantToken() {
        MerchantAuthLoginReqVO reqVO = new MerchantAuthLoginReqVO();
        reqVO.setUsername("seller100");
        reqVO.setPassword("secret123");
        MerchantUserDO user = new MerchantUserDO().setId(10L).setUsername("seller100")
                .setPassword("encoded-secret").setNickname("Tea Seller")
                .setMerchantId(20L).setStatus(CommonStatusEnum.ENABLE.getStatus());
        when(merchantUserService.getUserByUsername("seller100")).thenReturn(user);
        when(merchantUserService.isPasswordMatch("secret123", "encoded-secret")).thenReturn(true);
        when(merchantUserService.getUser(10L)).thenReturn(user);
        OAuth2AccessTokenDO token = new OAuth2AccessTokenDO().setUserId(10L)
                .setUserType(UserTypeEnum.MERCHANT.getValue()).setAccessToken("access")
                .setRefreshToken("refresh");
        when(oauth2TokenService.createAccessToken(10L, UserTypeEnum.MERCHANT.getValue(), "default", null))
                .thenReturn(token);

        var response = merchantAuthService.login(reqVO);

        assertEquals(10L, response.getUserId());
        assertEquals(20L, response.getMerchantId());
        assertEquals("access", response.getAccessToken());
        verify(oauth2TokenService).createAccessToken(10L, UserTypeEnum.MERCHANT.getValue(), "default", null);
    }

    @Test
    void loginDisabledAccountDoesNotCreateToken() {
        MerchantAuthLoginReqVO reqVO = new MerchantAuthLoginReqVO();
        reqVO.setUsername("seller100");
        reqVO.setPassword("secret123");
        MerchantUserDO user = new MerchantUserDO().setId(10L).setUsername("seller100")
                .setPassword("encoded-secret").setStatus(CommonStatusEnum.DISABLE.getStatus());
        when(merchantUserService.getUserByUsername("seller100")).thenReturn(user);
        when(merchantUserService.isPasswordMatch("secret123", "encoded-secret")).thenReturn(true);

        assertServiceException(() -> merchantAuthService.login(reqVO), ErrorCodeConstants.AUTH_LOGIN_USER_DISABLED);

        verify(oauth2TokenService, never()).createAccessToken(anyLong(), anyInt(), anyString(), any());
    }

}
