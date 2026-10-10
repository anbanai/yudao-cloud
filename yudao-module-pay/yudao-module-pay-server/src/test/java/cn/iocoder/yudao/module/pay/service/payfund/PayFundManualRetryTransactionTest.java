package cn.iocoder.yudao.module.pay.service.payfund;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundRetryReqDTO;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayDivideRequestDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundDetailDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayRefundFundDetailDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.refund.PayRefundDO;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayDivideRequestMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundDetailMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundOperationMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundOrderMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayRefundFundDetailMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.refund.PayRefundMapper;
import cn.iocoder.yudao.module.pay.dal.redis.no.PayNoRedisDAO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayDivideRequestStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayFundDetailStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayFundOrderStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayRefundFundDetailStatusEnum;
import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClientConfig;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import cn.iocoder.yudao.module.pay.service.refund.PayRefundService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real database state must survive manual retry races and failures after the audit commits. */
@Import({PayFundServiceImpl.class, PayFundOperationsService.class})
class PayFundManualRetryTransactionTest extends BaseDbUnitTest {

    @Resource private PayFundServiceImpl service;
    @Resource private PayFundOperationsService operations;
    @Resource private PayFundOrderMapper orderMapper;
    @Resource private PayFundDetailMapper detailMapper;
    @Resource private PayRefundFundDetailMapper refundDetailMapper;
    @Resource private PayRefundMapper refundMapper;
    @Resource private PayFundOperationMapper operationMapper;
    @MockitoSpyBean private PayDivideRequestMapper divideMapper;
    @MockitoBean private PayNoRedisDAO noRedisDAO;
    @MockitoBean private PayChannelService channelService;
    @MockitoBean private PayAppService appService;
    @MockitoBean private PayRefundService refundService;

    private YeepayPayClient client;

    @BeforeEach
    void setUp() {
        PayChannelDO channel = new PayChannelDO().setId(10L).setAppId(1L)
                .setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setConfig(new YeepayPayClientConfig().setFeeBearerMode("PLATFORM_FEE_ACCOUNT"));
        client = mock(YeepayPayClient.class);
        when(channelService.getChannel(10L)).thenReturn(channel);
        when(channelService.getPayClient(10L)).thenReturn(client);
        when(client.queryDivide(eq("P-1"), eq("YOP-1"), eq("DIVIDE-1"),
                eq("PARENT-OLD"), eq("COLLECTOR-OLD"))).thenReturn(Map.of("status", "PROCESSING"));
    }

    @Test
    void manualRetry_racingSuccessfulCallbackCannotDowngradeSuccess() throws Exception {
        PayFundOrderDO order = manualDivision();
        CountDownLatch requestRead = new CountDownLatch(1);
        CountDownLatch releaseRetry = new CountDownLatch(1);
        CountDownLatch callbackStarted = new CountDownLatch(1);
        AtomicBoolean pauseOnce = new AtomicBoolean(true);
        AtomicBoolean retryHasTransaction = new AtomicBoolean();
        doAnswer(invocation -> {
            PayDivideRequestDO request = (PayDivideRequestDO) invocation.callRealMethod();
            if ("manual-fund-retry".equals(Thread.currentThread().getName()) && pauseOnce.compareAndSet(true, false)) {
                retryHasTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
                requestRead.countDown();
                await(releaseRetry);
            }
            return request;
        }).when(divideMapper).selectByFundOrderId(order.getId());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> retry = executor.submit(() -> {
                Thread.currentThread().setName("manual-fund-retry");
                service.retryDivide(order.getId());
            });
            assertThat(requestRead.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> callback = executor.submit(() -> {
                callbackStarted.countDown();
                service.handleDivide(divideSuccess());
            });
            assertThat(callbackStarted.await(5, TimeUnit.SECONDS)).isTrue();
            if (retryHasTransaction.get()) {
                // The successful callback must wait while retry preparation owns the fund lock.
                assertThrows(TimeoutException.class, () -> callback.get(250, TimeUnit.MILLISECONDS));
            } else {
                // Reproduce the old race: callback commits between retry's read and stale update.
                callback.get(5, TimeUnit.SECONDS);
            }
            releaseRetry.countDown();
            retry.get(5, TimeUnit.SECONDS);
            callback.get(5, TimeUnit.SECONDS);

            assertThat(divideMapper.selectByFundOrderId(order.getId()).getStatus())
                    .isEqualTo(PayDivideRequestStatusEnum.SUCCESS.getStatus());
            assertThat(orderMapper.selectById(order.getId()).getDivideStatus())
                    .isEqualTo(PayFundOrderStatusEnum.SUCCESS.getStatus());
            assertThat(detailMapper.selectListByFundOrderId(order.getId())).singleElement()
                    .satisfies(detail -> assertThat(detail.getStatus()).isEqualTo(PayFundDetailStatusEnum.SUCCESS.getStatus()));
            verify(client, never()).applyDivide(anyString(), anyString(), anyString(), anyList(), anyString(), anyString());
        } finally {
            releaseRetry.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void manualDivisionRetry_commitsAuditAndQueryIntentBeforeNetwork() {
        PayFundOrderDO order = manualDivision();
        when(client.queryDivide(eq("P-1"), eq("YOP-1"), eq("DIVIDE-1"),
                eq("PARENT-OLD"), eq("COLLECTOR-OLD"))).thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    ExecutorService reader = Executors.newSingleThreadExecutor();
                    try {
                        assertThat(reader.submit(() -> divideMapper.selectByFundOrderId(order.getId()).getStatus())
                                .get(5, TimeUnit.SECONDS)).isEqualTo(PayDivideRequestStatusEnum.PROCESSING.getStatus());
                        assertThat(reader.submit(() -> operationMapper.selectCount()).get(5, TimeUnit.SECONDS)).isEqualTo(1L);
                    } finally {
                        reader.shutdownNow();
                    }
                    throw new IllegalStateException("provider outcome unknown");
                });

        assertDoesNotThrow(() -> operations.retryDivide(command(order.getId())));

        assertThat(operationMapper.selectCount()).isEqualTo(1L);
        assertThat(divideMapper.selectByFundOrderId(order.getId()).getRequestNo()).isEqualTo("DIVIDE-1");
        assertThat(divideMapper.selectByFundOrderId(order.getId()).getStatus())
                .isEqualTo(PayDivideRequestStatusEnum.PROCESSING.getStatus());
        assertThat(orderMapper.selectById(order.getId()).getDivideStatus())
                .isEqualTo(PayFundOrderStatusEnum.PROCESSING.getStatus());
        verify(client).queryDivide("P-1", "YOP-1", "DIVIDE-1", "PARENT-OLD", "COLLECTOR-OLD");
        verify(client, never()).applyDivide(anyString(), anyString(), anyString(), anyList(), anyString(), anyString());
    }

