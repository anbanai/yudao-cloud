package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay;

import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import com.yeepay.yop.sdk.service.common.YopClient;
import com.yeepay.yop.sdk.service.common.response.YopResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YeepayRefundQueryBindingTest {

    private static KeyPair keyPair;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void testGetRefund_completeSuccessMatchesOriginalReferences() {
        var result = query(success());

        assertEquals(PayRefundStatusEnum.SUCCESS.getStatus(), result.getStatus());
        assertEquals("REFUND-1", result.getOutRefundNo());
        assertEquals("YOP-REFUND-1", result.getChannelRefundNo());
    }

    @Test
    void testGetRefund_completeSuccessMatchesSavedRefundAmount() {
        var result = client(success()).getRefund("ORDER-1", "REFUND-1",
                "ORIGINAL-PARENT", "ORIGINAL-COLLECTOR", 100);

        assertEquals(PayRefundStatusEnum.SUCCESS.getStatus(), result.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.99", "1.01", "2.00"})
    void testGetRefund_successWithDifferentRefundAmountIsRejected(String amount) {
        Map<String, Object> response = success();
        response.put("refundAmount", amount);

        assertThrows(IllegalArgumentException.class, () -> client(response).getRefund("ORDER-1", "REFUND-1",
                "ORIGINAL-PARENT", "ORIGINAL-COLLECTOR", 100));
    }

    @ParameterizedTest
    @ValueSource(strings = {"refundRequestId", "orderId", "parentMerchantNo", "merchantNo"})
    void testGetRefund_conflictingSuccessIdentityIsRejected(String field) {
        Map<String, Object> response = success();
        response.put(field, "OTHER-IDENTITY");

        assertThrows(IllegalArgumentException.class, () -> query(response));
    }

    @ParameterizedTest
    @ValueSource(strings = {"refundRequestId", "orderId", "parentMerchantNo", "merchantNo", "refundAmount"})
    void testGetRefund_sparseSuccessCannotBeInferredFromQuery(String field) {
        Map<String, Object> response = success();
        response.remove(field);

        assertThrows(IllegalArgumentException.class, () -> query(response));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1.00", "0.001", "NaN", "not-money", "21474836.48"})
    void testGetRefund_invalidSuccessAmountIsRejected(String amount) {
        Map<String, Object> response = success();
        response.put("refundAmount", amount);

        assertThrows(IllegalArgumentException.class, () -> query(response));
    }

    @Test
    void testGetRefund_sparseProcessingRemainsWaiting() {
        var result = query(Map.of("code", "OPR00000", "status", "PROCESSING"));

        assertEquals(PayRefundStatusEnum.WAITING.getStatus(), result.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"refundRequestId", "orderId", "parentMerchantNo", "merchantNo"})
    void testGetRefund_processingWithExplicitIdentityConflictIsRejected(String field) {
        Map<String, Object> response = new LinkedHashMap<>(Map.of("code", "OPR00000", "status", "PROCESSING"));
        response.put(field, "OTHER-IDENTITY");

        assertThrows(IllegalArgumentException.class, () -> query(response));
    }

    @ParameterizedTest
    @ValueSource(strings = {"refundRequestId", "orderId", "parentMerchantNo", "merchantNo"})
    void testGetRefund_failureWithExplicitIdentityConflictIsRejected(String field) {
        Map<String, Object> response = success();
        response.put("status", "FAIL");
        response.put(field, "OTHER-IDENTITY");

        assertThrows(IllegalArgumentException.class, () -> query(response));
    }

    private PayRefundRespDTO query(Map<String, Object> response) {
        return client(response).getRefund("ORDER-1", "REFUND-1", "ORIGINAL-PARENT", "ORIGINAL-COLLECTOR");
    }

    private YeepayPayClient client(Map<String, Object> response) {
        YeepayPayClientConfig config = new YeepayPayClientConfig();
        config.setAppKey("test-app-key");
        config.setAppId("wx-test");
        config.setParentMerchantNo("NEW-PARENT");
        config.setCollectionMerchantNo("NEW-COLLECTOR");
        config.setPrivateKey(Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()));
        config.setPublicKey(Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        config.setMaxRetries(0);
        config.setSkipResponseVerifySign(true);
        YopClient sdk = mock(YopClient.class);
        YopResponse sdkResponse = mock(YopResponse.class);
        when(sdkResponse.getResult()).thenReturn(response);
        when(sdk.request(any())).thenReturn(sdkResponse);
        YeepayPayClient client = new YeepayPayClient(10L, config);
        ReflectionTestUtils.setField(client, "yopClient", sdk);
        return client;
    }

    private Map<String, Object> success() {
        return new LinkedHashMap<>(Map.of("code", "OPR00000", "status", "SUCCESS",
                "refundRequestId", "REFUND-1", "orderId", "ORDER-1",
                "parentMerchantNo", "ORIGINAL-PARENT", "merchantNo", "ORIGINAL-COLLECTOR",
                "refundAmount", "1.00", "uniqueRefundNo", "YOP-REFUND-1"));
    }

}
