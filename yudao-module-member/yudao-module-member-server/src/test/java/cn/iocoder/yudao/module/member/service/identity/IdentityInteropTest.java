package cn.iocoder.yudao.module.member.service.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in, synthetic SQLite Go router fixture; never point at deployed services. */
@EnabledIfEnvironmentVariable(named = "IDENTITY_INTEROP_FIXTURE_FILE", matches = ".+")
class IdentityInteropTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void realGoRouterMatchesJavaClaimPhoneWithdrawalAndInactiveResolve() throws Exception {
        JsonNode fixture = json.readTree(Files.readString(Path.of(System.getenv("IDENTITY_INTEROP_FIXTURE_FILE"))));
        String url = fixture.path("url").asText();
        assertTrue(url.startsWith("http://127.0.0.1:"), "Interop fixture must be a local synthetic server");
        IdentityBridgeProperties properties = new IdentityBridgeProperties();
        properties.setAllowLocalHttp(true);
        properties.setEnvironment(new org.springframework.mock.env.MockEnvironment().withProperty("spring.profiles.active", "test"));
        properties.setCenterUrl(url); properties.setKeyId(fixture.path("key_id").asText());
        properties.setHmacSecret(fixture.path("secret_base64").asText());
        IdentityCenterClient center = new IdentityCenterClient(properties, json);
        String openid = fixture.path("openid").asText();
        String member = fixture.path("member_id").asText();
        String request = UUID.randomUUID().toString();
        HttpRequest issueRequest = HttpRequest.newBuilder(URI.create(url + "/weapp/identity/handoffs"))
                .header("Authorization", "Bearer " + fixture.path("source_token").asText())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("mall_id", fixture.path("mall_id").asText(), "request_id", request)))).build();
        JsonNode issueEnvelope = json.readTree(HttpClient.newHttpClient().send(issueRequest, HttpResponse.BodyHandlers.ofString()).body());
        assertEquals(0, issueEnvelope.path("code").asInt(-1), "Fresh synthetic handoff issuance failed");
        String handoffCode = issueEnvelope.path("data").path("code").asText();
        JsonNode claim = center.post("claim", Map.of("code", handoffCode,
                "request_id", request, "openid", openid, "source_app_id", fixture.path("source_app_id").asText()));
        assertFalse(claim.path("subject_id").asText().isBlank());
        JsonNode completed = center.post("complete", Map.of("handoff_id", claim.path("handoff_id").asText(),
                "request_id", request, "openid", openid, "member_id", member));
        assertEquals(claim.path("subject_id"), completed.path("subject_id"));
        Map<String, Object> binding = Map.of("openid", openid, "member_id", member);
        assertTrue(center.post("resolve", binding).path("linked").asBoolean());
        assertTrue(center.post("phone", Map.of("openid", openid, "member_id", member, "event_id", UUID.randomUUID().toString(),
                "version", 1, "phone", "13800000000", "country_code", "86", "verified_at", System.currentTimeMillis(),
                "verification_method", "wechat", "share_consent", true)).path("accepted").asBoolean());
        JsonNode me = api(url, "/weapp/identity/me", "GET", fixture.path("source_token").asText());
        assertTrue(me.path("phone_verified").asBoolean(), "B-origin phone must reach A after opt-in");
        assertTrue(center.post("phone", Map.of("openid", openid, "member_id", member, "event_id", UUID.randomUUID().toString(),
                "version", 2, "share_consent", false)).path("accepted").asBoolean());
        me = api(url, "/weapp/identity/me", "GET", fixture.path("source_token").asText());
        assertFalse(me.path("phone_verified").asBoolean(), "B opt-out must withdraw the origin phone from A");
        assertTrue(center.post("resolve", binding).path("phone").isNull());
        api(url, "/weapp/identity/grants/" + fixture.path("mall_id").asText(), "DELETE", fixture.path("source_token").asText());
        JsonNode revoked = center.post("resolve", binding);
        assertFalse(revoked.path("linked").asBoolean()); assertTrue(revoked.path("phone").isNull());
        assertTrue(center.post("phone", Map.of("openid", openid, "member_id", member, "event_id", UUID.randomUUID().toString(),
                "version", 3, "share_consent", false)).path("accepted").asBoolean(), "Revoked grant must not block withdrawal");
        api(url, "/admin/navigation/malls/" + fixture.path("mall_id").asText() + "/disable", "POST", fixture.path("admin_token").asText());
        assertFalse(center.post("resolve", binding).path("linked").asBoolean());
    }
    private JsonNode api(String url, String path, String method, String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url + path)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.noBody()).build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode envelope = json.readTree(response.body());
        assertEquals(200, response.statusCode(), "Synthetic route failed: " + path);
        assertEquals(0, envelope.path("code").asInt(-1), "Synthetic envelope failed: " + path);
        return envelope.path("data");
    }
}
