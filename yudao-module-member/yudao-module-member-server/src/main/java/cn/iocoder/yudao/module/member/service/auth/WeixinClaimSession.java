package cn.iocoder.yudao.module.member.service.auth;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthLoginRespVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class WeixinClaimSession {
    private final StringRedisTemplate redis;

    void record(AppAuthLoginRespVO login) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || TenantContextHolder.isIgnore() || login.getUserId() == null
                || !StringUtils.hasText(login.getOpenid()) || !StringUtils.hasText(login.getAccessToken())
                || login.getExpiresTime() == null) {
            return;
        }
        Duration ttl = Duration.between(LocalDateTime.now(), login.getExpiresTime());
        if (ttl.toMillis() > 0) {
            redis.opsForValue().set(key(tenantId, login.getUserId(), login.getAccessToken()),
                    DigestUtil.sha256Hex(login.getOpenid()), ttl);
        }
    }

    public boolean matches(Long tenantId, Long userId, String token, String openid) {
        return StringUtils.hasText(openid) && DigestUtil.sha256Hex(openid).equals(
                redis.opsForValue().get(key(tenantId, userId, token)));
    }

    private String key(Long tenantId, Long userId, String token) {
        return "member:claim:wx-session:" + tenantId + ":" + userId + ":" + DigestUtil.sha256Hex(token);
    }
}
