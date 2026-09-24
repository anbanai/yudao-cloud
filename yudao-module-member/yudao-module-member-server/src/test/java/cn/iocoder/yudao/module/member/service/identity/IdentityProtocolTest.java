package cn.iocoder.yudao.module.member.service.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class IdentityProtocolTest {
    private IdentityBridgeProperties config() {
        IdentityBridgeProperties p = new IdentityBridgeProperties();
        p.setEnabled(true); p.setAppId("wxB"); p.setAppSecret("wechat-secret");
        p.setBackendInstance("B-server"); p.setTenantId(1L); p.setKeyId("key-v1");
        p.setHmacSecret(java.util.Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        p.setEncryptionKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        p.setSourceAppIds(Set.of("wxA")); p.setCenterUrl("https://identity.example.invalid");
        p.setPhoneSharingConsentText("同意同步验证手机号用于统一账号服务");
        p.setPhoneSharingPrivacyUrl("https://identity.example.invalid/privacy");
        p.setPhoneSharingPolicyVersion("1"); p.setIdentityOperatorName("operator");
        return p;
    }
    @Test void enabledRequiresHttpsAndNonemptyCredentials() {
        IdentityBridgeProperties p = config(); assertDoesNotThrow(p::validate);
        p.setCenterUrl("http://identity.example.invalid"); assertThrows(IllegalArgumentException.class, p::validate);
        p.setAllowLocalHttp(true); assertThrows(IllegalArgumentException.class, p::validate);
        p.setCenterUrl("https://identity.example.invalid/path"); assertThrows(IllegalArgumentException.class, p::validate);
        p.setCenterUrl("https://identity.example.invalid"); p.setHmacSecret("");
        assertThrows(IllegalArgumentException.class, p::validate);
    }
    @Test void httpOverrideIsLimitedToExplicitLocalProfiles() {
        IdentityBridgeProperties p = config();
        p.setCenterUrl("http://127.0.0.1:8080"); p.setAllowLocalHttp(true);
        org.springframework.mock.env.MockEnvironment environment = new org.springframework.mock.env.MockEnvironment();
        p.setEnvironment(environment);
        assertThrows(IllegalArgumentException.class, p::validate);
        environment.setActiveProfiles("test"); assertDoesNotThrow(p::validate);
        environment.setActiveProfiles("test", "production"); assertThrows(IllegalArgumentException.class, p::validate);
    }
    @Test void disabledNeedsNoSecrets() { assertDoesNotThrow(new IdentityBridgeProperties()::validate); }
    @Test void exactRawBodyAndPathAreSignedAndNonceChangesOnRetry() throws Exception {
        IdentityBridgeProperties p = config();
        p.setAllowLocalHttp(true);
        p.setEnvironment(new org.springframework.mock.env.MockEnvironment().withProperty("spring.profiles.active", "test"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> nonce = new AtomicReference<>();
        AtomicReference<Throwable> handlerFailure = new AtomicReference<>();
        server.createContext("/internal/identity/claim", exchange -> {
            try {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String ts = exchange.getRequestHeaders().getFirst("X-Identity-Timestamp");
                String n = exchange.getRequestHeaders().getFirst("X-Identity-Nonce");
                assertEquals("key-v1", exchange.getRequestHeaders().getFirst("X-Identity-Key-Id"));
                assertEquals(IdentityCrypto.sign(p.getHmacSecret(), IdentityCrypto.canonical("/internal/identity/claim", ts, n, body)),
                        exchange.getRequestHeaders().getFirst("X-Identity-Signature"));
                assertTrue(Math.abs(System.currentTimeMillis()/1000 - Long.parseLong(ts)) <= 60);
                assertNotEquals(nonce.getAndSet(n), n);
                assertTrue(body.contains("中文"));
                byte[] response = "{\"code\":0,\"data\":{\"handoff_id\":\"h1\"}}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response);
            } catch (Throwable failure) { handlerFailure.set(failure); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            p.setCenterUrl("http://127.0.0.1:" + server.getAddress().getPort());
            IdentityCenterClient client = new IdentityCenterClient(p, new ObjectMapper());
            assertEquals("h1", client.post("claim", Map.of("code", "中文", "request_id", "request")).path("handoff_id").asText());
            client.post("claim", Map.of("code", "中文", "request_id", "request"));
            assertNull(handlerFailure.get());
        } finally { server.stop(0); }
    }
    @Test void upstreamSecretsNeverAppearInClientErrors() throws Exception {
        IdentityBridgeProperties p = config();
        p.setAllowLocalHttp(true);
        p.setEnvironment(new org.springframework.mock.env.MockEnvironment().withProperty("spring.profiles.active", "test"));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/identity/claim", exchange -> {
            byte[] response = "{\"code\":123,\"msg\":\"secret-code 13800000000\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.start();
        try {
            p.setCenterUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var error = assertThrows(RuntimeException.class, () -> new IdentityCenterClient(p, new ObjectMapper()).post("claim", Map.of()));
            assertFalse(error.getMessage().contains("secret-code")); assertFalse(error.getMessage().contains("13800000000"));
        } finally { server.stop(0); }
    }
}
