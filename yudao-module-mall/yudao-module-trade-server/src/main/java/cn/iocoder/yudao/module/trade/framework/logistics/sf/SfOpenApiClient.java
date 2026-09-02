package cn.iocoder.yudao.module.trade.framework.logistics.sf;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpException;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import cn.iocoder.yudao.module.trade.framework.logistics.SfOpenApiSigner;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

@Component
public class SfOpenApiClient {

    public static final String PRODUCTION_ENDPOINT = "https://bspgw.sf-express.com/std/service";
    static final int MAX_RESPONSE_BYTES = 15 * 1024 * 1024;

    @Value("${yudao.trade.logistics.sf.endpoint:" + PRODUCTION_ENDPOINT + "}")
    private String endpoint = PRODUCTION_ENDPOINT;

    public JsonNode invoke(TradeLogisticsAccountDO account, String serviceCode, JsonNode message) {
        long timestamp = Instant.now().toEpochMilli();
        String msgData = JsonUtils.toJsonString(message);
        Map<String, Object> form = MapUtil.<String, Object>builder()
                .put("partnerID", account.getPartnerId())
                .put("requestID", IdUtil.fastSimpleUUID())
                .put("serviceCode", serviceCode)
                .put("timestamp", timestamp)
                .put("msgData", msgData)
                .put("msgDigest", SfOpenApiSigner.sign(msgData, timestamp, account.getCheckWord()))
                .build();
        try (HttpResponse response = HttpRequest.post(endpoint).form(form)
                .timeout(20_000).executeAsync()) {
            if (!response.isOk()) {
                throw new SfApiException("HTTP_" + response.getStatus(), "顺丰 HTTP 状态 " + response.getStatus());
            }
            return parseResponse(readBoundedBody(response));
        } catch (SfApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new SfApiException("NETWORK", "读取顺丰响应失败", false, exception);
        } catch (HttpException exception) {
            boolean timeout = exception.getCause() instanceof SocketTimeoutException
                    || exception.getMessage() != null && exception.getMessage().toLowerCase().contains("timed out");
            throw new SfApiException(timeout ? "TIMEOUT" : "NETWORK", exception.getMessage(), timeout, exception);
        }
    }

    private String readBoundedBody(HttpResponse response) throws IOException {
        if (response.contentLength() > MAX_RESPONSE_BYTES) {
            throw new SfApiException("RESPONSE_TOO_LARGE", "顺丰响应超过 15 MB 限制");
        }
        byte[] bytes = response.bodyStream().readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) {
            throw new SfApiException("RESPONSE_TOO_LARGE", "顺丰响应超过 15 MB 限制");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private JsonNode parseResponse(String body) {
        JsonNode root = JsonUtils.parseTree(body);
        if (root == null) {
            throw new SfApiException("EMPTY_RESPONSE", "顺丰返回为空");
        }
        String apiCode = root.path("apiResultCode").asText();
        if (!"A1000".equals(apiCode)) {
            throw new SfApiException(apiCode, root.path("apiErrorMsg").asText("顺丰接口失败"));
        }
        JsonNode apiData = root.path("apiResultData");
        if (apiData.isTextual()) {
            apiData = JsonUtils.parseTree(apiData.asText());
        }
        if (apiData == null || apiData.isMissingNode()) {
            throw new SfApiException("INVALID_RESPONSE", "顺丰返回缺少 apiResultData");
        }
        if (apiData.has("success") && !apiData.path("success").asBoolean()) {
            throw new SfApiException(apiData.path("errorCode").asText("SF_ERROR"),
                    apiData.path("errorMsg").asText("顺丰业务失败"));
        }
        JsonNode msgData = apiData.path("msgData");
        return msgData.isMissingNode() || msgData.isNull() ? apiData : msgData;
    }
}
