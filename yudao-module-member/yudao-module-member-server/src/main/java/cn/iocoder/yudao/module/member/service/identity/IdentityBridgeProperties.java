package cn.iocoder.yudao.module.member.service.identity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.*;

/** Trusted tenant context selects a configured app/key; request payloads never choose credentials. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "yudao.identity-bridge")
public class IdentityBridgeProperties extends IdentityBridgeScope {
    private Map<Long, IdentityBridgeScope> tenants = new HashMap<>();
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment environment;
    private static final IdentityBridgeScope DISABLED = new IdentityBridgeScope();

    private IdentityBridgeScope selected() {
        if (tenants.isEmpty()) { return null; } // Backward-compatible single-scope configuration.
        Long tenant = TenantContextHolder.getTenantId();
        IdentityBridgeScope configured = tenant == null ? DISABLED : tenants.getOrDefault(tenant, DISABLED);
        if (configured.getTenantId() != null && !configured.getTenantId().equals(tenant)) { return DISABLED; }
        return configured;
    }
    @Override public boolean isEnabled() {
        IdentityBridgeScope scope = selected();
        return super.isEnabled() && (scope == null || scope.isEnabled());
    }
    @Override public String getCenterUrl() { IdentityBridgeScope s = selected(); return s == null ? super.getCenterUrl() : s.getCenterUrl(); }
    @Override public boolean isAllowLocalHttp() { IdentityBridgeScope s = selected(); return s == null ? super.isAllowLocalHttp() : s.isAllowLocalHttp(); }
    @Override public String getAppId() { IdentityBridgeScope s = selected(); return s == null ? super.getAppId() : s.getAppId(); }
    @Override public String getAppSecret() { IdentityBridgeScope s = selected(); return s == null ? super.getAppSecret() : s.getAppSecret(); }
    @Override public String getBackendInstance() { IdentityBridgeScope s = selected(); return s == null ? super.getBackendInstance() : s.getBackendInstance(); }
    @Override public Long getTenantId() { IdentityBridgeScope s = selected(); return s == null ? super.getTenantId() : s == DISABLED ? null : TenantContextHolder.getTenantId(); }
    @Override public String getKeyId() { IdentityBridgeScope s = selected(); return s == null ? super.getKeyId() : s.getKeyId(); }
    @Override public String getHmacSecret() { IdentityBridgeScope s = selected(); return s == null ? super.getHmacSecret() : s.getHmacSecret(); }
    @Override public String getEncryptionKey() { IdentityBridgeScope s = selected(); return s == null ? super.getEncryptionKey() : s.getEncryptionKey(); }
    @Override public Set<String> getSourceAppIds() { IdentityBridgeScope s = selected(); return s == null ? super.getSourceAppIds() : s.getSourceAppIds(); }
    @Override public String getPhoneSharingConsentText() { IdentityBridgeScope s = selected(); return s == null ? super.getPhoneSharingConsentText() : s.getPhoneSharingConsentText(); }
    @Override public String getPhoneSharingPrivacyUrl() { IdentityBridgeScope s = selected(); return s == null ? super.getPhoneSharingPrivacyUrl() : s.getPhoneSharingPrivacyUrl(); }
    @Override public String getPhoneSharingPolicyVersion() { IdentityBridgeScope s = selected(); return s == null ? super.getPhoneSharingPolicyVersion() : s.getPhoneSharingPolicyVersion(); }
    @Override public String getIdentityOperatorName() { IdentityBridgeScope s = selected(); return s == null ? super.getIdentityOperatorName() : s.getIdentityOperatorName(); }

    public void validateCurrentScope() {
        if (!isEnabled()) { throw IdentityPolicy.unavailable(); }
        validateScope(this);
    }
    public void validateTransport() { validateTransport(this); }
    public String scope() { return getTenantId() + ":" + getAppId() + ":" + getBackendInstance(); }
    public Collection<Long> registeredTenantIds() {
        if (!super.isEnabled()) { return List.of(); }
        if (tenants.isEmpty()) { return super.getTenantId() == null ? List.of() : List.of(super.getTenantId()); }
        return tenants.entrySet().stream().filter(entry -> entry.getValue().isEnabled()).map(Map.Entry::getKey).toList();
    }
    @PostConstruct
    public void validate() {
        if (!super.isEnabled()) { return; }
        if (tenants.isEmpty()) {
            validateScope(this);
        } else {
            tenants.forEach((tenant, scope) -> {
                if (scope.getTenantId() != null && !tenant.equals(scope.getTenantId())) {
                    throw new IllegalArgumentException("Identity registry tenant scope mismatch");
                }
                scope.setTenantId(tenant);
                if (scope.isEnabled()) { validateScope(scope); }
            });
        }
    }
    private void validateTransport(IdentityBridgeScope scope) {
        URI uri = URI.create(scope.getCenterUrl());
        boolean local = uri.getHost() != null && Set.of("localhost", "127.0.0.1", "::1").contains(uri.getHost());
        boolean localProfile = environment != null && environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local", "test", "unit-test"))
                && !environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod", "production"));
        if ((!"https".equals(uri.getScheme()) && !(scope.isAllowLocalHttp() && localProfile && local && "http".equals(uri.getScheme())))
                || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Identity center requires HTTPS origin (local profile loopback override only)");
        }
    }
    private void validateScope(IdentityBridgeScope scope) {
        validateTransport(scope);
        if (scope.getAppId().isBlank() || scope.getAppSecret().isBlank() || scope.getBackendInstance().isBlank() || scope.getTenantId() == null
                || scope.getKeyId().isBlank() || Base64.getDecoder().decode(scope.getHmacSecret()).length < 32 || scope.getSourceAppIds().isEmpty()
                || scope.getPhoneSharingConsentText().isBlank() || scope.getIdentityOperatorName().isBlank()
                || scope.getPhoneSharingPolicyVersion().isBlank() || !scope.getPhoneSharingPrivacyUrl().startsWith("https://")) {
            throw new IllegalArgumentException("Identity bridge credentials/scope/disclosure missing");
        }
        new IdentityCrypto(scope.getEncryptionKey());
    }
}
