package cn.iocoder.yudao.module.pay.service.payfund;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.PayYeepayEventDO;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay.PayYeepayEventMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YeepayCallbackArchiveTest {
    private Object codec(MockEnvironment env) throws Exception {
        return Class.forName("cn.iocoder.yudao.module.pay.service.payfund.YeepayCallbackArchive")
                .getConstructor(Environment.class).newInstance(env);
    }
    private MockEnvironment environment() {
        return new MockEnvironment().withProperty("yudao.pay.yeepay.callback-archive.active-key-id", "v1")
                .withProperty("yudao.pay.yeepay.callback-archive.keys.v1", Base64.getEncoder().encodeToString(new byte[32]));
    }
    private String encrypt(Object codec, String plaintext, String binding) throws Exception {
        return (String) codec.getClass().getMethod("encrypt", String.class, String.class).invoke(codec, plaintext, binding);
    }
    private String decrypt(Object codec, String ciphertext, String binding) throws Exception {
        return (String) codec.getClass().getMethod("decrypt", String.class, String.class).invoke(codec, ciphertext, binding);
    }
    @Test void encrypt_randomNonceAndExactRoundTripWithoutPlaintext() throws Exception {
        Object codec = codec(environment()); String original = "{\"body\":\"signed original 6222000000000000\"}";
        String first = encrypt(codec, original, "tenant1|channel7|payment|event1");
        String second = encrypt(codec, original, "tenant1|channel7|payment|event1");
        assertNotEquals(first, second); assertFalse(first.contains("6222000000000000"));
        assertEquals(original, decrypt(codec, first, "tenant1|channel7|payment|event1"));
    }
    @Test void decrypt_rejectsTamperingAndCrossTenantBinding() throws Exception {
        Object codec = codec(environment()); String ciphertext = encrypt(codec, "original", "tenant1|event1");
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> decrypt(codec, ciphertext, "tenant2|event1"));
        int split = ciphertext.lastIndexOf(':'); byte[] payload = Base64.getDecoder().decode(ciphertext.substring(split + 1));
        payload[0] ^= 1; String altered = ciphertext.substring(0, split + 1) + Base64.getEncoder().encodeToString(payload);
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> decrypt(codec, altered, "tenant1|event1"));
    }
    @Test void encrypt_missingKeyFailsClosed() throws Exception {
        Object codec = codec(new MockEnvironment());
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> encrypt(codec, "original", "tenant1|event1"));
    }

    @Test void merchantCallback_requestNumberDedupeIgnoresEnvelopeAndDistinguishesProgress() {
        String reviewing = PayFundServiceImpl.eventExternalId(Map.of("requestNo", "MERCHANT-REQUEST-1",
                "status", "REVIEWING", "timestamp", "20261010010101"));
        assertEquals(reviewing, PayFundServiceImpl.eventExternalId(Map.of("requestNo", "MERCHANT-REQUEST-1",
                "status", "REVIEWING", "timestamp", "20261010010102")));
        assertNotEquals(reviewing, PayFundServiceImpl.eventExternalId(Map.of("requestNo", "MERCHANT-REQUEST-1",
                "status", "COMPLETED", "merchantNo", "MERCHANT1")));
    }

    @Test void accept_sameProviderEventIdOnTwoTrustedChannelsIsNotDropped() {
        PayFundServiceImpl service = new PayFundServiceImpl();
        PayYeepayEventMapper mapper = mock(PayYeepayEventMapper.class);
        ReflectionTestUtils.setField(service, "yeepayEventMapper", mapper);
        Map<String, PayYeepayEventDO> records = new java.util.HashMap<>();
        when(mapper.selectByEventId(anyString())).thenAnswer(invocation -> records.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            PayYeepayEventDO event = invocation.getArgument(0);
            records.put(event.getEventId(), event);
            return 1;
        }).when(mapper).insert(any(PayYeepayEventDO.class));
        assertTrue(service.acceptYeepayEvent("payment", Map.of("eventId", "provider-event-1",
                "orderId", "P1", "_trustedChannelId", 7L)));
        assertTrue(service.acceptYeepayEvent("payment", Map.of("eventId", "provider-event-1",
                "orderId", "P2", "_trustedChannelId", 8L)));
        assertEquals(2, records.size());
        assertFalse(service.acceptYeepayEvent("payment", Map.of("eventId", "provider-event-1",
                "orderId", "P1", "_trustedChannelId", 7L)));
    }

    @Test void decrypt_retainedOldKeySupportsRotation() throws Exception {
        MockEnvironment env = environment(); Object codec = codec(env);
        String previous = encrypt(codec, "old callback", "tenant1|event1");
        byte[] newKey = new byte[32]; newKey[0] = 1;
        env.withProperty("yudao.pay.yeepay.callback-archive.active-key-id", "v2")
                .withProperty("yudao.pay.yeepay.callback-archive.keys.v2", Base64.getEncoder().encodeToString(newKey));
        assertEquals("old callback", decrypt(codec, previous, "tenant1|event1"));
        assertTrue(encrypt(codec, "new callback", "tenant1|event1").startsWith("YPA:1:v2:"));
    }

    @SuppressWarnings("unchecked")
    @Test void accept_originalArchivedOnceWithBoundIdentityAndNeverSerialized() throws Exception {
        PayFundServiceImpl service = new PayFundServiceImpl();
        PayYeepayEventMapper mapper = mock(PayYeepayEventMapper.class);
        ObjectProvider<YeepayCallbackArchive> provider = mock(ObjectProvider.class);
        YeepayCallbackArchive archive = new YeepayCallbackArchive(environment());
        ReflectionTestUtils.setField(service, "yeepayEventMapper", mapper);
        ReflectionTestUtils.setField(service, "callbackArchiveProvider", provider);
        when(provider.getIfAvailable()).thenReturn(archive);
        Map<String, Object> payload = Map.of("eventId", "event1", "orderId", "P1", "_trustedChannelId", 7L);
        String original = "{\"body\":\"signed sensitive original\"}";
        TenantContextHolder.setTenantId(42L);
        try {
            assertTrue(service.acceptYeepayEvent("payment", payload, original));
            ArgumentCaptor<PayYeepayEventDO> event = ArgumentCaptor.forClass(PayYeepayEventDO.class);
            verify(mapper).insert(event.capture());
            PayYeepayEventDO stored = event.getValue();
            assertEquals(original, archive.decrypt(stored.getEncryptedOriginal(), "42|7|payment|" + stored.getEventId()));
            assertEquals("sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(stored.getEncryptedOriginal().getBytes(StandardCharsets.UTF_8))), stored.getRawDataRef());
            assertFalse(JsonUtils.toJsonString(stored).contains(stored.getEncryptedOriginal()));
            assertFalse(stored.toString().contains(stored.getEncryptedOriginal()));
            when(mapper.selectByEventId(stored.getEventId())).thenReturn(stored);
            assertFalse(service.acceptYeepayEvent("payment", payload, "provider retry different envelope"));
            verify(mapper, times(1)).insert(any(PayYeepayEventDO.class));
        } finally {
            TenantContextHolder.clear();
        }
    }
}
