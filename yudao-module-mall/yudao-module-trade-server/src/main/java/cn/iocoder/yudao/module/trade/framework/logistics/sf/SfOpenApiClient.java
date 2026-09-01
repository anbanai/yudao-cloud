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

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Map;

@Component
public class SfOpenApiClient {

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
        try (HttpResponse response = HttpRequest.post(account.getEndpoint()).form(form)
                .timeout(20_000).execute()) {
            if (!response.isOk()) {
                throw new SfApiException("HTTP_" + response.getStatus(), "顺丰 HTTP 状态 " + response.getStatus());
            }
            return parseResponse(response.body());
        } catch (HttpException exception) {
            boolean timeout = exception.getCause() instanceof SocketTimeoutException
                    || exception.getMessage() != null && exception.getMessage().toLowerCase().contains("timed out");
            throw new SfApiException(timeout ? "TIMEOUT" : "NETWORK", exception.getMessage(), timeout, exception);
        }
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