    @Test
    void manualRefundRetry_commitsAuditAndOriginalReturnIntentWhenAfterCommitFails() {
        PayFundOrderDO order = manualDivision();
        PayRefundDO refund = new PayRefundDO().setNo("R-1").setAppId(1L).setChannelId(10L)
                .setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode()).setOrderId(30L).setOrderNo("P-1")
                .setMerchantOrderId("M-1").setMerchantRefundId("MR-1").setNotifyUrl("https://business.test/refund")
                .setChannelOrderNo("YOP-1")
                .setStatus(PayRefundStatusEnum.WAITING.getStatus()).setPayPrice(300).setRefundPrice(100)
                .setReason("manual retry fixture");
        refundMapper.insert(refund);
        PayRefundFundDetailDO detail = new PayRefundFundDetailDO().setNo("RF-1").setRefundId(refund.getId())
                .setRefundNo("R-1").setFundOrderId(order.getId()).setAppId(1L).setAmount(100).setPrice(100)
                .setDivideBackRequestId("R-1_BACK").setDivideBackStatus(PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus())
                .setRefundStatus(PayRefundFundDetailStatusEnum.PENDING.getStatus()).setRetryCount(99);
        refundDetailMapper.insert(detail);
        when(refundService.retryRefund(refund.getId())).thenThrow(new IllegalStateException("provider outcome unknown"));

        assertDoesNotThrow(() -> operations.retryRefund(command(refund.getId())));

        assertThat(operationMapper.selectCount()).isEqualTo(1L);
        assertThat(refundDetailMapper.selectById(detail.getId()).getDivideBackStatus())
                .isEqualTo(PayRefundFundDetailStatusEnum.PROCESSING.getStatus());
        assertThat(refundDetailMapper.selectById(detail.getId()).getDivideBackRequestId()).isEqualTo("R-1_BACK");
        assertThat(refundDetailMapper.selectById(detail.getId()).getRetryCount()).isZero();
        verify(refundService).retryRefund(refund.getId());
    }

    private PayFundOrderDO manualDivision() {
        PayFundOrderDO order = new PayFundOrderDO().setNo("F-1").setAppId(1L).setChannelId(10L)
                .setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode()).setPayOrderId(30L).setPayOrderNo("P-1")
                .setChannelOrderNo("YOP-1").setAmount(300).setPrice(300).setRefundPrice(0)
                .setClearingStatus(PayFundOrderStatusEnum.SUCCESS.getStatus()).setSuccessTime(LocalDateTime.now())
                .setDivideStatus(PayFundOrderStatusEnum.MANUAL_REQUIRED.getStatus())
                .setParentMerchantNo("PARENT-OLD").setCollectionMerchantNo("COLLECTOR-OLD");
        orderMapper.insert(order);
        detailMapper.insert(new PayFundDetailDO().setNo("FD-1").setFundOrderId(order.getId())
                .setPayOrderId(30L).setMerchantId(40L).setMerchantNo("SUB-1").setAmount(300).setPrice(300)
                .setDivideAmount(300).setRefundedAmount(0).setReturnedAmount(0)
                .setStatus(PayFundDetailStatusEnum.PROCESSING.getStatus()));
        divideMapper.insert(new PayDivideRequestDO().setNo("D-1").setRequestNo("DIVIDE-1").setFundOrderId(order.getId())
                .setAppId(1L).setChannelId(10L).setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setAmount(300).setPrice(300).setStatus(PayDivideRequestStatusEnum.MANUAL_REQUIRED.getStatus()));
        return order;
    }

    private Map<String, Object> divideSuccess() {
        return Map.of("status", "SUCCESS", "orderId", "P-1", "uniqueOrderNo", "YOP-1",
                "parentMerchantNo", "PARENT-OLD", "merchantNo", "COLLECTOR-OLD",
                "divideRequestId", "DIVIDE-1", "divideAmount", "3.00",
                "divideDetail", List.of(Map.of("divideDetailNo", "YOP-D-1", "ledgerNo", "SUB-1", "amount", "3.00")));
    }

    private PayFundRetryReqDTO command(Long id) {
        return new PayFundRetryReqDTO().setTargetId(id).setOperatorId(9L)
                .setReason("已核实原请求状态").setExternalEvidence("provider-evidence-42");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test coordination timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

}
