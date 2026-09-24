package cn.iocoder.yudao.module.member.service.identity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class IdentityOutboxJob {
    private final IdentityBridgeProperties properties;
    private final MemberIdentityService service;
    @Scheduled(fixedDelayString = "${yudao.identity-bridge.retry-delay-ms:30000}")
    public void retry() {
        Long previous = TenantContextHolder.getTenantId();
        try {
            for (Long tenant : properties.registeredTenantIds()) {
                TenantContextHolder.setTenantId(tenant);
                try { service.retryOutbox(); }
                catch (RuntimeException ignored) { /* A failed scope is retried next tick; never block other tenants or log PII. */ }
            }
        } finally { if (previous == null) { TenantContextHolder.clear(); } else { TenantContextHolder.setTenantId(previous); } }
    }
}
