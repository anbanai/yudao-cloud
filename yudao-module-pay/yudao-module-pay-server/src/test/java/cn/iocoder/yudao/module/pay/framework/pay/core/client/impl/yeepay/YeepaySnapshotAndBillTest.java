package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay;

import com.yeepay.yop.sdk.service.common.YopClient;
import com.yeepay.yop.sdk.service.common.request.YopRequest;
import com.yeepay.yop.sdk.service.common.response.YopResponse;
import com.yeepay.yop.sdk.model.yos.YosDownloadResponse;
import com.yeepay.yop.sdk.model.yos.YosDownloadInputStream;
import com.yeepay.yop.sdk.http.YopHttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class YeepaySnapshotAndBillTest {
    @Test void configurationDiagnostics_neverExposeRuntimeSecretMaterial() {
        var config = new YeepayPayClientConfig().setAppKey("public-app-id")
                .setPrivateKey("runtime-private-key-secret").setSm4CallbackKey("runtime-sm4-secret");
        assertFalse(config.toString().contains("runtime-private-key-secret"));
        assertFalse(config.toString().contains("runtime-sm4-secret"));
    }
    private YeepayPayClient client(YopClient sdk) throws Exception {
        var keys = KeyPairGenerator.getInstance("RSA"); keys.initialize(2048); var pair = keys.generateKeyPair();
        var config = new YeepayPayClientConfig().setAppKey("test-app").setAppId("wx-platform")
                .setParentMerchantNo("NEW-PARENT").setCollectionMerchantNo("NEW-COLLECT")
                .setPrivateKey(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()))
                .setPublicKey(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()))
                .setMaxRetries(0).setRateLimitPerSecond(100);
        var client = new YeepayPayClient(1L, config); client.init();
        ReflectionTestUtils.setField(client, "yopClient", sdk); return client;
    }
    @Test void getOrder_usesOriginalMerchantsAfterConfigurationChange() throws Exception {
        YopClient sdk = mock(YopClient.class); YopResponse response = new YopResponse();
        response.setResult(Map.of("code", "OPR00000", "status", "SUCCESS", "orderId", "PAY1",
                "parentMerchantNo", "OLD-PARENT", "merchantNo", "OLD-COLLECT", "orderAmount", "1.00",
                "uniqueOrderNo", "YOP1", "paySuccessDate", "2026-10-10 12:00:00"));
        when(sdk.request(any(YopRequest.class))).thenReturn(response);
        var client = client(sdk);
        var method = YeepayPayClient.class.getMethod("getOrder", String.class, String.class, String.class);
        method.invoke(client, "PAY1", "OLD-PARENT", "OLD-COLLECT");
        var request = ArgumentCaptor.forClass(YopRequest.class); verify(sdk).request(request.capture());
        assertEquals(java.util.List.of("OLD-PARENT"), request.getValue().getParameters().get("parentMerchantNo").stream().toList());
        assertEquals(java.util.List.of("OLD-COLLECT"), request.getValue().getParameters().get("merchantNo").stream().toList());
    }
    @Test void getOrder_rejectsWrongAmountAndMissingSuccessfulIdentity() throws Exception {
        YopClient sdk = mock(YopClient.class); YopResponse response = new YopResponse();
        var payload = new java.util.LinkedHashMap<String, Object>(Map.of("code", "OPR00000", "status", "SUCCESS", "orderId", "PAY1",
                "parentMerchantNo", "OLD-PARENT", "merchantNo", "OLD-COLLECT", "orderAmount", "2.00",
                "uniqueOrderNo", "YOP1", "paySuccessDate", "2026-10-10 12:00:00"));
        response.setResult(payload); when(sdk.request(any(YopRequest.class))).thenReturn(response);
        var client = client(sdk);
        var method = YeepayPayClient.class.getMethod("getOrder", String.class, String.class, String.class, Integer.class);
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(client, "PAY1", "OLD-PARENT", "OLD-COLLECT", 100));
        payload.put("orderAmount", "1.00"); payload.remove("merchantNo"); response.setResult(payload);
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(client, "PAY1", "OLD-PARENT", "OLD-COLLECT", 100));
        payload.put("merchantNo", "OLD-COLLECT"); payload.remove("paySuccessDate"); response.setResult(payload);
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> method.invoke(client, "PAY1", "OLD-PARENT", "OLD-COLLECT", 100));
    }
    @Test void download_usesOfficialSdkAndHistoricalPlatformScopeAndClosesStream() throws Exception {
        YopClient sdk = mock(YopClient.class); YosDownloadResponse response = new YosDownloadResponse();
        var stream = spy(new ByteArrayInputStream("账单".getBytes(StandardCharsets.UTF_8)));
        var http = mock(YopHttpResponse.class); when(http.getContent()).thenReturn(stream);
        response.setResult(new YosDownloadInputStream(http)); when(sdk.download(any(YopRequest.class))).thenReturn(response);
        var client = client(sdk); LocalDate date = LocalDate.now().minusDays(1);
        var method = YeepayPayClient.class.getMethod("downloadDailyBill", LocalDate.class, boolean.class, String.class);
        assertArrayEquals("账单".getBytes(StandardCharsets.UTF_8), (byte[]) method.invoke(client, date, false, "OLD-PARENT"));
        var request = ArgumentCaptor.forClass(YopRequest.class); verify(sdk).download(request.capture());
        assertEquals("/yos/v1.0/std/bill/tradedaydownload", request.getValue().getApiUri());
        assertEquals("GET", request.getValue().getHttpMethod());
        assertTrue(request.getValue().getParameters().containsEntry("merchantNo", "OLD-PARENT"));
        assertTrue(request.getValue().getParameters().containsEntry("dataType", "platform"));
        verify(stream).close();
    }
    @Test void queryClearing_rejectsIncompleteOrMismatchedIdentityAndAmount() throws Exception {
        YopClient sdk = mock(YopClient.class); YopResponse response = new YopResponse();
        var original = new java.util.LinkedHashMap<String, Object>(Map.of("code", "OPR00000", "status", "SUCCESS",
                "clearStatus", "SUCCESS", "orderId", "PAY1", "parentMerchantNo", "OLD-PARENT",
                "merchantNo", "OLD-COLLECT", "orderAmount", "1.00", "uniqueOrderNo", "YOP1"));
        response.setResult(original); when(sdk.request(any(YopRequest.class))).thenReturn(response);
        var client = client(sdk);
        assertEquals("SUCCESS", client.queryClearing("PAY1", "YOP1", "OLD-PARENT", "OLD-COLLECT").get("clearStatus"));
        for (String field : java.util.List.of("orderId", "parentMerchantNo", "merchantNo", "orderAmount", "uniqueOrderNo")) {
            var malformed = new java.util.LinkedHashMap<>(original); malformed.remove(field); response.setResult(malformed);
            assertThrows(IllegalArgumentException.class, () -> client.queryClearing("PAY1", "YOP1", "OLD-PARENT", "OLD-COLLECT"), field);
        }
        for (String field : java.util.List.of("orderId", "parentMerchantNo", "merchantNo", "uniqueOrderNo")) {
            var malformed = new java.util.LinkedHashMap<>(original); malformed.put(field, "OTHER"); response.setResult(malformed);
            assertThrows(IllegalArgumentException.class, () -> client.queryClearing("PAY1", "YOP1", "OLD-PARENT", "OLD-COLLECT"), field);
        }
        for (String amount : java.util.List.of("0.00", "-1.00", "1.001", "21474836.48")) {
            var malformed = new java.util.LinkedHashMap<>(original); malformed.put("orderAmount", amount); response.setResult(malformed);
            assertThrows(IllegalArgumentException.class, () -> client.queryClearing("PAY1", "YOP1", "OLD-PARENT", "OLD-COLLECT"), amount);
        }
    }
}
