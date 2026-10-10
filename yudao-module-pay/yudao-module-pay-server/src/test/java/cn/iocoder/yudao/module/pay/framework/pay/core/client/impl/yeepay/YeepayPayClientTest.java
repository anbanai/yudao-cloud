package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.yeepay.yop.sdk.service.common.YopClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.time.LocalDateTime;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YeepayPayClientTest {

    @Test
    void testMoney_usesFixedTwoDecimalPlaces() {
        assertEquals("0.01", YeepayPayClient.money(1));
        assertEquals("12.30", YeepayPayClient.money(1230));
        assertEquals("0.00", YeepayPayClient.money(0));
        assertThrows(IllegalArgumentException.class, () -> YeepayPayClient.money(-1));
    }

    @Test
    void testBuildMiniProgramPayParams_keepsStandardWechatShape() {
        YeepayPayClient client = newClient();
        Map<String, String> result = client.buildMiniProgramPayParams(
                "{\"appId\":\"wx-platform\",\"timeStamp\":\"1710000000\","
                        + "\"nonceStr\":\"nonce\",\"package\":\"prepay_id=tn-1\","
                        + "\"signType\":\"RSA\",\"paySign\":\"wechat-sign\"}", Map.of());
        assertEquals("wx-platform", result.get("appId"));
        assertEquals("1710000000", result.get("timeStamp"));
        assertEquals("prepay_id=tn-1", result.get("package"));
        assertEquals(result.get("package"), result.get("packageValue"));
        assertEquals("RSA", result.get("signType"));
        assertEquals("wechat-sign", result.get("paySign"));
    }

    @Test
    void testBuildMiniProgramPayParams_rejectsPrePayTnWithoutWechatSignature() {
        YeepayPayClient client = newClient();

        assertThrows(IllegalArgumentException.class,
                () -> client.buildMiniProgramPayParams("tn-1", Map.of("appId", "wx-platform")));
    }

    @Test
    void testBuildMiniProgramPayParams_doesNotCompleteIncompleteOfficialPayloadFromOuterResponse() {
        YeepayPayClient client = newClient();

        assertThrows(IllegalArgumentException.class, () -> client.buildMiniProgramPayParams(
                "{\"appId\":\"wx-platform\",\"timeStamp\":\"1710000000\","
                        + "\"nonceStr\":\"nonce\",\"package\":\"prepay_id=tn-1\"}",
                Map.of("signType", "RSA", "paySign", "must-not-be-used")));
    }

    @Test
    void testUnifiedOrder_delayedSettlementSendsPrePayDivideDetail() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"40044\",\"subCode\":\"00000\","
                            + "\"orderId\":\"ORDER-1\",\"prePayTn\":\"{\\\"appId\\\":\\\"wx-platform\\\","
                            + "\\\"timeStamp\\\":\\\"1710000000\\\",\\\"nonceStr\\\":\\\"nonce\\\","
                            + "\\\"package\\\":\\\"prepay_id=tn-1\\\",\\\"signType\\\":\\\"RSA\\\","
                            + "\\\"paySign\\\":\\\"wechat-sign\\\"}\"}}"));
            server.start();
            KeyPair keyPair = keyPair();
            YeepayPayClientConfig config = config(keyPair);
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO request =
                    new cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO();
            request.setUserIp("192.0.2.10");
            request.setOutTradeNo("ORDER-1");
            request.setParentMerchantNo("PARENT-1");
            request.setCollectionMerchantNo("COLLECTION-1");
            request.setSubject("测试商品");
            request.setNotifyUrl("https://merchant.example.test/pay/notify");
            request.setPrice(1234);
            request.setExpireTime(LocalDateTime.of(2026, 10, 10, 12, 34, 56));
            request.setChannelExtras(Map.of("openId", "openid-1",
                    "divideDetail", "[{\"amount\":\"12.34\",\"ledgerNo\":\"SUB-1\","
                            + "\"divideDetailDesc\":\"商家订单分账\"}]"));

            var response = client.unifiedOrder(request);
            assertEquals("ORDER-1", response.getOutTradeNo());
            assertTrue(response.getDisplayContent().contains("wechat-sign"));

            RecordedRequest recorded = server.takeRequest();
            String body = recorded.getBody().readUtf8();
            String decodedBody = URLDecoder.decode(body, StandardCharsets.UTF_8);
            String fullyDecodedBody = URLDecoder.decode(decodedBody, StandardCharsets.UTF_8);
            assertTrue(body.contains("userIp=192.0.2.10"));
            assertTrue(fullyDecodedBody.contains("goodsName=测试商品"));
            assertTrue(fullyDecodedBody.contains("expiredTime=2026-10-10 12:34:56"));
            assertTrue(body.contains("fundProcessType=DELAY_SETTLE"));
            assertTrue(fullyDecodedBody.contains("divideDetail="));
            assertTrue(formValue(fullyDecodedBody, "divideDetail").contains("SUB-1"));
        }
    }

    @Test
    void testUnifiedOrder_delayedSettlementWithoutDivideDetailIsRejectedBeforeSendingRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"40044\",\"subCode\":\"00000\","
                            + "\"orderId\":\"ORDER-1\",\"prePayTn\":\"{\\\"appId\\\":\\\"wx-platform\\\","
                            + "\\\"timeStamp\\\":\\\"1710000000\\\",\\\"nonceStr\\\":\\\"nonce\\\","
                            + "\\\"package\\\":\\\"prepay_id=tn-1\\\",\\\"signType\\\":\\\"RSA\\\","
                            + "\\\"paySign\\\":\\\"wechat-sign\\\"}\"}}"));
            server.start();
            YeepayPayClientConfig config = config(keyPair());
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO request =
                    new cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO();
            request.setUserIp("192.0.2.10");
            request.setOutTradeNo("ORDER-1");
            request.setParentMerchantNo("PARENT-1");
            request.setCollectionMerchantNo("COLLECTION-1");
            request.setSubject("测试商品");
            request.setNotifyUrl("https://merchant.example.test/pay/notify");
            request.setPrice(1234);
            request.setExpireTime(LocalDateTime.of(2026, 10, 10, 12, 34, 56));
            request.setChannelExtras(Map.of("openId", "openid-1"));

            assertThrows(RuntimeException.class, () -> client.unifiedOrder(request));
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test
    void testUnifiedOrder_realtimeDivideRequiresDivideDetailBeforeSendingRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            YeepayPayClientConfig config = config(keyPair());
            config.setFundProcessType(YeepayPayClientConfig.FUND_PROCESS_TYPE_REAL_TIME_DIVIDE);
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            var request = new cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO();
            request.setUserIp("192.0.2.10");
            request.setOutTradeNo("ORDER-1");
            request.setParentMerchantNo("PARENT-1");
            request.setCollectionMerchantNo("COLLECTION-1");
            request.setSubject("测试商品");
            request.setNotifyUrl("https://merchant.example.test/pay/notify");
            request.setPrice(1234);
            request.setExpireTime(LocalDateTime.of(2026, 10, 10, 12, 34, 56));
            request.setChannelExtras(Map.of("openId", "openid-1"));

            assertThrows(RuntimeException.class, () -> client.unifiedOrder(request));
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test
    void testUnifiedOrder_rejectsRealtimeDivideEvenWithValidSnapshot() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"40044\",\"subCode\":\"00000\","
                            + "\"orderId\":\"ORDER-1\",\"prePayTn\":\"{\\\"appId\\\":\\\"wx-platform\\\","
                            + "\\\"timeStamp\\\":\\\"1710000000\\\",\\\"nonceStr\\\":\\\"nonce\\\","
                            + "\\\"package\\\":\\\"prepay_id=tn-1\\\",\\\"signType\\\":\\\"RSA\\\","
                            + "\\\"paySign\\\":\\\"wechat-sign\\\"}\"}}"));
            server.start();
            YeepayPayClientConfig config = config(keyPair());
            config.setFundProcessType(YeepayPayClientConfig.FUND_PROCESS_TYPE_REAL_TIME_DIVIDE);
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            var request = new cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderUnifiedReqDTO();
            request.setUserIp("192.0.2.10");
            request.setOutTradeNo("ORDER-1");
            request.setParentMerchantNo("PARENT-1");
            request.setCollectionMerchantNo("COLLECTION-1");
            request.setSubject("测试商品");
            request.setNotifyUrl("https://merchant.example.test/pay/notify");
            request.setPrice(1234);
            request.setExpireTime(LocalDateTime.of(2026, 10, 10, 12, 34, 56));
            request.setChannelExtras(Map.of("openId", "openid-1",
                    "divideDetail", "[{\"amount\":\"12.34\",\"ledgerNo\":\"SUB-1\"}]"));

            assertThrows(RuntimeException.class, () -> client.unifiedOrder(request));
            assertEquals(0, server.getRequestCount(), "首期统一走延迟结算后的异步分账，不能先实时分账");
        }
    }

    @Test
    void testParseYeepayOrderNotify_prefersOfficialPaySuccessDate() {
        YeepayPayClient client = newClient();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderId", "ORDER-1");
        response.put("status", "SUCCESS");
        response.put("channelOrderId", "CHANNEL-1");
        response.put("paySuccessDate", "2026-10-10 12:34:56");

        var result = client.parseYeepayOrderNotify(response);

        assertEquals(LocalDateTime.of(2026, 10, 10, 12, 34, 56), result.getSuccessTime());
    }

    @Test
    void testParseYeepayRefundNotify_usesRequestIdAsLocalRefundNo() {
        YeepayPayClient client = newClient();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("refundRequestId", "REFUND-REQUEST-1");
        response.put("uniqueRefundNo", "YEEPAY-REFUND-1");
        response.put("refundOrderId", "CHANNEL-REFUND-1");

        var result = client.parseYeepayRefundNotify(response);

        assertEquals("REFUND-REQUEST-1", result.getOutRefundNo());
        assertEquals("YEEPAY-REFUND-1", result.getChannelRefundNo());
    }

    @Test
    void testBuildMerchantRegisterParams_requiresOfficialSubjectObjects() {
        YeepayPayClientConfig config = config(keyPair()).setMerchantNotifyUrl(
                "https://merchant.example.test/pay/notify/yeepay/merchant");
        YeepayPayClient client = new YeepayPayClient(7L, config);
        client.init();

        Map<String, Object> materials = validMerchantMaterials();
        materials.remove("businessAddressInfo");

        assertThrows(IllegalArgumentException.class,
                () -> client.buildMerchantRegisterParams(materials, "REQUEST-1", null));
    }

    @Test
    void testBuildMerchantRegisterParams_requiresMerchantNotifyUrl() {
        YeepayPayClient client = newClient();

        assertThrows(IllegalStateException.class,
                () -> client.buildMerchantRegisterParams(validMerchantMaterials(), "REQUEST-1", null));
    }

    @Test
    void testBuildMerchantRegisterParams_addsSharePermissionAndRemovesUnsupportedMerchantId() {
        YeepayPayClientConfig config = config(keyPair()).setMerchantNotifyUrl(
                "https://merchant.example.test/pay/notify/yeepay/merchant");
        YeepayPayClient client = new YeepayPayClient(7L, config);
        client.init();

        Map<String, Object> params = client.buildMerchantRegisterParams(validMerchantMaterials(), "REQUEST-1", null);

        assertEquals("REQUEST-1", params.get("requestNo"));
        assertEquals("SETTLED_MERCHANT", params.get("businessRole"));
        assertFalse(params.containsKey("merchantId"));
        assertTrue(String.valueOf(params.get("functionService")).contains("SHARE"));
        assertTrue(String.valueOf(params.get("functionServiceQualificationInfo")).contains("MINI_PROGRAM"));
        assertTrue(String.valueOf(params.get("notifyUrl")).startsWith(
                "https://merchant.example.test/pay/notify/yeepay/merchant"));
        assertTrue(String.valueOf(params.get("notifyUrl")).contains("channelId=7"));
    }

    @Test
    void testBuildMerchantRegisterParams_requiresShareSceneForSharePermission() {
        YeepayPayClientConfig config = config(keyPair()).setMerchantNotifyUrl(
                "https://merchant.example.test/pay/notify/yeepay/merchant");
        YeepayPayClient client = new YeepayPayClient(7L, config);
        client.init();
        Map<String, Object> materials = validMerchantMaterials();
        materials.put("functionServiceQualificationInfo", Map.of("otherScene", "ONLINE"));

        assertThrows(IllegalArgumentException.class,
                () -> client.buildMerchantRegisterParams(materials, "REQUEST-1", null));
    }

    @Test
    void testBuildMicroMerchantRegisterParams_allowsMissingContactAndDetectsMicroMerchant() {
        YeepayPayClientConfig config = config(keyPair()).setMerchantNotifyUrl(
                "https://merchant.example.test/pay/notify/yeepay/merchant");
        YeepayPayClient client = new YeepayPayClient(7L, config);
        client.init();

        Map<String, Object> materials = validMerchantMaterials();
        materials.remove("merchantContactInfo");
        Map<String, Object> subjectInfo = new LinkedHashMap<>();
        subjectInfo.put("merchantType", "MICRO");
        materials.put("merchantSubjectInfo", subjectInfo);

        Map<String, Object> params = client.buildMicroMerchantRegisterParams(materials, "MICRO-REQUEST-1", null);

        assertTrue(client.isMicroMerchantMaterials(materials));
        assertTrue(client.isMicroMerchantMaterials(Map.of(
                "merchantSubjectInfo", "{\"merchantType\":\"SMALL_MICRO\"}")));
        assertEquals("MICRO-REQUEST-1", params.get("requestNo"));
        assertTrue(params.get("merchantSubjectInfo") instanceof Map);
        assertFalse(params.containsKey("merchantContactInfo"));
        assertTrue(String.valueOf(params.get("functionService")).contains("SHARE"));
    }

    @Test
    void testMerchantQueries_enforceOfficialIdentifierContracts() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonSuccess());
            server.enqueue(jsonSuccess());
            server.enqueue(jsonSuccess());
            server.start();
            YeepayPayClientConfig config = config(keyPair()).setServerUrl(server.url("/").toString())
                    .setTimeoutSeconds(2).setConnectTimeoutSeconds(2).setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            client.queryMerchantProgress("REQUEST-1", null);
            client.queryMerchantProgress(null, "MERCHANT-1");
            client.queryMerchantStatus("MERCHANT-1");

            RecordedRequest byRequest = server.takeRequest();
            assertEquals("GET", byRequest.getMethod());
            assertTrue(byRequest.getPath().startsWith("/rest/v2.0/mer/register/query?"));
            assertTrue(byRequest.getPath().contains("requestNo=REQUEST-1"));
            assertFalse(byRequest.getPath().contains("merchantNo="));

            RecordedRequest byMerchant = server.takeRequest();
            assertEquals("GET", byMerchant.getMethod());
            assertTrue(byMerchant.getPath().contains("merchantNo=MERCHANT-1"));
            assertFalse(byMerchant.getPath().contains("requestNo="));

            RecordedRequest status = server.takeRequest();
            assertEquals("GET", status.getMethod());
            assertTrue(status.getPath().startsWith("/rest/v1.0/mer/status/query?"));
            assertTrue(status.getPath().contains("merchantNo=MERCHANT-1"));
            assertFalse(status.getPath().contains("requestNo="));

            assertThrows(IllegalArgumentException.class,
                    () -> client.queryMerchantProgress(null, null));
            assertThrows(IllegalArgumentException.class,
                    () -> client.queryMerchantProgress("REQUEST-1", "MERCHANT-1"));
            assertThrows(IllegalArgumentException.class, () -> client.queryMerchantStatus(" "));
        }
    }

    @Test
    void testRepeatMerchantNotify_forwardsOfficialFields() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonSuccess());
            server.start();
            YeepayPayClientConfig config = config(keyPair()).setServerUrl(server.url("/").toString())
                    .setTimeoutSeconds(2).setConnectTimeoutSeconds(2).setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            client.repeatMerchantNotify(Map.of("requestNo", "REQUEST-1", "notifyType", "SMS"));

            RecordedRequest request = server.takeRequest();
            assertEquals("POST", request.getMethod());
            assertEquals("/rest/v2.0/mer/notify/repeat", request.getPath());
            String body = URLDecoder.decode(request.getBody().readUtf8(), StandardCharsets.UTF_8);
            assertTrue(body.contains("requestNo=REQUEST-1"));
            assertTrue(body.contains("notifyType=SMS"));
            assertThrows(IllegalArgumentException.class, () -> client.repeatMerchantNotify(Map.of()));
        }
    }

    @Test
    void testUploadMerchantQualification_usesOfficialYosMultipartField() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"merQualUrl\":\"https://yos.example.test/qual/1\"}}"));
            server.start();
            YeepayPayClientConfig config = config(keyPair()).setServerUrl(server.url("/api/").toString())
                    .setYosServerUrl(server.url("/yos/").toString())
                    .setTimeoutSeconds(2).setConnectTimeoutSeconds(2).setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            Map<String, Object> result = client.uploadMerchantQualification(
                    new ByteArrayInputStream("certificate-bytes".getBytes(StandardCharsets.UTF_8)),
                    "license.jpg");

            assertEquals("https://yos.example.test/qual/1", result.get("merQualUrl"));
            RecordedRequest request = server.takeRequest();
            assertEquals("POST", request.getMethod());
            assertTrue(request.getPath().contains("/yos/v1.0/sys/merchant/qual/upload"));
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("merQual"), body);
            assertTrue(body.contains("license.jpg"), body);
            assertTrue(body.contains("certificate-bytes"));
            assertThrows(IllegalArgumentException.class,
                    () -> client.uploadMerchantQualification(null, "license.jpg"));
            assertThrows(IllegalArgumentException.class,
                    () -> client.uploadMerchantQualification(
                            new ByteArrayInputStream(new byte[]{1}), " "));
        }
    }

    @Test
    void testGetRefund_queriesByRefundRequestId() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"SUCCESS\","
                            + "\"refundRequestId\":\"REFUND-REQUEST-1\","
                            + "\"orderId\":\"ORDER-1\",\"parentMerchantNo\":\"PARENT-1\","
                            + "\"merchantNo\":\"COLLECTION-1\",\"refundAmount\":\"1.00\","
                            + "\"uniqueRefundNo\":\"YEEPAY-REFUND-1\","
                            + "\"refundOrderId\":\"CHANNEL-REFUND-1\"}}"));
            server.start();
            KeyPair keyPair = keyPair();
            YeepayPayClientConfig config = config(keyPair);
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            var result = client.getRefund("ORDER-1", "REFUND-REQUEST-1");

            assertEquals("REFUND-REQUEST-1", result.getOutRefundNo());
            RecordedRequest request = server.takeRequest();
            assertEquals("GET", request.getMethod());
            assertTrue(request.getPath().startsWith("/rest/v1.0/trade/refund/query?"));
            assertTrue(request.getPath().contains("refundRequestId=REFUND-REQUEST-1"));
            assertFalse(request.getPath().contains("uniqueRefundNo=REFUND-REQUEST-1"));
            assertTrue(request.getPath().contains("merchantNo=COLLECTION-1"));
            assertTrue(request.getPath().contains("parentMerchantNo="));
        }
    }

    @Test
    void testGetRefund_usesOriginalMerchantSnapshotAfterConfigChange() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json").addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"PROCESSING\"}}"));
            server.start();
            YeepayPayClientConfig config = config(keyPair());
            config.setServerUrl(server.url("/").toString());
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            client.getRefund("ORDER-1", "REFUND-REQUEST-1", "ORIGINAL-PARENT", "ORIGINAL-COLLECTOR");

            RecordedRequest request = server.takeRequest();
            assertEquals("ORIGINAL-PARENT", request.getRequestUrl().queryParameter("parentMerchantNo"));
            assertEquals("ORIGINAL-COLLECTOR", request.getRequestUrl().queryParameter("merchantNo"));
            assertEquals("REFUND-REQUEST-1", request.getRequestUrl().queryParameter("refundRequestId"));
            assertEquals("COLLECTION-1", config.getCollectionMerchantNo());
        }
    }

    @Test
    void testGetRefund_snapshotQueryDoesNotExposeSdkExceptionChain() {
        YeepayPayClient client = newClient();
        YopClient sdk = mock(YopClient.class);
        when(sdk.request(any())).thenThrow(new IllegalStateException("synthetic-sensitive-bank-account"));
        ReflectionTestUtils.setField(client, "yopClient", sdk);

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> client.getRefund("ORDER-1", "REFUND-1", "ORIGINAL-PARENT", "ORIGINAL-COLLECTOR"));

        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        assertFalse(trace.toString().contains("synthetic-sensitive-bank-account"),
                "快照查询的异常链必须屏蔽 SDK 原始响应或身份资料");
    }

    @Test
    void testUnifiedRefund_sendsOfficialDivideBackFields() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"PROCESSING\"}}"));
            server.start();
            KeyPair keyPair = keyPair();
            YeepayPayClientConfig config = config(keyPair);
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            client.unifiedRefund(new cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO()
                    .setOutTradeNo("ORDER-1").setUniqueOrderNo("YEEPAY-ORDER-1")
                    .setOutRefundNo("REFUND-REQUEST-1")
                    .setReason("用户退款").setPayPrice(1000).setRefundPrice(100)
                    .setNotifyUrl("https://merchant.example.test/refund")
                    .setFundAllocation(List.of(new cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundFundAllocationDTO()
                            .setLedgerNo("SUB-1").setDivideDetailNo("CHANNEL-DIVIDE-1").setAmount(100))));

            RecordedRequest request = server.takeRequest();
            String body = URLDecoder.decode(URLDecoder.decode(request.getBody().readUtf8(), StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
            assertTrue(body.contains("uniqueOrderNo=YEEPAY-ORDER-1"));
            assertFalse(body.contains("orderId=ORDER-1"),
                    "易宝退款在使用 uniqueOrderNo 时不能同时发送 orderId");
            assertTrue(body.contains("\"ledgerNo\":\"SUB-1\""));
            assertTrue(body.contains("\"divideDetailNo\":\"CHANNEL-DIVIDE-1\""));
            assertTrue(body.contains("\"amount\":1.00"));
            assertFalse(body.contains("divBackAccountType"));
        }
    }

    @Test
    void testUnifiedRefund_collapsesPendingDivideRefundsToSingleCollectorDeduction() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"PROCESSING\"}}"));
            server.start();
            YeepayPayClientConfig config = config(keyPair());
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            client.unifiedRefund(new cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO()
                    .setOutTradeNo("ORDER-1").setUniqueOrderNo("YEEPAY-ORDER-1")
                    .setOutRefundNo("REFUND-REQUEST-1")
                    .setParentMerchantNo("ORIGINAL-PARENT").setCollectionMerchantNo("ORIGINAL-COLLECTOR")
                    .setReason("用户退款").setPayPrice(1000).setRefundPrice(300)
                    .setNotifyUrl("https://merchant.example.test/refund")
                    .setFundAllocation(List.of(new cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundFundAllocationDTO()
                            .setLedgerNo("SUB-1").setDivBackAccountType("DIVIDE_ACCOUNT").setAmount(100),
                            new cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundFundAllocationDTO()
                                    .setLedgerNo("SUB-2").setDivBackAccountType("DIVIDE_ACCOUNT").setAmount(200))));

            RecordedRequest request = server.takeRequest();
            String body = URLDecoder.decode(URLDecoder.decode(request.getBody().readUtf8(), StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
            assertEquals("ORIGINAL-PARENT", formValue(body, "parentMerchantNo"));
            assertEquals("ORIGINAL-COLLECTOR", formValue(body, "merchantNo"));
            String detail = formValue(body, "divideBackDetail");
            assertEquals(List.of(Map.of("ledgerNo", "ORIGINAL-COLLECTOR", "divBackAccountType", "DIVIDE_ACCOUNT")),
                    JsonUtils.parseArray(detail, Map.class));
        }
    }

    @Test
    void testUnifiedRefund_mapsImmediateCancelledStatusToFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"CANCEL\","
                            + "\"message\":\"退款已取消\"}}"));
            server.start();
            KeyPair keyPair = keyPair();
            YeepayPayClientConfig config = config(keyPair);
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            var response = client.unifiedRefund(new cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundUnifiedReqDTO()
                    .setOutTradeNo("ORDER-1").setOutRefundNo("REFUND-1")
                    .setReason("用户退款").setPayPrice(1000).setRefundPrice(100)
                    .setNotifyUrl("https://merchant.example.test/refund"));

            assertEquals(20, response.getStatus());
        }
    }

    @Test
    void testParseOrderNotify_rejectsPlaintextCallbackWithoutDigitalEnvelope() {
        YeepayPayClient client = newClient();
        String body = "{\"orderId\":\"ORDER-1\",\"status\":\"SUCCESS\"}";
        assertThrows(RuntimeException.class, () -> client.parseOrderNotify(Map.of(), body, Map.of()));
    }

    @Test
    void testMergeCallbackFormParams_doesNotDuplicateBodyFields() {
        Map<String, List<String>> queryParams = new LinkedHashMap<>();
        queryParams.put("orderId", List.of("ORDER-1"));
        queryParams.put("channelId", List.of("7"));

        Map<String, List<String>> merged = YeepayPayClient.mergeCallbackFormParams(
                "orderId=ORDER-1&status=SUCCESS", queryParams);

        assertEquals(List.of("ORDER-1"), merged.get("orderId"));
        assertEquals(List.of("SUCCESS"), merged.get("status"));
        assertEquals(List.of("7"), merged.get("channelId"));
    }

    @Test
    void testApplyDivide_postsSignedFormWithMoneyAndStableRequest() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"SUCCESS\"}}"));
            server.start();
            KeyPair keyPair = keyPair();
            YeepayPayClientConfig config = config(keyPair);
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            client.applyDivide("ORDER-1", "YEEPAY-ORDER-1", "DIVIDE-1", List.of(
                    new LinkedHashMap<>(Map.of("ledgerNo", "SUB-1",
                            "divideDetailDesc", "供应商结算", "amount", "0.01"))));

            RecordedRequest request = server.takeRequest();
            assertEquals("/rest/v1.0/divide/apply", request.getPath());
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("orderId=ORDER-1"));
            assertFalse(body.contains("uniqueOrderNo=YEEPAY-ORDER-1"),
                    "divide/apply 的 orderId 与 uniqueOrderNo 必须二选一");
            assertTrue(body.contains("divideRequestId=DIVIDE-1"));
            assertTrue(body.contains("divideRule=AMOUNT"));
            String decodedBody = URLDecoder.decode(URLDecoder.decode(body, StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
            assertTrue(decodedBody.contains("\"ledgerNo\":\"SUB-1\""));
            assertTrue(decodedBody.contains("\"ledgerType\":\"MERCHANT2MERCHANT\""));
            assertTrue(decodedBody.contains("\"divideDetailDesc\":\"供应商结算\""));
            assertFalse(body.contains("notifyUrl="), "官方分账申请没有 notifyUrl 字段");
            assertFalse(decodedBody.contains("\"merchantNo\":\"SUB-1\""));
            assertFalse(decodedBody.contains("\"divideDetailNo\":\"DETAIL-1\""));
            assertFalse(body.contains("appKey=app-key"));
            assertFalse(body.contains("privateKey"));
        }
    }

    @Test
    void testGetOrder_usesOfficialGetEndpointAndMerchantScope() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"SUCCESS\","
                            + "\"orderId\":\"ORDER-1\",\"clearStatus\":\"SUCCESS\",\"parentMerchantNo\":\"PARENT-1\","
                            + "\"merchantNo\":\"COLLECTION-1\",\"orderAmount\":\"1.00\",\"uniqueOrderNo\":\"YOP1\","
                            + "\"paySuccessDate\":\"2026-10-10 12:00:00\"}}"));
            server.start();
            YeepayPayClientConfig config = config(keyPair()).setServerUrl(server.url("/").toString())
                    .setTimeoutSeconds(2).setConnectTimeoutSeconds(2).setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            assertEquals(10, client.getOrder("ORDER-1").getStatus());

            RecordedRequest request = server.takeRequest();
            assertEquals("GET", request.getMethod());
            assertTrue(request.getPath().startsWith("/rest/v1.0/trade/order/query?"));
            assertTrue(request.getPath().contains("orderId=ORDER-1"));
            assertTrue(request.getPath().contains("merchantNo=COLLECTION-1"));
            assertTrue(request.getPath().contains("parentMerchantNo="));
            assertEquals(0, request.getBodySize());
        }
    }

    @Test
    void testQueryClearing_usesOrderQueryClearStatus() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"SUCCESS\","
                            + "\"clearStatus\":\"PROCESSING\",\"orderId\":\"ORDER-1\",\"parentMerchantNo\":\"PARENT-1\","
                            + "\"merchantNo\":\"COLLECTION-1\",\"orderAmount\":\"1.00\",\"uniqueOrderNo\":\"YOP1\"}}"));
            server.start();
            YeepayPayClientConfig config = config(keyPair()).setServerUrl(server.url("/").toString())
                    .setTimeoutSeconds(2).setConnectTimeoutSeconds(2).setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            client.queryClearing("ORDER-1");

            RecordedRequest request = server.takeRequest();
            assertEquals("GET", request.getMethod());
            assertTrue(request.getPath().startsWith("/rest/v1.0/trade/order/query?"));
            assertFalse(request.getPath().contains("clearStatus"));
            assertTrue(request.getPath().contains("parentMerchantNo="));
        }
    }

    @Test
    void testQueryDivideAndDivideBack_useGetAndMerchantScope() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonSuccess());
            server.enqueue(jsonSuccess());
            server.start();
            YeepayPayClientConfig config = config(keyPair()).setServerUrl(server.url("/").toString())
                    .setTimeoutSeconds(2).setConnectTimeoutSeconds(2).setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            client.queryDivide("ORDER-1", "YEEPAY-ORDER-1", "DIVIDE-1");
            client.queryDivideBack("ORDER-1", "YEEPAY-ORDER-1", "BACK-1");

            RecordedRequest divide = server.takeRequest();
            assertEquals("GET", divide.getMethod());
            assertTrue(divide.getPath().startsWith("/rest/v1.0/divide/query?"));
            assertTrue(divide.getPath().contains("divideRequestId=DIVIDE-1"));
            assertTrue(divide.getPath().contains("orderId=ORDER-1"));
            assertFalse(divide.getPath().contains("uniqueOrderNo=YEEPAY-ORDER-1"),
                    "divide/query 的 orderId 与 uniqueOrderNo 必须二选一");
            assertTrue(divide.getPath().contains("merchantNo=COLLECTION-1"));
            assertTrue(divide.getPath().contains("parentMerchantNo="));

            RecordedRequest divideBack = server.takeRequest();
            assertEquals("GET", divideBack.getMethod());
            assertTrue(divideBack.getPath().startsWith("/rest/v1.0/divide/back/query?"));
            assertTrue(divideBack.getPath().contains("divideBackRequestId=BACK-1"));
            assertTrue(divideBack.getPath().contains("orderId=ORDER-1"));
            assertTrue(divideBack.getPath().contains("uniqueOrderNo=YEEPAY-ORDER-1"),
                    "divide/back/query 允许携带可选 uniqueOrderNo 辅助定位");
            assertTrue(divideBack.getPath().contains("merchantNo=COLLECTION-1"));
            assertTrue(divideBack.getPath().contains("parentMerchantNo="));
        }
    }

    private static MockResponse jsonSuccess() {
        return new MockResponse().setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .addHeader("Connection", "close")
                .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"SUCCESS\"}}");
    }

    private static String formValue(String decodedForm, String key) {
        for (String pair : decodedForm.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && key.equals(parts[0])) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("missing form field: " + key + " in " + decodedForm);
    }

    @Test
    void testApplyDivide_rejectsInternalDetailFieldNames() {
        YeepayPayClient client = newClient();

        assertThrows(IllegalArgumentException.class, () -> client.applyDivide("ORDER-1", "DIVIDE-1", List.of(
                new LinkedHashMap<>(Map.of("merchantNo", "SUB-1", "amount", "0.01",
                        "divideDetailNo", "DETAIL-1")))));
    }

    @Test
    void testApplyDivideBack_requiresMerchantOrderId() {
        YeepayPayClient client = newClient();

        assertThrows(IllegalArgumentException.class, () -> client.applyDivideBack(null,
                "YEEPAY-ORDER-1", "DIVIDE-1", "BACK-1", List.of(
                        new LinkedHashMap<>(Map.of("divideDetailNo", "DETAIL-1", "amount", "0.01")))));
    }

    @Test
    void testApplyDivide_usesOfficialYopAuthorizationInsteadOfCustomSignParameter() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"SUCCESS\"}}"));
            server.start();
            KeyPair keyPair = keyPair();
            YeepayPayClientConfig config = config(keyPair);
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            client.applyDivide("ORDER-1", "DIVIDE-1", List.of(
                    new LinkedHashMap<>(Map.of("ledgerNo", "SUB-1",
                            "divideDetailDesc", "供应商结算", "amount", "0.01"))));

            RecordedRequest request = server.takeRequest();
            assertTrue(request.getHeader("Authorization") != null,
                    "官方 SDK 应通过 Authorization 头传递 YOP 签名");
            assertTrue(request.getHeader("Authorization").contains("YOP-RSA2048-SHA256"));
            assertFalse(request.getBody().readUtf8().contains("sign="),
                    "自研 canonical sign 不应再作为业务参数发送");
        }
    }

    @Test
    void testApplyDivideBack_sendsOnlyOfficialDetailFields() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"SUCCESS\"}}"));
            server.start();
            KeyPair keyPair = keyPair();
            YeepayPayClientConfig config = config(keyPair);
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(0);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            client.applyDivideBack("ORDER-1", "DIVIDE-1", "BACK-1", List.of(
                    new LinkedHashMap<>(Map.of("ledgerNo", "SUB-1", "divideDetailNo", "DETAIL-1",
                            "amount", "1.00", "divBackAccountType", "DIVIDE_ACCOUNT",
                            "divideBackReason", "用户退款"))));

            RecordedRequest request = server.takeRequest();
            assertEquals("/rest/v1.0/divide/back", request.getPath());
            String body = URLDecoder.decode(URLDecoder.decode(request.getBody().readUtf8(), StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
            assertTrue(body.contains("\"amount\":1.00"));
            assertTrue(body.contains("\"divideDetailNo\":\"DETAIL-1\""));
            assertTrue(body.contains("\"divideBackReason\":\"用户退款\""));
            assertFalse(body.contains("ledgerNo"));
            assertFalse(body.contains("divBackAccountType"));
            assertFalse(body.contains("notifyUrl"));
        }
    }

    @Test
    void testParseYeepayRefundNotify_mapsOfficialTerminalAndSuspendedStatuses() {
        YeepayPayClient client = newClient();

        Map<String, Object> cancelled = new LinkedHashMap<>(Map.of(
                "status", "CANCEL", "refundRequestId", "REFUND-CANCEL"));
        assertEquals(20, client.parseYeepayRefundNotify(cancelled).getStatus());

        Map<String, Object> suspended = new LinkedHashMap<>(Map.of(
                "status", "SUSPEND", "refundRequestId", "REFUND-SUSPEND"));
        assertEquals(0, client.parseYeepayRefundNotify(suspended).getStatus());
    }

    @Test
    void testApplyDivide_doesNotResubmitAfterResponseParsingFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("not-json"));
            server.start();
            KeyPair keyPair = keyPair();
            YeepayPayClientConfig config = config(keyPair);
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(3);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            assertThrows(RuntimeException.class, () -> client.applyDivide("ORDER-1", "DIVIDE-1", List.of(
                    new LinkedHashMap<>(Map.of("ledgerNo", "SUB-1", "ledgerType", "MERCHANT2MERCHANT",
                            "divideDetailDesc", "供应商结算", "amount", "0.01")))));
            assertTrue(server.takeRequest(1, TimeUnit.SECONDS) != null,
                    "分账请求应至少到达一次");
            assertEquals(1, server.getRequestCount(), "分账响应解析失败不能再次提交同一个分账请求");
            assertTrue(server.takeRequest(250, TimeUnit.MILLISECONDS) == null,
                    "分账失败后不应出现业务层重复请求");
        }
    }

    @Test
    void testApplyDivide_doesNotUseOfficialSdkTransportRetryOnServerError() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(500)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"error\":\"temporary\"}"));
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Connection", "close")
                    .setBody("{\"result\":{\"code\":\"OPR00000\",\"status\":\"SUCCESS\"}}"));
            server.start();
            KeyPair keyPair = keyPair();
            YeepayPayClientConfig config = config(keyPair);
            config.setServerUrl(server.url("/").toString());
            config.setTimeoutSeconds(2);
            config.setConnectTimeoutSeconds(2);
            config.setMaxRetries(3);
            useIsolatedSdkSpace(config, server);
            YeepayPayClient client = new YeepayPayClient(7L, config);
            client.init();

            assertThrows(RuntimeException.class, () -> client.applyDivide("ORDER-1", "DIVIDE-1", List.of(
                    new LinkedHashMap<>(Map.of("ledgerNo", "SUB-1", "ledgerType", "MERCHANT2MERCHANT",
                            "divideDetailDesc", "供应商结算", "amount", "0.01")))));
            assertTrue(server.takeRequest(1, TimeUnit.SECONDS) != null);
            assertEquals(1, server.getRequestCount(),
                    "官方 SDK 默认重试必须关闭，状态变更请求不能在 5xx 后再次提交");
        }
    }

    @Test
    void testConfig_doesNotSerializePrivateKey() {
        KeyPair keyPair = keyPair();
        String json = JsonUtils.toJsonString(config(keyPair));
        assertFalse(json.contains("PRIVATE KEY"));
        assertFalse(json.contains("privateKey"));
    }

    @Test
    void testConfig_resolvesPrivateKeyReferenceFromSystemProperty() {
        KeyPair keyPair = keyPair();
        String key = pem(keyPair, true);
        String propertyName = "yeepay.test.private-key";
        System.setProperty(propertyName, key);
        try {
            YeepayPayClientConfig config = config(keyPair).setPrivateKey(null)
                    .setPrivateKeyRef("sys://" + propertyName);
            assertEquals(key, config.getRuntimePrivateKey());
            config.validate(validator());
        } finally {
            System.clearProperty(propertyName);
        }
    }

    @Test
    void testConfig_resolvesUnprefixedPrivateKeyReferenceFromSystemProperty() {
        KeyPair keyPair = keyPair();
        String key = pem(keyPair, true);
        String propertyName = "yeepay.test.private-key-direct";
        System.setProperty(propertyName, key);
        try {
            YeepayPayClientConfig config = config(keyPair).setPrivateKey(null)
                    .setPrivateKeyRef(propertyName);
            assertEquals(key, config.getRuntimePrivateKey());
        } finally {
            System.clearProperty(propertyName);
        }
    }

    @Test
    void testConfig_rejectsUnresolvedExplicitPrivateKeyReference() {
        YeepayPayClientConfig config = config(keyPair()).setPrivateKey(null)
                .setPrivateKeyRef("env://YEEPAY_TEST_MISSING_PRIVATE_KEY");
        assertThrows(IllegalArgumentException.class, () -> config.validate(validator()));
        assertThrows(IllegalStateException.class, config::getRuntimePrivateKey);
    }

    @Test
    void testConfig_usesDefaultRsaPlatformCertificateSerial() {
        YeepayPayClientConfig config = config(keyPair());
        assertEquals(com.yeepay.yop.sdk.YopConstants.YOP_RSA_PLATFORM_CERT_DEFAULT_SERIAL_NO,
                config.getEffectivePlatformCertSerialNo());
    }

    @Test
    void testConfig_rejectsBothPublicKeyAndCertificate() {
        YeepayPayClientConfig config = config(keyPair()).setPublicCertificate("certificate");
        assertThrows(IllegalArgumentException.class, () -> config.validate(validator()));
    }

    @Test
    void testConfig_rejectsMissingPlatformParentMerchant() {
        YeepayPayClientConfig config = config(keyPair()).setParentMerchantNo("  ");
        assertThrows(RuntimeException.class, () -> config.validate(validator()));
    }

    @Test
    void testConfig_rejectsRealtimeDivideForFirstPhaseAsyncDivideFlow() {
        YeepayPayClientConfig config = config(keyPair())
                .setFundProcessType(YeepayPayClientConfig.FUND_PROCESS_TYPE_REAL_TIME_DIVIDE);
        assertThrows(IllegalArgumentException.class, () -> config.validate(validator()));
    }

    @Test
    void testConfig_doesNotPersistAppKeyOrPlatformVerificationMaterials() {
        YeepayPayClientConfig config = config(keyPair()).setPublicCertificate("certificate-material");
        Map<String, Object> persisted = JsonUtils.parseObject(JsonUtils.toJsonString(config), Map.class);
        assertFalse(persisted.containsKey("appKey"));
        assertFalse(persisted.containsKey("publicKey"));
        assertFalse(persisted.containsKey("publicCertificate"));
        assertFalse(persisted.containsKey("certificate"));
        assertFalse(persisted.containsKey("verificationKey"));
    }

    @Test
    void testConfig_resolvesAppKeyAndPlatformPublicKeyFromPersistedReferences() throws Exception {
        KeyPair keyPair = keyPair();
        String appKeyProperty = "yeepay.test.runtime-app-key";
        String publicKeyProperty = "yeepay.test.runtime-public-key";
        String privateKeyProperty = "yeepay.test.runtime-private-key";
        System.setProperty(appKeyProperty, "runtime-app-key");
        System.setProperty(publicKeyProperty, pem(keyPair, false));
        System.setProperty(privateKeyProperty, pem(keyPair, true));
        try {
            YeepayPayClientConfig config = JsonUtils.parseObject("{\"@class\":\""
                    + YeepayPayClientConfig.class.getName() + "\",\"appKeyRef\":\"sys://"
                    + appKeyProperty + "\",\"publicKeyRef\":\"sys://" + publicKeyProperty + "\"}",
                    YeepayPayClientConfig.class);
            assertEquals("runtime-app-key", config.getAppKey());
            assertEquals(pem(keyPair, false), config.getVerificationKey());
            config.setPrivateKeyRef("sys://" + privateKeyProperty);
            config.setAppId("wx-platform");
            config.setParentMerchantNo("PARENT-1");
            config.setCollectionMerchantNo("COLLECTION-1");
            config.validate(validator());
            assertFalse(config.toString().contains("runtime-app-key"));
            assertFalse(JsonUtils.toJsonString(config).contains("PUBLIC KEY"));
            try (MockWebServer server = new MockWebServer()) {
                server.enqueue(jsonSuccess());
                server.start();
                config.setServerUrl(server.url("/").toString());
                config.setTimeoutSeconds(2).setConnectTimeoutSeconds(2).setMaxRetries(0);
                config.setSkipResponseVerifySign(true);
                useIsolatedSdkSpace(config, server);
                YeepayPayClient client = new YeepayPayClient(7L, config);
                client.init();
                client.applyDivide("ORDER-REFS", "DIVIDE-REFS", List.of(
                        Map.of("ledgerNo", "SUB-REFS", "divideDetailDesc", "供应商结算", "amount", "0.01")));
                RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
                assertTrue(request != null);
                assertTrue(request.getHeader("Authorization").contains("YOP-RSA2048-SHA256"),
                        "只持久化引用的配置仍必须经官方 SDK 生成请求签名");
            }
        } finally {
            System.clearProperty(appKeyProperty);
            System.clearProperty(publicKeyProperty);
            System.clearProperty(privateKeyProperty);
        }
    }

    @Test
    void testConfig_rejectsUnresolvedPlatformCredentialReferences() {
        YeepayPayClientConfig config = JsonUtils.parseObject("{\"@class\":\""
                + YeepayPayClientConfig.class.getName()
                + "\",\"appKeyRef\":\"sys://yeepay.test.missing-app-key\"}", YeepayPayClientConfig.class);
        assertThrows(IllegalStateException.class, config::getAppKey);
    }

    @Test
    void testConfig_equalityDoesNotResolveUnconfiguredCredentials() {
        YeepayPayClientConfig first = new YeepayPayClientConfig();
        YeepayPayClientConfig second = new YeepayPayClientConfig();

        assertEquals(first, second);
        assertEquals(second, first);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void testConfig_equalityDoesNotResolveMissingCredentialReferences() {
        YeepayPayClientConfig first = new YeepayPayClientConfig()
                .setAppKeyRef("sys://yeepay.test.equality-missing-app-key")
                .setPrivateKeyRef("sys://yeepay.test.equality-missing-private-key")
                .setPublicKeyRef("sys://yeepay.test.equality-missing-public-key")
                .setPublicCertificateRef("sys://yeepay.test.equality-missing-certificate");
        YeepayPayClientConfig second = new YeepayPayClientConfig()
                .setAppKeyRef("sys://yeepay.test.equality-missing-app-key")
                .setPrivateKeyRef("sys://yeepay.test.equality-missing-private-key")
                .setPublicKeyRef("sys://yeepay.test.equality-missing-public-key")
                .setPublicCertificateRef("sys://yeepay.test.equality-missing-certificate");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertThrows(IllegalStateException.class, first::getAppKey,
                "普通配置比较不能替代真正使用凭证时的有效性校验");
    }

    @Test
    void testConfig_equalityDetectsChangedReferencesWithSameRuntimeValue() {
        String firstReference = "yeepay.test.equality-first-app-key";
        String secondReference = "yeepay.test.equality-second-app-key";
        System.setProperty(firstReference, "same-runtime-app-key");
        System.setProperty(secondReference, "same-runtime-app-key");
        try {
            YeepayPayClientConfig first = new YeepayPayClientConfig().setAppKeyRef("sys://" + firstReference);
            YeepayPayClientConfig second = new YeepayPayClientConfig().setAppKeyRef("sys://" + secondReference);

            assertFalse(first.equals(second), "配置引用变化仍需触发客户端配置更新");
            assertFalse(second.equals(first));
        } finally {
            System.clearProperty(firstReference);
            System.clearProperty(secondReference);
        }
    }

    @Test
    void testConfig_resolvesCertificateReferenceAndRejectsMissingExplicitPublicKey() {
        String propertyName = "yeepay.test.runtime-certificate";
        System.setProperty(propertyName, "certificate-material");
        try {
            YeepayPayClientConfig config = config(keyPair()).setPublicKey(null)
                    .setPublicCertificateRef("sys://" + propertyName);
            assertEquals("certificate-material", config.getVerificationKey());
            config.validate(validator());
            assertFalse(JsonUtils.toJsonString(config).contains("certificate-material"));
            config.setPublicKeyRef("sys://yeepay.test.missing-public-key");
            assertThrows(IllegalStateException.class, config::getVerificationKey,
                    "显式配置的公钥引用无法解析时不能静默退回证书");
        } finally {
            System.clearProperty(propertyName);
        }
    }

    @Test
    void testCredentialProviders_doNotIncludeAppKeyInFailureMessages() {
        String missingAppKey = "missing-sensitive-app-key";
        RuntimeException clientError = assertThrows(RuntimeException.class,
                () -> YeepayCredentialsProvider.INSTANCE.getCredentials(
                        "test-provider", "test-env", missingAppKey, "RSA2048"));
        RuntimeException platformError = assertThrows(RuntimeException.class,
                () -> YeepayPlatformCredentialsProvider.INSTANCE.getCredentials(
                        "test-provider", "test-env", missingAppKey, "missing-serial", null));
        assertFalse(clientError.getMessage().contains(missingAppKey));
        assertFalse(platformError.getMessage().contains(missingAppKey));
    }

    @Test
    void testConfig_rejectsSm2WithoutCertificateOrSerial() {
        YeepayPayClientConfig config = config(keyPair()).setSigningCertType("SM2");
        assertThrows(IllegalArgumentException.class, () -> config.validate(validator()));
    }

    private static YeepayPayClient newClient() {
        KeyPair keyPair = keyPair();
        return newClient(keyPair);
    }

    private static Validator validator() {
        return Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static Map<String, Object> validMerchantMaterials() {
        Map<String, Object> materials = new LinkedHashMap<>();
        materials.put("merchantId", "platform-merchant-id");
        materials.put("merchantSubjectInfo", Map.of("merchantType", "ENTERPRISE"));
        materials.put("merchantCorporationInfo", Map.of("legalPersonName", "张三"));
        materials.put("merchantContactInfo", Map.of("contactName", "李四"));
        materials.put("businessAddressInfo", Map.of("province", "北京市"));
        materials.put("functionServiceQualificationInfo", Map.of("shareScene", "MINI_PROGRAM"));
        return materials;
    }

    private static YeepayPayClient newClient(KeyPair keyPair) {
        YeepayPayClient client = new YeepayPayClient(7L, config(keyPair));
        client.init();
        return client;
    }

    private static YeepayPayClientConfig config(KeyPair keyPair) {
        YeepayPayClientConfig config = new YeepayPayClientConfig();
        config.setAppKey("app-key");
        config.setAppId("wx-platform");
        config.setCollectionMerchantNo("COLLECTION-1");
        config.setParentMerchantNo("PARENT-1");
        config.setFullAmountDivideConfirmed(true);
        config.setFeeBearerMode("PLATFORM_FEE_ACCOUNT");
        config.setPrivateKey(pem(keyPair, true));
        config.setPublicKey(pem(keyPair, false));
        config.setSkipResponseVerifySign(true);
        return config;
    }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void useIsolatedSdkSpace(YeepayPayClientConfig config, MockWebServer server) {
        // YOP keeps the inner basic client cache by provider/env. A unique test space prevents
        // one dynamic MockWebServer endpoint from being reused by another test.
        config.setSdkProvider("yeepay-test");
        config.setSdkEnv("mock-" + server.getPort());
    }

    private static String pem(KeyPair keyPair, boolean privateKey) {
        byte[] encoded = privateKey ? keyPair.getPrivate().getEncoded() : keyPair.getPublic().getEncoded();
        String label = privateKey ? "PRIVATE KEY" : "PUBLIC KEY";
        return "-----BEGIN " + label + "-----\n"
                + Base64.getEncoder().encodeToString(encoded)
                + "\n-----END " + label + "-----";
    }
}
