package cn.iocoder.yudao.module.pay.controller.admin.notify;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.PayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.order.PayOrderRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import cn.iocoder.yudao.module.pay.service.order.PayOrderService;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundService;
import cn.iocoder.yudao.module.pay.service.refund.PayRefundService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayNotifyControllerLegacyYeepayTest {

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @ParameterizedTest
    @CsvSource({"payment,true", "payment,false", "refund,true", "refund,false"})
    void legacyYeepayRoute_archivesAndValidatesBeforeMutationAndPreservesOfficialAck(String eventType,
                                                                                     boolean matches) {
        PayNotifyController controller = new PayNotifyController();
        PayChannelService channels = mock(PayChannelService.class);
        PayFundService funds = mock(PayFundService.class);
        PayOrderService orders = mock(PayOrderService.class);
        PayRefundService refunds = mock(PayRefundService.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        YeepayPayClient client = mock(YeepayPayClient.class);
        ReflectionTestUtils.setField(controller, "channelService", channels);
        ReflectionTestUtils.setField(controller, "payFundService", funds);
        ReflectionTestUtils.setField(controller, "orderService", orders);
        ReflectionTestUtils.setField(controller, "refundService", refunds);
        ReflectionTestUtils.setField(controller, "yeepayEventExecutor", executor);
        when(channels.getPayClient(10L)).thenReturn(client);
        PayChannelDO channel = new PayChannelDO().setId(10L).setAppId(1L);
        channel.setTenantId(42L);
        when(channels.validExistingPayChannel(10L)).thenReturn(channel);
        Map<String, Object> payload = Map.of("orderId", "P-1", "refundRequestId", "R-1",
                "status", "SUCCESS", "orderAmount", matches ? "3.00" : "999.00",
                "refundAmount", matches ? "1.00" : "999.00");
        var verified = new YeepayPayClient.VerifiedCallback(payload,
                new YeepayPayClient.YeepayCallbackAck("OFFICIAL-ACK", "application/xml", Map.of("X-Yop-Ack", "signed")));
        when(client.handlePaymentNotify(any(), any(), any())).thenReturn(verified);
        when(client.handleRefundNotify(any(), any(), any())).thenReturn(verified);
        // Seed legacy parsers too: the old route would otherwise fail only because its mock is empty.
        PayOrderRespDTO payment = PayOrderRespDTO.successOf("YOP-1", "user", LocalDateTime.now(), "P-1", Map.of());
        PayRefundRespDTO refund = PayRefundRespDTO.successOf("YOP-R-1", LocalDateTime.now(), "R-1", Map.of());
        when(client.parseOrderNotify(any(), any(), any())).thenReturn(payment);
        when(client.parseRefundNotify(any(), any(), any())).thenReturn(refund);
        when(client.parseYeepayOrderNotify(anyMap())).thenReturn(payment);
        when(client.parseYeepayRefundNotify(anyMap())).thenReturn(refund);
        when(funds.acceptYeepayEvent(eq(eventType), anyMap(), anyString())).thenReturn(true);
        if (matches) {
            when(funds.validatePaymentNotify(anyMap())).thenReturn(true);
            when(funds.validateRefundNotify(anyMap())).thenReturn(true);
        } else {
            when(funds.validatePaymentNotify(anyMap())).thenThrow(new IllegalArgumentException("original amount mismatch"));
            when(funds.validateRefundNotify(anyMap())).thenThrow(new IllegalArgumentException("original amount mismatch"));
        }
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        Object response = "payment".equals(eventType)
                ? controller.notifyOrder(10L, Map.of("response", "signed"), "signed-body", Map.of("header", "value"))
                : controller.notifyRefund(10L, Map.of("response", "signed"), "signed-body", Map.of("header", "value"));

        verify(funds).acceptYeepayEvent(eq(eventType), anyMap(), anyString());
        assertThat(response).isInstanceOf(ResponseEntity.class);
        ResponseEntity<?> ack = (ResponseEntity<?>) response;
        assertThat(ack.getBody()).isEqualTo("OFFICIAL-ACK");
        assertThat(ack.getHeaders().getFirst("Content-Type")).isEqualTo("application/xml");
        assertThat(ack.getHeaders().getFirst("X-Yop-Ack")).isEqualTo("signed");
        verify(client, never()).parseOrderNotify(any(), any(), any());
        verify(client, never()).parseRefundNotify(any(), any(), any());
        if ("payment".equals(eventType)) {
            verify(funds).validatePaymentNotify(anyMap());
            if (matches) verify(orders).notifyOrder(10L, payment);
            else verifyNoInteractions(orders);
            verifyNoInteractions(refunds);
        } else {
            verify(funds).validateRefundNotify(anyMap());
            if (matches) verify(refunds).notifyRefund(10L, refund);
            else verifyNoInteractions(refunds);
            verifyNoInteractions(orders);
        }
        if (matches) verify(funds).markYeepayEventProcessed(eq(eventType), anyMap());
        else verify(funds).markYeepayEventFailed(eq(eventType), anyMap(), any(IllegalArgumentException.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"payment", "refund"})
    void legacyNonYeepayRoute_keepsPlainSuccessAndNormalNotification(String eventType) {
        PayNotifyController controller = new PayNotifyController();
        PayChannelService channels = mock(PayChannelService.class);
        PayOrderService orders = mock(PayOrderService.class);
        PayRefundService refunds = mock(PayRefundService.class);
        PayFundService funds = mock(PayFundService.class);
        PayClient<?> client = mock(PayClient.class);
        ReflectionTestUtils.setField(controller, "channelService", channels);
        ReflectionTestUtils.setField(controller, "orderService", orders);
        ReflectionTestUtils.setField(controller, "refundService", refunds);
        ReflectionTestUtils.setField(controller, "payFundService", funds);
        when(channels.getPayClient(10L)).thenReturn(client);
        PayOrderRespDTO payment = PayOrderRespDTO.successOf("WX-1", "user", LocalDateTime.now(), "P-1", Map.of());
        PayRefundRespDTO refund = PayRefundRespDTO.successOf("WX-R-1", LocalDateTime.now(), "R-1", Map.of());
        when(client.parseOrderNotify(any(), any(), any())).thenReturn(payment);
        when(client.parseRefundNotify(any(), any(), any())).thenReturn(refund);

        Object response = "payment".equals(eventType)
                ? controller.notifyOrder(10L, Map.of(), "body", Map.of())
                : controller.notifyRefund(10L, Map.of(), "body", Map.of());

        assertThat(response instanceof ResponseEntity<?> ack ? ack.getBody() : response).isEqualTo("success");
        if ("payment".equals(eventType)) verify(orders).notifyOrder(10L, payment);
        else verify(refunds).notifyRefund(10L, refund);
        verifyNoInteractions(funds);
    }

}
