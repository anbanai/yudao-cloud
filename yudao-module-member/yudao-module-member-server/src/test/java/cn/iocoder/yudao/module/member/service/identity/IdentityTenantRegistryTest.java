package cn.iocoder.yudao.module.member.service.identity;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IdentityTenantRegistryTest {
    @AfterEach void cleanup() { TenantContextHolder.clear(); }
    private IdentityBridgeProperties registry() {
        IdentityBridgeProperties p = new IdentityBridgeProperties(); p.setEnabled(true);
        p.setTenants(Map.of(1L, scope("wxTenant1", "key1", "one"), 2L, scope("wxTenant2", "key2", "two")));
        p.validate(); return p;
    }
    private IdentityBridgeScope scope(String app, String key, String salt) {
        IdentityBridgeScope s = new IdentityBridgeScope(); s.setEnabled(true);
        s.setAppId(app); s.setAppSecret("secret-" + app); s.setBackendInstance("shared-backend");
        s.setKeyId(key); s.setHmacSecret(Base64.getEncoder().encodeToString((salt.repeat(11)).getBytes(StandardCharsets.UTF_8)));
        s.setEncryptionKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        s.setSourceAppIds(Set.of("wxA")); s.setCenterUrl("https://identity.example.invalid");
        s.setPhoneSharingConsentText("明确用途及手机号共享同意"); s.setIdentityOperatorName("identity operator");
        s.setPhoneSharingPolicyVersion("1"); s.setPhoneSharingPrivacyUrl("https://identity.example.invalid/privacy");
        return s;
    }
    @Test void unknownTenantIsDisabledAndSchedulerEnumeratesConfiguredScopes() {
        IdentityBridgeProperties p = registry();
        assertFalse(p.isEnabled());
        assertEquals(Set.of(1L, 2L), new HashSet<>(p.registeredTenantIds()));
        TenantContextHolder.setTenantId(99L); assertFalse(p.isEnabled()); assertEquals("", p.getAppId());
        TenantContextHolder.setTenantId(1L); assertTrue(p.isEnabled()); assertEquals("1:wxTenant1:shared-backend", p.scope());
        TenantContextHolder.setTenantId(2L); assertEquals("2:wxTenant2:shared-backend", p.scope());
    }
    @Test void registryRejectsMismatchedDeclaredTenant() {
        IdentityBridgeProperties p = registry(); p.getTenants().get(1L).setTenantId(2L);
        assertThrows(IllegalArgumentException.class, p::validate);
    }
    @SuppressWarnings("unchecked")
    @Test void freshWechatExchangeUsesOnlySelectedTenantCredentials() throws Exception {
        IdentityBridgeProperties p = registry();
        IdentityWechatClient client = new IdentityWechatClient(p, new ObjectMapper());
        HttpClient http = mock(HttpClient.class); ReflectionTestUtils.setField(client, "http", http);
        List<String> requests = new ArrayList<>();
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0); requests.add(request.uri().getRawQuery());
            HttpResponse<String> response = mock(HttpResponse.class); when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn("{\"openid\":\"openid-" + TenantContextHolder.getTenantId() + "\"}"); return response;
        });
        TenantContextHolder.setTenantId(1L); assertEquals("openid-1", client.exchange("fresh-one"));
        TenantContextHolder.setTenantId(2L); assertEquals("openid-2", client.exchange("fresh-two"));
        assertTrue(requests.get(0).contains("appid=wxTenant1&secret=secret-wxTenant1"));
        assertFalse(requests.get(0).contains("wxTenant2"));
        assertTrue(requests.get(1).contains("appid=wxTenant2&secret=secret-wxTenant2"));
        assertFalse(requests.get(1).contains("wxTenant1"));
    }
    @SuppressWarnings("unchecked")
    @Test void centerHmacKeyAndScopeFollowTrustedTenantContext() throws Exception {
        IdentityBridgeProperties p = registry();
        IdentityCenterClient client = new IdentityCenterClient(p, new ObjectMapper());
        HttpClient http = mock(HttpClient.class); ReflectionTestUtils.setField(client, "http", http);
        List<String> keyIds = new ArrayList<>();
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            keyIds.add(request.headers().firstValue("X-Identity-Key-Id").orElseThrow());
            HttpResponse<String> response = mock(HttpResponse.class); when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn("{\"code\":0,\"data\":{\"linked\":false,\"phone\":null}}"); return response;
        });
        TenantContextHolder.setTenantId(1L); client.post("resolve", Map.of("openid", "one", "member_id", "10"));
        TenantContextHolder.setTenantId(2L); client.post("resolve", Map.of("openid", "two", "member_id", "20"));
        assertEquals(List.of("key1", "key2"), keyIds);
    }
}
