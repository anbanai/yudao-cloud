package cn.iocoder.yudao.module.member.service.auth;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthLoginRespVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WeixinClaimSessionTest {
    @Test
    void onlyIssuedTokenInSameTenantForSameMemberAndBindingMatches() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var sessions = new WeixinClaimSession(redis);
        var login = AppAuthLoginRespVO.builder().userId(12L).accessToken("issued-token").openid("wx-subject")
                .expiresTime(LocalDateTime.now().plusMinutes(5)).build();
        try {
            TenantContextHolder.setTenantId(8L);
            sessions.record(login);
        } finally {
            TenantContextHolder.clear();
        }
        var key = ArgumentCaptor.forClass(String.class);
        var value = ArgumentCaptor.forClass(String.class);
        var ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(key.capture(), value.capture(), ttl.capture());
        assertFalse(key.getValue().contains(login.getAccessToken()));
        assertFalse(value.getValue().contains(login.getOpenid()));
        assertTrue(ttl.getValue().toMillis() > 0 && ttl.getValue().toMinutes() <= 5);
        when(values.get(key.getValue())).thenReturn(value.getValue());
        assertTrue(sessions.matches(8L, 12L, "issued-token", "wx-subject"));
        assertFalse(sessions.matches(8L, 12L, "other-token", "wx-subject"));
        assertFalse(sessions.matches(9L, 12L, "issued-token", "wx-subject"));
        assertFalse(sessions.matches(8L, 13L, "issued-token", "wx-subject"));
        assertFalse(sessions.matches(8L, 12L, "issued-token", "new-binding"));
    }
}
