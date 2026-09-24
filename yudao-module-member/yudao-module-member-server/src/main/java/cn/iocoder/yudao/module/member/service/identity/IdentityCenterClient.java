package cn.iocoder.yudao.module.member.service.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class IdentityCenterClient {
    private final IdentityBridgeProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public JsonNode post(String operation, Map<String, Object> payload) {
        String path = "/internal/identity/" + operation;
        if (!java.util.Set.of("claim", "complete", "resolve", "phone").contains(operation)) { throw IdentityPolicy.unavailable(); }
        try {
            properties.validateTransport();
            String body = mapper.writeValueAsString(payload);
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            String nonce = UUID.randomUUID().toString();
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getCenterUrl().replaceAll("/$", "") + path))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json")
                    .header("X-Identity-Key-Id", properties.getKeyId())
                    .header("X-Identity-Timestamp", timestamp).header("X-Identity-Nonce", nonce)
                    .header("X-Identity-Signature", IdentityCrypto.sign(properties.getHmacSecret(),
                            IdentityCrypto.canonical(path, timestamp, nonce, body)))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = mapper.readTree(response.body());
            if (response.statusCode() != 200 || !envelope.has("code") || envelope.path("code").asInt(-1) != 0) {
                // Never forward upstream bodies, which can contain personal information.
                if (envelope.path("msg").asText().contains("IDENTITY_CONFLICT")) { throw IdentityPolicy.conflict(); }
                throw IdentityPolicy.unavailable();
            }
            if (!envelope.hasNonNull("data")) { throw IdentityPolicy.unavailable(); }
            return envelope.get("data");
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw IdentityPolicy.unavailable(); }
        catch (Exception e) { throw IdentityPolicy.unavailable(); }
    }
}
