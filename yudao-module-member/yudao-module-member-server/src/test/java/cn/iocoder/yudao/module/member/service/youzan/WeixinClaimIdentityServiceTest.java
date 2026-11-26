package cn.iocoder.yudao.module.member.service.youzan;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.config.SecurityProperties;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.auth.WeixinClaimSession;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeixinClaimIdentityServiceTest {
    private static final Integer MEMBER = UserTypeEnum.MEMBER.getValue();
    private final OAuth2TokenCommonApi tokens = mock(OAuth2TokenCommonApi.class);
    private final MemberUserService users = mock(MemberUserService.class);
    private final SocialUserApi bindings = mock(SocialUserApi.class);
    private final WeixinClaimSession sessions = mock(WeixinClaimSession.class);
    private final SocialClientApi weixin = mock(SocialClientApi.class);
    private final WeixinClaimIdentityService service = new WeixinClaimIdentityService(new SecurityProperties(),
            tokens, users, bindings, sessions, weixin);
    private final OAuth2AccessTokenCheckRespDTO live = new OAuth2AccessTokenCheckRespDTO().setUserType(MEMBER)
            .setUserId(3L).setTenantId(7L).setExpiresTime(LocalDateTime.now().plusMinutes(2));
    private final MemberUserDO member = MemberUserDO.builder().id(3L).status(CommonStatusEnum.ENABLE.getStatus()).build();
    private final SocialWxPhoneNumberInfoRespDTO phone = new SocialWxPhoneNumberInfoRespDTO()
            .setCountryCode("86").setPurePhoneNumber("13900000000");

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(7L);
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer sample-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityFrameworkUtils.setLoginUser(new LoginUser().setId(3L).setUserType(MEMBER).setTenantId(7L), request);
        when(tokens.checkAccessToken("sample-token")).thenReturn(success(live));
        member.setTenantId(7L);
        when(users.getUser(3L)).thenReturn(member);
        when(bindings.getSocialUserByUserId(MEMBER, 3L, SocialTypeEnum.WECHAT_MINI_PROGRAM.getType()))
                .thenReturn(success(new SocialUserRespDTO("synthetic-binding", null, null, 3L)));
        when(sessions.matches(7L, 3L, "sample-token", "synthetic-binding")).thenReturn(true);
        when(weixin.exchangeWxMaPhoneNumber(MEMBER, "one-time-code")).thenReturn(success(phone));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        TenantContextHolder.clear();
    }

    @Test
    void returnsOnlyProviderNumberWithoutMemberMutation() {
        assertEquals("13900000000", service.verifiedMobile("one-time-code"));
        verify(users).getUser(3L);
        verifyNoMoreInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"anonymous", "other-user", "other-type", "other-tenant", "expired", "revoked",
            "no-marker", "disabled", "foreign-member", "ignore-tenant", "blank-code"})
    void rejectsBeforeConsumingPhoneCode(String scenario) {
        switch (scenario) {
            case "anonymous" -> SecurityContextHolder.clearContext();
            case "other-user" -> live.setUserId(4L);
            case "other-type" -> live.setUserType(UserTypeEnum.ADMIN.getValue());
            case "other-tenant" -> live.setTenantId(8L);
            case "expired" -> live.setExpiresTime(LocalDateTime.now().minusSeconds(1));
            case "revoked" -> when(tokens.checkAccessToken("sample-token")).thenReturn(success(null));
            case "no-marker" -> reset(sessions);
            case "disabled" -> member.setStatus(CommonStatusEnum.DISABLE.getStatus());
            case "foreign-member" -> member.setTenantId(8L);
            case "ignore-tenant" -> TenantContextHolder.setIgnore(true);
        }
        assertThrows(ServiceException.class, () -> service.verifiedMobile("blank-code".equals(scenario) ? " " : "one-time-code"));
        verifyNoInteractions(weixin);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign", "malformed", "absent", "provider-error"})
    void rejectsInvalidAuthorizationWithoutDisclosingUpstreamDetails(String scenario) {
        switch (scenario) {
            case "foreign" -> phone.setCountryCode("1");
            case "malformed" -> phone.setPurePhoneNumber("not-a-mobile");
            case "absent" -> when(weixin.exchangeWxMaPhoneNumber(MEMBER, "one-time-code")).thenReturn(success(null));
            case "provider-error" -> when(weixin.exchangeWxMaPhoneNumber(MEMBER, "one-time-code"))
                    .thenThrow(new IllegalStateException("private-provider-details"));
        }
        var error = assertThrows(ServiceException.class, () -> service.verifiedMobile("one-time-code"));
        assertFalse(error.getMessage().contains("private-provider-details"));
        assertNull(error.getCause());
        verify(users).getUser(3L);
        verifyNoMoreInteractions(users);
    }
}
