package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi;
import cn.iocoder.yudao.framework.common.biz.system.oauth2.dto.OAuth2AccessTokenRespDTO;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.*;
import cn.iocoder.yudao.module.member.dal.dataobject.user.MemberUserDO;
import cn.iocoder.yudao.module.member.service.user.MemberUserService;
import cn.iocoder.yudao.module.system.api.logger.LoginLogApi;
import cn.iocoder.yudao.module.system.api.sms.SmsCodeApi;
import cn.iocoder.yudao.module.system.api.social.SocialClientApi;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import cn.iocoder.yudao.module.system.api.social.dto.SocialUserRespDTO;
import cn.iocoder.yudao.module.system.api.social.dto.SocialWxPhoneNumberInfoRespDTO;
import cn.iocoder.yudao.module.system.enums.social.SocialTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeixinClaimLoginTest {
    private final MemberAuthServiceImpl auth = new MemberAuthServiceImpl();
    private final SocialUserApi social = mock(SocialUserApi.class);
    private final WeixinClaimSession sessions = mock(WeixinClaimSession.class);
    private final Integer mini = SocialTypeEnum.WECHAT_MINI_PROGRAM.getType();

    @BeforeEach
    void setUp() {
        var tokens = mock(OAuth2TokenCommonApi.class);
        var token = new OAuth2AccessTokenRespDTO().setUserId(1L).setAccessToken("test-token");
        when(tokens.createAccessToken(any())).thenReturn(success(token));
        when(tokens.refreshAccessToken(any(), any())).thenReturn(success(token));
        var users = mock(MemberUserService.class);
        var member = MemberUserDO.builder().id(1L).status(CommonStatusEnum.ENABLE.getStatus()).build();
        when(users.getUser(1L)).thenReturn(member);
        when(users.getUserByMobile(any())).thenReturn(member);
        when(users.createUserIfAbsent(any(), any(), any())).thenReturn(member);
        when(users.isPasswordMatch(any(), any())).thenReturn(true);
        var logs = mock(LoginLogApi.class);
        when(logs.createLoginLog(any())).thenReturn(success(true));
        var sms = mock(SmsCodeApi.class);
        when(sms.useSmsCode(any())).thenReturn(success(true));
        var weixin = mock(SocialClientApi.class);
        when(weixin.exchangeWxMaPhoneNumber(any(), any())).thenReturn(success(new SocialWxPhoneNumberInfoRespDTO()
                .setPurePhoneNumber("13900000000")));
        when(social.getSocialUserByCode(any(), any(), any(), any())).thenReturn(success(
                new SocialUserRespDTO("same-openid", null, null, 1L)));
        when(social.bindSocialUser(any())).thenReturn(success("same-openid"));
        ReflectionTestUtils.setField(auth, "oauth2TokenApi", tokens);
        ReflectionTestUtils.setField(auth, "userService", users);
        ReflectionTestUtils.setField(auth, "loginLogApi", logs);
        ReflectionTestUtils.setField(auth, "smsCodeApi", sms);
        ReflectionTestUtils.setField(auth, "socialClientApi", weixin);
        ReflectionTestUtils.setField(auth, "socialUserApi", social);
        ReflectionTestUtils.setField(auth, "claimSession", sessions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "sms", "other-social", "refresh"})
    void otherLoginDoesNotIssueProof(String kind) {
        switch (kind) {
            case "password" -> auth.login(new AppAuthLoginReqVO().setSocialType(mini));
            case "sms" -> auth.smsLogin(new AppAuthSmsLoginReqVO().setSocialType(mini));
            case "other-social" -> auth.socialLogin(new AppAuthSocialLoginReqVO().setType(SocialTypeEnum.WECHAT_MP.getType()));
            case "refresh" -> auth.refreshToken("sample-refresh");
        }
        verifyNoInteractions(sessions);
    }

    @ParameterizedTest
    @ValueSource(strings = {"social", "mini-phone"})
    void miniLoginIssuesProofForTheActualToken(String kind) {
        AppAuthLoginRespVO result;
        if ("social".equals(kind)) {
            result = auth.socialLogin(new AppAuthSocialLoginReqVO().setType(mini).setState("client-state"));
            var state = ArgumentCaptor.forClass(String.class);
            verify(social).getSocialUserByCode(any(), eq(mini), any(), state.capture());
            assertNotEquals("client-state", state.getValue());
        } else {
            result = auth.weixinMiniAppLogin(new AppAuthWeixinMiniAppLoginReqVO().setState("client-state"));
        }
        assertEquals("test-token", result.getAccessToken());
        assertEquals("same-openid", result.getOpenid());
        verify(sessions).record(result);
    }
}
