package cn.iocoder.yudao.module.trade.framework.logistics.sf;

import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsAccountDO;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SfOpenApiClientTest {

    @Test
    void invokeSendsMillisecondTimestamp() throws Exception {
        AtomicReference<Map<String, String>> form = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sf", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            form.set(Arrays.stream(body.split("&")).map(part -> part.split("=", 2))
                    .collect(Collectors.toMap(parts -> decode(parts[0]), parts -> decode(parts[1]))));
            byte[] response = "{\"apiResultCode\":\"A1000\",\"apiResultData\":{\"success\":true}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            long before = System.currentTimeMillis();
            SfOpenApiClient client = new SfOpenApiClient();
            ReflectionTestUtils.setField(client, "endpoint",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/sf");
            client.invoke(new TradeLogisticsAccountDO().setEndpoint("https://attacker.example.test")
                            .setPartnerId("partner").setCheckWord("check"),
                    "TEST_SERVICE", JsonNodeFactory.instance.objectNode().put("orderId", "ORDER-1"));
            long after = System.currentTimeMillis();

            long timestamp = Long.parseLong(form.get().get("timestamp"));
            assertThat(timestamp).isBetween(before, after);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void productionEndpointUsesOfficialGateway() {
        assertThat(SfOpenApiClient.PRODUCTION_ENDPOINT)
                .isEqualTo("https://bspgw.sf-express.com/std/service");
    }

    @Test
    void invokeRejectsOversizedChunkedResponseWithoutUnboundedBuffering() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/sf", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            byte[] chunk = new byte[8192];
            int remaining = SfOpenApiClient.MAX_RESPONSE_BYTES + 1;
            while (remaining > 0) {
                int length = Math.min(remaining, chunk.length);
                exchange.getResponseBody().write(chunk, 0, length);
                remaining -= length;
            }
            exchange.close();
        });
        server.start();
        try {
            SfOpenApiClient client = new SfOpenApiClient();
            ReflectionTestUtils.setField(client, "endpoint",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/sf");

            assertThatThrownBy(() -> client.invoke(new TradeLogisticsAccountDO()
                            .setPartnerId("partner").setCheckWord("check"), "TEST_SERVICE",
                    JsonNodeFactory.instance.objectNode()))
                    .isInstanceOf(SfApiException.class)
                    .extracting("code")
                    .isEqualTo("RESPONSE_TOO_LARGE");
        } finally {
            server.stop(0);
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
