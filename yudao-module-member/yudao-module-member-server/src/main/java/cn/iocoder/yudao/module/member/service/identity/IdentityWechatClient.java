package cn.iocoder.yudao.module.member.service.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Exchanges every login code directly with B's configured WeChat app; never reuses legacy code caches. */
@Component
@RequiredArgsConstructor
public class IdentityWechatClient {
    private final IdentityBridgeProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    public String exchange(String code) {
        if (code == null || code.isBlank() || code.length() > 256) { throw IdentityPolicy.unavailable(); }
        JsonNode result = get("https://api.weixin.qq.com/sns/jscode2session?appid=" + encode(properties.getAppId())
                + "&secret=" + encode(properties.getAppSecret()) + "&js_code=" + encode(code) + "&grant_type=authorization_code");
        String openid = result.path("openid").asText();
        if (openid.isBlank() || result.path("errcode").asInt() != 0) { throw IdentityPolicy.unavailable(); }
        return openid;
    }
    public String verifiedPhone(String code) {
        if (code == null || code.isBlank()) { throw IdentityPolicy.unavailable(); }
        JsonNode token = get("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                + encode(properties.getAppId()) + "&secret=" + encode(properties.getAppSecret()));
        if (!token.hasNonNull("access_token")) { throw IdentityPolicy.unavailable(); }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token="
                    + encode(token.get("access_token").asText()))).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("code", code)))).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode result = mapper.readTree(response.body());
            JsonNode info = result.path("phone_info");
            if (response.statusCode() != 200 || result.path("errcode").asInt(-1) != 0
                    || !properties.getAppId().equals(info.path("watermark").path("appid").asText())
                    || !"86".equals(info.path("countryCode").asText())
                    || !info.path("purePhoneNumber").asText().matches("1[0-9]{10}")) { throw IdentityPolicy.unavailable(); }
            return info.get("purePhoneNumber").asText();
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw IdentityPolicy.unavailable(); }
        catch (Exception e) { throw IdentityPolicy.unavailable(); }
    }
    private JsonNode get(String url) {
        try {
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) { throw IdentityPolicy.unavailable(); }
            return mapper.readTree(response.body());
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw IdentityPolicy.unavailable(); }
        catch (Exception e) { throw IdentityPolicy.unavailable(); }
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
