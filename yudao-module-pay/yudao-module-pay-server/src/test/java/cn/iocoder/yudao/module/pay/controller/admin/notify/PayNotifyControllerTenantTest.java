package cn.iocoder.yudao.module.pay.controller.admin.notify;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import cn.iocoder.yudao.module.pay.service.order.PayOrderService;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundService;
import cn.iocoder.yudao.module.pay.service.refund.PayRefundService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayNotifyControllerTenantTest {

    @AfterEach
    void cleanTenantContext() {
        TenantContextHolder.clear();
    }

    @ParameterizedTest
    @CsvSource({"Payment,payment", "Clearing,clearing", "Divide,divide", "Refund,refund",
            "DivideBack,divide-back", "Merchant,merchant"})
    void verifiedCallback_persistenceAndWorkerUseOriginalChannelTenant(String callback, String eventType)
            throws Exception {
        PayNotifyController controller = new PayNotifyController();
        PayChannelService channelService = mock(PayChannelService.class);
        PayFundService fundService = mock(PayFundService.class);
        YeepayPayClient client = mock(YeepayPayClient.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ReflectionTestUtils.setField(controller, "channelService", channelService);
        ReflectionTestUtils.setField(controller, "payFundService", fundService);
        ReflectionTestUtils.setField(controller, "yeepayEventExecutor", executor);
        ReflectionTestUtils.setField(controller, "orderService", mock(PayOrderService.class));
        ReflectionTestUtils.setField(controller, "refundService", mock(PayRefundService.class));
        when(channelService.getPayClient(10L)).thenReturn(client);
        PayChannelDO channel = new PayChannelDO().setId(10L).setAppId(1L);
        channel.setTenantId(42L);
        when(channelService.validExistingPayChannel(10L)).thenReturn(channel);
        Map<String, Object> payload = Map.of("orderId", "P-STABLE-1", "status", "PROCESSING",
                "_trustedChannelId", 999L, "_trustedAppId", 888L);
        YeepayPayClient.VerifiedCallback verified = new YeepayPayClient.VerifiedCallback(payload,
                new YeepayPayClient.YeepayCallbackAck("SUCCESS", "text/plain", Map.of()));
        when(client.handlePaymentNotify(any(), any(), any())).thenReturn(verified);
        when(client.handleClearingNotify(any(), any(), any())).thenReturn(verified);
        when(client.handleDivideNotify(any(), any(), any())).thenReturn(verified);
        when(client.handleRefundNotify(any(), any(), any())).thenReturn(verified);
        when(client.handleDivideBackNotify(any(), any(), any())).thenReturn(verified);
        when(client.handleMerchantNotify(any(), any(), any())).thenReturn(verified);
        List<Long> observedTenants = new ArrayList<>();
        List<Boolean> observedIgnore = new ArrayList<>();
        when(fundService.acceptYeepayEvent(eq(eventType), anyMap(), anyString())).thenAnswer(invocation -> {
            Map<String, Object> accepted = invocation.getArgument(1);
            assertThat(accepted.get("_trustedChannelId")).isEqualTo(10L);
            assertThat(accepted.get("_trustedAppId")).isEqualTo(1L);
            assertThat(accepted).isNotSameAs(payload);
            String original = invocation.getArgument(2);
            assertThat(original).contains("verified-body", "x-yop-sign", "provider-signature");
            assertThat(original).doesNotContain("login-token", "session-cookie", "unrelated-header");
            observedTenants.add(TenantContextHolder.getTenantId());
            observedIgnore.add(TenantContextHolder.isIgnore());
            return true;
        });
        doAnswer(invocation -> {
            observedTenants.add(TenantContextHolder.getTenantId());
            observedIgnore.add(TenantContextHolder.isIgnore());
            return null;
        }).when(fundService).markYeepayEventProcessed(eq(eventType), anyMap());
        AtomicReference<Runnable> worker = new AtomicReference<>();
        doAnswer(invocation -> {
            worker.set(invocation.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));
        TenantContextHolder.setTenantId(99L);
        TenantContextHolder.setIgnore(true);

        controller.getClass().getMethod("notifyYeepay" + callback, Long.class, Map.class, String.class, Map.class)
                .invoke(controller, 10L, Map.of("response", "provider-encrypted-response"), "verified-body",
                        Map.of("x-yop-sign", "provider-signature", "Authorization", "login-token",
                                "Cookie", "session-cookie", "X-Trace", "unrelated-header"));
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(99L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
        // 模拟线程池执行时继承了入口的 TenantIgnore，而不是依赖线程上下文恰好正确。
        worker.get().run();

        assertThat(observedTenants).containsExactly(42L, 42L);
        assertThat(observedIgnore).containsExactly(false, false);
        assertThat(payload.get("_trustedChannelId")).isEqualTo(999L);
        assertThat(payload.get("_trustedAppId")).isEqualTo(888L);
        assertThat(TenantContextHolder.getTenantId()).isEqualTo(99L);
        assertThat(TenantContextHolder.isIgnore()).isTrue();
    }

    @Test
    void saturatedWorkerStillAcknowledgesDurablyAcceptedCallback() {
        PayNotifyController controller = new PayNotifyController();
        PayChannelService channels = mock(PayChannelService.class);
        PayFundService funds = mock(PayFundService.class);
        YeepayPayClient client = mock(YeepayPayClient.class);
        ThreadPoolTaskExecutor executor = mock(ThreadPoolTaskExecutor.class);
        ReflectionTestUtils.setField(controller, "channelService", channels);
        ReflectionTestUtils.setField(controller, "payFundService", funds);
        ReflectionTestUtils.setField(controller, "yeepayEventExecutor", executor);
        PayChannelDO channel = new PayChannelDO().setId(10L).setAppId(1L);
        channel.setTenantId(42L);
        when(channels.getPayClient(10L)).thenReturn(client);
        when(channels.validExistingPayChannel(10L)).thenReturn(channel);
        when(client.handleClearingNotify(any(), any(), any())).thenReturn(new YeepayPayClient.VerifiedCallback(
                Map.of("orderId", "P-1", "clearStatus", "SUCCESS"),
                new YeepayPayClient.YeepayCallbackAck("SUCCESS", "text/plain", Map.of())));
        when(funds.acceptYeepayEvent(eq("clearing"), anyMap(), anyString())).thenReturn(true);
        doThrow(new org.springframework.core.task.TaskRejectedException("queue saturated"))
                .when(executor).execute(any(Runnable.class));

        var response = controller.notifyYeepayClearing(10L, Map.of(), "signed-body", Map.of());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("SUCCESS");
        verify(funds).acceptYeepayEvent(eq("clearing"), anyMap(), anyString());
        verify(funds, never()).markYeepayEventProcessed(anyString(), anyMap());
        verify(funds, never()).handleClearing(anyMap());
    }

}
