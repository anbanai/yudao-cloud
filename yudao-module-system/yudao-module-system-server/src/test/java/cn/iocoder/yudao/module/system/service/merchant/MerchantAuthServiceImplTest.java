package cn.iocoder.yudao.module.system.service.merchant;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthLoginReqVO;
import cn.iocoder.yudao.module.system.controller.merchant.auth.vo.MerchantAuthRegisterReqVO;
import cn.iocoder.yudao.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO;
import cn.iocoder.yudao.module.system.dal.dataobject.user.MerchantUserDO;
import cn.iocoder.yudao.module.system.enums.ErrorCodeConstants;
import cn.iocoder.yudao.module.system.service.logger.LoginLogService;
import cn.iocoder.yudao.module.system.service.oauth2.OAuth2TokenService;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static org.junit.jupiter.api.Assertions.*;
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

    @Mock
    private TenantService tenantService;

    @Test
    void profileRecommendsCurrentWebsiteAmongActivePlatforms() {
        when(merchantUserService.getUser(10L)).thenReturn(new MerchantUserDO().setId(10L));
        LocalDateTime now = LocalDateTime.now();
        when(tenantService.getTenantListByStatus(CommonStatusEnum.ENABLE.getStatus())).thenReturn(List.of(
                TenantDO.builder().id(4L).name("旧平台").expireTime(now.minusDays(1)).build(),
                TenantDO.builder().id(5L).name("其他平台").expireTime(now.plusDays(1)).build(),
                TenantDO.builder().id(7L).name("好茶引力").expireTime(now.plusDays(1))
                        .websites(List.of("tea.example")).build()));
        var platforms = merchantAuthService.getProfile(10L, "TEA.example").getOnboardingPlatforms();
        assertEquals(List.of(5L, 7L), platforms.stream().map(platform -> platform.id()).toList());
        assertFalse(platforms.get(0).recommended());
        assertTrue(platforms.get(1).recommended());
    }

    @Test
    void selectionReplacesUnassignedSessionWithChosenTenantToken() {
        var user = new MerchantUserDO().setId(10L).setUsername("seller");
        user.setTenantId(7L);
        when(merchantUserService.selectOnboardingPlatform(10L, 7L)).thenReturn(user);
        when(oauth2TokenService.createAccessToken(10L, UserTypeEnum.MERCHANT.getValue(), "default", null))
                .thenAnswer(invocation -> {
                    assertEquals(7L, TenantContextHolder.getTenantId());
                    assertFalse(TenantContextHolder.isIgnore());
                    return new OAuth2AccessTokenDO().setAccessToken("scoped").setRefreshToken("refresh");
                });
        var tokens = merchantAuthService.selectPlatform(10L, 7L);
        assertEquals("scoped", tokens.getAccessToken());
        assertEquals("refresh", tokens.getRefreshToken());
        verify(oauth2TokenService).removeAccessToken(10L, UserTypeEnum.MERCHANT.getValue());
    }

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void loginSuccessReturnsMerchantIdAndMerchantToken() {
        MerchantAuthLoginReqVO reqVO = new MerchantAuthLoginReqVO();
        reqVO.setUsername("seller100");
        reqVO.setPassword("secret123");
        MerchantUserDO user = new MerchantUserDO().setId(10L).setUsername("seller100")
                .setPassword("encoded-secret").setNickname("Tea Seller")
                .setMerchantId(20L).setStatus(CommonStatusEnum.ENABLE.getStatus());
        user.setTenantId(32L);
        TenantContextHolder.setTenantId(73L);
        TenantContextHolder.setIgnore(true);
        when(merchantUserService.getUserByUsername("seller100")).thenReturn(user);
        when(merchantUserService.isPasswordMatch("secret123", "encoded-secret")).thenReturn(true);
        OAuth2AccessTokenDO token = new OAuth2AccessTokenDO().setUserId(10L)
                .setUserType(UserTypeEnum.MERCHANT.getValue()).setAccessToken("access")
                .setRefreshToken("refresh");
        when(oauth2TokenService.createAccessToken(10L, UserTypeEnum.MERCHANT.getValue(), "default", null))
                .thenAnswer(invocation -> {
                    assertEquals(32L, TenantContextHolder.getTenantId());
                    assertFalse(TenantContextHolder.isIgnore());
                    return token;
                });

        var response = merchantAuthService.login(reqVO);

        assertEquals(73L, TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
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

    @Test
    void registerUsesPersistedAccountTenantAndRestoresHeaderlessContext() {
        MerchantAuthRegisterReqVO reqVO = new MerchantAuthRegisterReqVO();
        reqVO.setUsername("seller");
        MerchantUserDO stored = new MerchantUserDO().setId(10L);
        stored.setTenantId(32L);
        TenantContextHolder.setIgnore(true);
        when(merchantUserService.registerUser(reqVO)).thenReturn(new MerchantUserDO().setId(10L));
        when(merchantUserService.getUser(10L)).thenReturn(stored);
        when(oauth2TokenService.createAccessToken(10L, UserTypeEnum.MERCHANT.getValue(), "default", null))
                .thenAnswer(invocation -> {
                    assertEquals(32L, TenantContextHolder.getTenantId());
                    assertFalse(TenantContextHolder.isIgnore());
                    return new OAuth2AccessTokenDO().setUserId(10L);
                });

        assertEquals(10L, merchantAuthService.register(reqVO).getUserId());
        assertNull(TenantContextHolder.getTenantId());
        assertTrue(TenantContextHolder.isIgnore());
    }

    @Test
    void registerWithoutAssignedTenantDoesNotAdoptRequestTenant() {
        MerchantAuthRegisterReqVO reqVO = new MerchantAuthRegisterReqVO();
        MerchantUserDO stored = new MerchantUserDO().setId(10L);
        stored.setTenantId(0L);
        TenantContextHolder.setTenantId(73L);
        TenantContextHolder.setIgnore(true);
        when(merchantUserService.registerUser(reqVO)).thenReturn(stored);
        when(merchantUserService.getUser(10L)).thenReturn(stored);
        when(oauth2TokenService.createAccessToken(10L, UserTypeEnum.MERCHANT.getValue(), "default", null))
                .thenAnswer(invocation -> {
                    assertNull(TenantContextHolder.getTenantId());
                    assertTrue(TenantContextHolder.isIgnore());
                    return new OAuth2AccessTokenDO().setUserId(10L);
                });

        assertEquals(10L, merchantAuthService.register(reqVO).getUserId());
        assertEquals(73L, TenantContextHolder.getTenantId());
    }

    @Test
    void refreshUsesPreviouslyAssignedTenant() {
        stubRefresh(32L, 32L);
        assertEquals("newAccess", merchantAuthService.refreshToken("refresh").getAccessToken());
        verify(oauth2TokenService, never()).removeAccessToken(anyString());
    }

    @Test
    void refreshRejectsAndRevokesTokenIssuedUnderAnotherTenant() {
        stubRefresh(32L, 0L);
        assertServiceException(() -> merchantAuthService.refreshToken("refresh"), GlobalErrorCodeConstants.UNAUTHORIZED);
        verify(oauth2TokenService).removeAccessToken("newAccess");
    }

    @Test
    void refreshAllowsExistingTenantDisabledDeployment() {
        ReflectionTestUtils.setField(merchantAuthService, "tenantEnabled", false);
        stubRefresh(32L, 0L);
        assertEquals("newAccess", merchantAuthService.refreshToken("refresh").getAccessToken());
    }

    private void stubRefresh(Long accountTenant, Long tokenTenant) {
        MerchantUserDO user = new MerchantUserDO().setId(10L);
        user.setTenantId(accountTenant);
        OAuth2AccessTokenDO token = new OAuth2AccessTokenDO().setUserId(10L).setAccessToken("newAccess");
        token.setTenantId(tokenTenant);
        when(oauth2TokenService.refreshAccessToken("refresh", "default", UserTypeEnum.MERCHANT.getValue()))
                .thenReturn(token);
        when(merchantUserService.getUser(10L)).thenReturn(user);
    }

}
