package cn.iocoder.yudao.module.pay.service.payfund;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundFundAllocationDTO;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayDivideRequestDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundDetailDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayRefundFundDetailDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.refund.PayRefundDO;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayDivideRequestMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundDetailMapper;
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
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClientConfig;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses real transactions and distinct database connections to exercise the fund locks and counters. */
@Import(PayFundServiceImpl.class)
class PayFundTransactionTest extends BaseDbUnitTest {

    @Resource
    private PayFundServiceImpl service;
    @Resource
    private PayFundOrderMapper fundOrderMapper;
    @Resource
    private PayFundDetailMapper fundDetailMapper;
    @Resource
    private PayDivideRequestMapper divideMapper;
    @MockitoSpyBean
    private PayRefundFundDetailMapper refundFundMapper;
    @Resource
    private PayRefundMapper refundMapper;
    @Resource
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private PayNoRedisDAO noRedisDAO;
    @MockitoBean
    private PayChannelService channelService;
    @MockitoBean
    private PayAppService appService;

    private PayChannelDO channel;
    private YeepayPayClient client;

    @BeforeEach
    void setUp() {
        AtomicInteger sequence = new AtomicInteger();
        when(noRedisDAO.generate(anyString())).thenAnswer(invocation -> "NO-" + sequence.incrementAndGet());
        channel = new PayChannelDO().setId(10L).setAppId(1L).setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setConfig(new YeepayPayClientConfig().setFeeBearerMode("PLATFORM_FEE_ACCOUNT"));
        client = mock(YeepayPayClient.class);
        when(channelService.getChannel(10L)).thenReturn(channel);
        when(channelService.getPayClient(10L)).thenReturn(client);
    }

    @Test
    void processDivide_commitsProcessingRequestBeforeNetwork() {
        Snapshot snapshot = snapshot(false);
        when(client.applyDivide(eq("P-1"), eq("YOP-1"), anyString(), anyList(), eq("PARENT-OLD"), eq("COLLECTOR-OLD")))
                .thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    ExecutorService reader = Executors.newSingleThreadExecutor();
                    try {
                        PayDivideRequestDO committed = reader.submit(() -> divideMapper.selectByFundOrderId(snapshot.order().getId()))
                                .get(5, TimeUnit.SECONDS);
                        assertThat(committed.getStatus()).isEqualTo(PayDivideRequestStatusEnum.PROCESSING.getStatus());
                        assertThat(committed.getRequestNo()).isEqualTo(invocation.getArgument(2));
                        assertThat(committed.getAmount()).isEqualTo(300);
                    } finally {
                        reader.shutdownNow();
                    }
                    return Map.of("status", "PROCESSING");
                });

        service.processDivide(snapshot.order().getId());

        verify(client).applyDivide(eq("P-1"), eq("YOP-1"), eq("DIVIDE_" + snapshot.order().getId()), anyList(),
                eq("PARENT-OLD"), eq("COLLECTOR-OLD"));
        assertThat(divideMapper.selectByFundOrderId(snapshot.order().getId()).getStatus())
                .isEqualTo(PayDivideRequestStatusEnum.PROCESSING.getStatus());
    }

    @Test
    void executeReturn_commitsStableProcessingIdBeforeNetwork() {
        Snapshot snapshot = snapshot(true);
        PayRefundDO refund = reserveRefund(snapshot);
        when(client.applyDivideBack(eq("P-1"), eq("YOP-1"), eq("DIVIDE-F-1"), eq("R-1_BACK"), anyList(),
                eq("PARENT-OLD"), eq("COLLECTOR-OLD"))).thenAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    ExecutorService reader = Executors.newSingleThreadExecutor();
                    try {
                        PayRefundFundDetailDO committed = reader.submit(() -> refundFundMapper.selectByRefundId(refund.getId()))
                                .get(5, TimeUnit.SECONDS);
                        assertThat(committed.getDivideBackStatus()).isEqualTo(PayRefundFundDetailStatusEnum.PROCESSING.getStatus());
                        assertThat(committed.getDivideBackRequestId()).isEqualTo("R-1_BACK");
                    } finally {
                        reader.shutdownNow();
                    }
                    return Map.of("status", "PROCESSING");
                });

        assertThat(service.executeRefundPreparation(refund.getId())).isFalse();

        verify(client).applyDivideBack(eq("P-1"), eq("YOP-1"), eq("DIVIDE-F-1"), eq("R-1_BACK"), anyList(),
                eq("PARENT-OLD"), eq("COLLECTOR-OLD"));
        assertThat(refundFundMapper.selectByRefundId(refund.getId()).getDivideBackRequestId()).isEqualTo("R-1_BACK");
    }

    @Test
    void successfulPartialRefund_reducesExecutionAmountAndKeepsOriginalSnapshot() {
        Snapshot snapshot = snapshot(false);
        PayRefundDO refund = reserveRefund(snapshot);
        assertThat(service.prepareDivideAttempt(snapshot.order().getId())).isNull();

        service.onRefundResult(refund, PayRefundRespDTO.successOf("YOP-R-1", LocalDateTime.now(), refund.getNo(), Map.of()));
        PayFundServiceImpl.DivideAttempt attempt = service.prepareDivideAttempt(snapshot.order().getId());

        assertThat(attempt.request().getAmount()).isEqualTo(200);
        assertThat(attempt.details()).singleElement().satisfies(detail -> assertThat(detail.get("amount")).isEqualTo("2.00"));
        PayFundDetailDO detail = fundDetailMapper.selectById(snapshot.detail().getId());
        assertThat(detail.getAmount()).isEqualTo(300);
        assertThat(detail.getDivideAmount()).isEqualTo(200);
        assertThat(detail.getRefundedAmount()).isEqualTo(100);
        assertThat(fundOrderMapper.selectById(snapshot.order().getId()).getAmount()).isEqualTo(300);
    }

    @Test
    void pendingRefundReservation_holdsSameFundLockAsDivision() throws Exception {
        Snapshot snapshot = snapshot(false);
        PayRefundDO refund = refund();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch refundReserved = new CountDownLatch(1);
        CountDownLatch releaseRefund = new CountDownLatch(1);
        CountDownLatch divisionStarted = new CountDownLatch(1);
        try {
            Future<?> reservation = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                service.prepareRefund(refund, payOrder(snapshot), channel, allocations(snapshot));
                refundReserved.countDown();
                await(releaseRefund);
            }));
            assertThat(refundReserved.await(5, TimeUnit.SECONDS)).isTrue();
            Future<PayFundServiceImpl.DivideAttempt> division = executor.submit(() -> {
                divisionStarted.countDown();
                return service.prepareDivideAttempt(snapshot.order().getId());
            });
            assertThat(divisionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> division.get(250, TimeUnit.MILLISECONDS));
            releaseRefund.countDown();
            reservation.get(5, TimeUnit.SECONDS);
            assertThat(division.get(5, TimeUnit.SECONDS)).isNull();
            assertThat(divideMapper.selectByFundOrderId(snapshot.order().getId())).isNull();
            assertThat(refundFundMapper.selectByRefundId(refund.getId()).getAmount()).isEqualTo(100);
        } finally {
            releaseRefund.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void divisionReservation_blocksRefundUntilCommittedAndThenRejectsIt() throws Exception {
        Snapshot snapshot = snapshot(false);
        PayRefundDO refund = refund();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch divideReserved = new CountDownLatch(1);
        CountDownLatch releaseDivision = new CountDownLatch(1);
        CountDownLatch refundStarted = new CountDownLatch(1);
        try {
            Future<?> division = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertThat(service.prepareDivideAttempt(snapshot.order().getId())).isNotNull();
                divideReserved.countDown();
                await(releaseDivision);
            }));
            assertThat(divideReserved.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> reservation = executor.submit(() -> {
                refundStarted.countDown();
                return service.prepareRefund(refund, payOrder(snapshot), channel, allocations(snapshot));
            });
            assertThat(refundStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> reservation.get(250, TimeUnit.MILLISECONDS));
            releaseDivision.countDown();
            division.get(5, TimeUnit.SECONDS);
            ExecutionException failure = assertThrows(ExecutionException.class, () -> reservation.get(5, TimeUnit.SECONDS));
            assertThat(failure.getCause()).isInstanceOf(IllegalStateException.class);
            assertThat(refundFundMapper.selectListByRefundNo(refund.getNo())).isEmpty();
            assertThat(divideMapper.selectByFundOrderId(snapshot.order().getId()).getAmount()).isEqualTo(300);
        } finally {
            releaseDivision.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void returnStateAndAmountCounter_rollBackTogetherAndCountSuccessOnce() {
        Snapshot snapshot = snapshot(true);
        PayRefundDO refund = reserveRefund(snapshot);
        service.prepareReturnAttempt(refund.getId());
        Map<String, Object> success = returnSuccess();

        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            service.completeReturnAttempt(refund.getId(), success);
            assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isEqualTo(100);
            throw new IllegalStateException("rollback after counter update");
        }));
        assertThat(refundFundMapper.selectByRefundId(refund.getId()).getDivideBackStatus())
                .isEqualTo(PayRefundFundDetailStatusEnum.PROCESSING.getStatus());
        assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isZero();

        service.completeReturnAttempt(refund.getId(), success);
        service.completeReturnAttempt(refund.getId(), success);
        service.completeReturnAttempt(refund.getId(), Map.of("status", "FAIL", "divideBackDetail",
                List.of(Map.of("divideDetailNo", "YOP-D-1", "status", "FAIL"))));

        assertThat(refundFundMapper.selectByRefundId(refund.getId()).getDivideBackStatus())
                .isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
        assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isEqualTo(100);
        assertThat(JsonUtils.parseArray(refundMapper.selectById(refund.getId()).getFundAllocationData(), PayRefundFundAllocationDTO.class))
                .singleElement().satisfies(allocation -> assertThat(allocation.getDivideBackCompleted()).isTrue());
    }

    @Test
    void aggregateReturnSuccess_requiresOriginalReferencesBeforeChangingCounter() {
        Snapshot snapshot = snapshot(true);
        PayRefundDO refund = reserveRefund(snapshot);
        service.prepareReturnAttempt(refund.getId());
        Map<String, Object> aggregate = new LinkedHashMap<>(returnSuccess());
        aggregate.remove("divideBackDetail");
        aggregate.put("merchantNo", "OTHER-COLLECTOR");

        assertThrows(IllegalArgumentException.class, () -> service.completeReturnAttempt(refund.getId(), aggregate));

        assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isZero();
        assertThat(refundFundMapper.selectByRefundId(refund.getId()).getDivideBackStatus())
                .isEqualTo(PayRefundFundDetailStatusEnum.PROCESSING.getStatus());
        aggregate.put("merchantNo", "COLLECTOR-OLD");

        service.completeReturnAttempt(refund.getId(), aggregate);

        assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isEqualTo(100);
        assertThat(refundFundMapper.selectByRefundId(refund.getId()).getDivideBackStatus())
                .isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
    }

    @Test
    void failedRefund_afterSuccessfulReturnKeepsOriginalReservation() {
        Snapshot snapshot = snapshot(true);
        PayRefundDO first = reserveRefund(snapshot);
        service.prepareReturnAttempt(first.getId());
        service.completeReturnAttempt(first.getId(), returnSuccess());
        service.onRefundResult(first, PayRefundRespDTO.failureOf(first.getNo(), Map.of()));

        assertNewRefundCannotReturnReservedMoney(snapshot);

        assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isEqualTo(100);
    }

    @Test
    void failedRefund_afterUncertainReturnKeepsOriginalReservation() {
        Snapshot snapshot = snapshot(true);
        PayRefundDO first = reserveRefund(snapshot);
        service.prepareReturnAttempt(first.getId());
        service.onRefundResult(first, PayRefundRespDTO.failureOf(first.getNo(), Map.of()));

        assertNewRefundCannotReturnReservedMoney(snapshot);

        assertThat(refundFundMapper.selectByRefundId(first.getId()).getDivideBackStatus())
                .isEqualTo(PayRefundFundDetailStatusEnum.PROCESSING.getStatus());
    }

    @Test
    void failedRefund_afterManualReturnKeepsOriginalReservation() {
        Snapshot snapshot = snapshot(true);
        PayRefundDO first = reserveRefund(snapshot);
        service.prepareReturnAttempt(first.getId());
        PayRefundFundDetailDO row = refundFundMapper.selectByRefundId(first.getId());
        refundFundMapper.updateById(new PayRefundFundDetailDO().setId(row.getId())
                .setDivideBackStatus(PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus()));
        service.onRefundResult(first, PayRefundRespDTO.failureOf(first.getNo(), Map.of()));

        assertNewRefundCannotReturnReservedMoney(snapshot);
    }

    @Test
    void closedRefund_afterSuccessfulReturnKeepsOriginalReservation() {
        Snapshot snapshot = snapshot(true);
        PayRefundDO first = reserveRefund(snapshot);
        service.prepareReturnAttempt(first.getId());
        service.completeReturnAttempt(first.getId(), returnSuccess());
        PayRefundFundDetailDO row = refundFundMapper.selectByRefundId(first.getId());
        refundFundMapper.updateById(new PayRefundFundDetailDO().setId(row.getId())
                .setRefundStatus(PayRefundFundDetailStatusEnum.FAILED.getStatus())
                .setStatus(PayRefundFundDetailStatusEnum.CLOSED.getStatus()));

        assertNewRefundCannotReturnReservedMoney(snapshot);
    }

    @Test
    void failedRefund_afterSuccessfulReturnRequiresManualResolutionAndKeepsReturnEvidence() {
        Snapshot snapshot = snapshot(true);
        PayRefundDO first = reserveRefund(snapshot);
        service.prepareReturnAttempt(first.getId());
        service.completeReturnAttempt(first.getId(), returnSuccess());

        service.onRefundResult(first, PayRefundRespDTO.failureOf(first.getNo(), Map.of()));
        service.onRefundResult(first, PayRefundRespDTO.failureOf(first.getNo(), Map.of()));

        PayRefundFundDetailDO row = refundFundMapper.selectByRefundId(first.getId());
        assertThat(row.getRefundStatus()).isEqualTo(PayRefundFundDetailStatusEnum.FAILED.getStatus());
        assertThat(row.getStatus()).isEqualTo(PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus());
        assertThat(row.getLastError()).contains("资金已归还", "人工核实");
        assertThat(row.getDivideBackStatus()).isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
        assertThat(row.getDivideBackRequestId()).isEqualTo("R-1_BACK");
        assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isEqualTo(100);
    }

    @Test
    void lateSuccessfulReturn_afterFailedRefundKeepsManualStateUntilVerifiedRefundSuccess() {
        Snapshot snapshot = snapshot(true);
        PayRefundDO refund = reserveRefund(snapshot);
        service.prepareReturnAttempt(refund.getId());
        service.onRefundResult(refund, PayRefundRespDTO.failureOf(refund.getNo(), Map.of()));

        service.completeReturnAttempt(refund.getId(), returnSuccess());
        service.completeReturnAttempt(refund.getId(), returnSuccess());

        PayRefundFundDetailDO row = refundFundMapper.selectByRefundId(refund.getId());
        assertThat(row.getRefundStatus()).isEqualTo(PayRefundFundDetailStatusEnum.FAILED.getStatus());
        assertThat(row.getStatus()).isEqualTo(PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus());
        assertThat(row.getLastError()).contains("资金已归还", "人工核实");
        assertThat(row.getDivideBackStatus()).isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
        assertThat(row.getDivideBackRequestId()).isEqualTo("R-1_BACK");
        assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isEqualTo(100);
        assertNewRefundCannotReturnReservedMoney(snapshot);

        service.onRefundResult(refund, PayRefundRespDTO.successOf("YOP-R-1", LocalDateTime.now(), refund.getNo(), Map.of()));

        row = refundFundMapper.selectByRefundId(refund.getId());
        assertThat(row.getStatus()).isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
        assertThat(row.getLastError()).isNull();
        assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isEqualTo(100);
        assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getRefundedAmount()).isEqualTo(100);
    }

    @Test
    void lateSuccessfulReturn_stalePendingRefundReadCannotOverwriteCommittedManualState() throws Exception {
        Snapshot snapshot = snapshot(true);
        PayRefundDO refund = reserveRefund(snapshot);
        service.prepareReturnAttempt(refund.getId());
        CountDownLatch pendingRead = new CountDownLatch(1);
        CountDownLatch releaseReturn = new CountDownLatch(1);
        AtomicInteger returnReads = new AtomicInteger();
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PayRefundFundDetailDO> rows = (List<PayRefundFundDetailDO>) invocation.callRealMethod();
            if ("late-fund-return".equals(Thread.currentThread().getName()) && returnReads.incrementAndGet() == 2) {
                assertThat(rows.get(0).getRefundStatus()).isEqualTo(PayRefundFundDetailStatusEnum.PENDING.getStatus());
                pendingRead.countDown();
                await(releaseReturn);
            }
            return rows;
        }).when(refundFundMapper).selectListByRefundNo(refund.getNo());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> returned = executor.submit(() -> {
                Thread.currentThread().setName("late-fund-return");
                service.completeReturnAttempt(refund.getId(), returnSuccess());
            });
            assertThat(pendingRead.await(5, TimeUnit.SECONDS)).isTrue();
            service.onRefundResult(refund, PayRefundRespDTO.failureOf(refund.getNo(), Map.of()));
            assertThat(refundFundMapper.selectByRefundId(refund.getId()).getRefundStatus())
                    .isEqualTo(PayRefundFundDetailStatusEnum.FAILED.getStatus());

            releaseReturn.countDown();
            returned.get(5, TimeUnit.SECONDS);

            PayRefundFundDetailDO row = refundFundMapper.selectByRefundId(refund.getId());
            assertThat(row.getRefundStatus()).isEqualTo(PayRefundFundDetailStatusEnum.FAILED.getStatus());
            assertThat(row.getStatus()).isEqualTo(PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus());
            assertThat(row.getLastError()).contains("资金已归还", "人工核实");
            assertThat(row.getDivideBackStatus()).isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
            assertThat(row.getDivideBackRequestId()).isEqualTo("R-1_BACK");
            assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isEqualTo(100);
        } finally {
            releaseReturn.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void lateReturnUpdate_stalePendingRefundReadPreservesCommittedRefundSuccessTime() {
        Snapshot snapshot = snapshot(true);
        PayRefundDO refund = reserveRefund(snapshot);
        service.prepareReturnAttempt(refund.getId());
        PayRefundFundDetailDO stale = refundFundMapper.selectByRefundId(refund.getId());
        LocalDateTime refundSuccessTime = LocalDateTime.now().withNano(0);
        service.onRefundResult(refund, PayRefundRespDTO.successOf("YOP-R-1", refundSuccessTime, refund.getNo(), Map.of()));

        refundFundMapper.updateDivideBackStatusIfNotSuccess(stale
                .setDivideBackStatus(PayRefundFundDetailStatusEnum.SUCCESS.getStatus())
                .setStatus(PayRefundFundDetailStatusEnum.PENDING.getStatus()));

        PayRefundFundDetailDO row = refundFundMapper.selectByRefundId(refund.getId());
        assertThat(row.getRefundStatus()).isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
        assertThat(row.getStatus()).isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
        assertThat(row.getSuccessTime()).isEqualTo(refundSuccessTime);
        assertThat(row.getLastError()).isNull();
        assertThat(row.getDivideBackStatus()).isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
        assertThat(row.getDivideBackRequestId()).isEqualTo("R-1_BACK");
    }

    @Test
    void failedRefund_stalePendingReturnReadCannotOverwriteCommittedSuccessfulReturnManualState() throws Exception {
        Snapshot snapshot = snapshot(true);
        PayRefundDO refund = reserveRefund(snapshot);
        CountDownLatch pendingRead = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<PayRefundFundDetailDO> rows = (List<PayRefundFundDetailDO>) invocation.callRealMethod();
            if ("stale-refund-failure".equals(Thread.currentThread().getName())) {
                assertThat(rows.get(0).getDivideBackStatus()).isEqualTo(PayRefundFundDetailStatusEnum.PENDING.getStatus());
                pendingRead.countDown();
                await(releaseFailure);
            }
            return rows;
        }).when(refundFundMapper).selectListByRefundNo(refund.getNo());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> failure = executor.submit(() -> {
                Thread.currentThread().setName("stale-refund-failure");
                service.onRefundResult(refund, PayRefundRespDTO.failureOf(refund.getNo(), Map.of()));
            });
            assertThat(pendingRead.await(5, TimeUnit.SECONDS)).isTrue();
            service.prepareReturnAttempt(refund.getId());
            service.completeReturnAttempt(refund.getId(), returnSuccess());
            assertThat(refundFundMapper.selectByRefundId(refund.getId()).getDivideBackStatus())
                    .isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());

            releaseFailure.countDown();
            failure.get(5, TimeUnit.SECONDS);

            PayRefundFundDetailDO row = refundFundMapper.selectByRefundId(refund.getId());
            assertThat(row.getRefundStatus()).isEqualTo(PayRefundFundDetailStatusEnum.FAILED.getStatus());
            assertThat(row.getStatus()).isEqualTo(PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus());
            assertThat(row.getLastError()).contains("资金已归还", "人工核实");
            assertThat(row.getDivideBackStatus()).isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
            assertThat(row.getDivideBackRequestId()).isEqualTo("R-1_BACK");
            assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isEqualTo(100);
            assertNewRefundCannotReturnReservedMoney(snapshot);
        } finally {
            releaseFailure.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedUndividedRefund_canReleaseReservationWithoutReturningMerchantFunds() {
        Snapshot snapshot = snapshot(false);
        PayRefundDO first = reserveRefund(snapshot);
        service.onRefundResult(first, PayRefundRespDTO.failureOf(first.getNo(), Map.of()));

        PayRefundDO second = new PayRefundDO().setId(999L).setNo("R-2").setAppId(1L).setRefundPrice(250);
        service.prepareRefund(second, payOrder(snapshot), channel,
                List.of(new PayRefundFundAllocationDTO().setFundDetailId(snapshot.detail().getId()).setAmount(250)));

        assertThat(refundFundMapper.selectListByRefundNo("R-2")).singleElement()
                .satisfies(row -> assertThat(row.getDivideDetailNo()).isNull());
        verify(client, never()).applyDivideBack(anyString(), anyString(), anyString(), anyString(),
                anyList(), anyString(), anyString());
    }

    private void assertNewRefundCannotReturnReservedMoney(Snapshot snapshot) {
        PayRefundDO second = new PayRefundDO().setId(999L).setNo("R-2").setAppId(1L).setRefundPrice(250);

        assertThrows(IllegalArgumentException.class, () -> service.prepareRefund(second, payOrder(snapshot), channel,
                List.of(new PayRefundFundAllocationDTO().setFundDetailId(snapshot.detail().getId()).setAmount(250))));

        assertThat(refundFundMapper.selectListByRefundNo("R-2")).isEmpty();
        verify(client, never()).applyDivideBack(anyString(), anyString(), anyString(), anyString(),
                anyList(), anyString(), anyString());
    }

    @Test
    void concurrentReturnSuccess_countsReturnedMoneyOnce() throws Exception {
        Snapshot snapshot = snapshot(true);
        PayRefundDO refund = reserveRefund(snapshot);
        service.prepareReturnAttempt(refund.getId());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> { await(start); service.completeReturnAttempt(refund.getId(), returnSuccess()); });
            Future<?> second = executor.submit(() -> { await(start); service.completeReturnAttempt(refund.getId(), returnSuccess()); });
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
            assertThat(fundDetailMapper.selectById(snapshot.detail().getId()).getReturnedAmount()).isEqualTo(100);
            assertThat(refundFundMapper.selectByRefundId(refund.getId()).getDivideBackStatus())
                    .isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private Snapshot snapshot(boolean divided) {
        PayFundOrderDO order = new PayFundOrderDO().setNo("F-1").setAppId(1L).setChannelId(10L)
                .setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode()).setPayOrderId(30L).setPayOrderNo("P-1")
                .setChannelOrderNo("YOP-1").setAmount(300).setPrice(300).setRefundPrice(0)
                .setClearingStatus(PayFundOrderStatusEnum.SUCCESS.getStatus()).setSuccessTime(LocalDateTime.now())
                .setDivideStatus(divided ? PayFundOrderStatusEnum.SUCCESS.getStatus() : PayFundOrderStatusEnum.PENDING.getStatus())
                .setParentMerchantNo("PARENT-OLD").setCollectionMerchantNo("COLLECTOR-OLD");
        fundOrderMapper.insert(order);
        PayFundDetailDO detail = new PayFundDetailDO().setNo("FD-1").setFundOrderId(order.getId()).setFundOrderNo(order.getNo())
                .setPayOrderId(30L).setMerchantId(40L).setMerchantNo("SUB-1").setAmount(300).setPrice(300)
                .setRefundedAmount(0).setReturnedAmount(0)
                .setStatus(divided ? PayFundDetailStatusEnum.SUCCESS.getStatus() : PayFundDetailStatusEnum.PENDING.getStatus())
                .setChannelDetailNo(divided ? "YOP-D-1" : null);
        fundDetailMapper.insert(detail);
        if (divided) {
            divideMapper.insert(new PayDivideRequestDO().setNo("D-1").setRequestNo("DIVIDE-F-1").setFundOrderId(order.getId())
                    .setAppId(1L).setAmount(300).setPrice(300).setStatus(PayDivideRequestStatusEnum.SUCCESS.getStatus()));
        }
        return new Snapshot(order, detail);
    }

    private PayRefundDO reserveRefund(Snapshot snapshot) {
        PayRefundDO refund = refund();
        List<PayRefundFundAllocationDTO> allocations = service.prepareRefund(refund, payOrder(snapshot), channel, allocations(snapshot));
        refundMapper.updateById(new PayRefundDO().setId(refund.getId()).setFundAllocationData(JsonUtils.toJsonString(allocations)));
        return refund;
    }

    private PayRefundDO refund() {
        PayRefundDO refund = new PayRefundDO().setNo("R-1").setAppId(1L).setChannelId(10L)
                .setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode()).setOrderId(30L).setOrderNo("P-1")
                .setMerchantOrderId("M-1").setMerchantRefundId("MR-1").setNotifyUrl("https://business.test/refund")
                .setStatus(PayRefundStatusEnum.WAITING.getStatus()).setPayPrice(300).setRefundPrice(100)
                .setReason("退货").setChannelOrderNo("YOP-1");
        refundMapper.insert(refund);
        return refund;
    }

    private PayOrderDO payOrder(Snapshot snapshot) {
        return new PayOrderDO().setId(snapshot.order().getPayOrderId()).setNo("P-1").setPrice(300);
    }

    private List<PayRefundFundAllocationDTO> allocations(Snapshot snapshot) {
        return List.of(new PayRefundFundAllocationDTO().setFundDetailId(snapshot.detail().getId()).setAmount(100));
    }

    private Map<String, Object> returnSuccess() {
        return Map.of("status", "SUCCESS", "orderId", "P-1", "uniqueOrderNo", "YOP-1", "parentMerchantNo", "PARENT-OLD",
                "merchantNo", "COLLECTOR-OLD", "divideRequestId", "DIVIDE-F-1", "divideBackRequestId", "R-1_BACK",
                "divideBackDetail", List.of(Map.of("divideDetailNo", "YOP-D-1", "amount", "1.00", "status", "SUCCESS")));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test coordination timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record Snapshot(PayFundOrderDO order, PayFundDetailDO detail) { }
    @Test
    void concurrentClearingScansClaimOneQueryWithoutExhaustingRetries() throws Exception {
        Snapshot snapshot = snapshot(false);
        fundOrderMapper.updateById(new PayFundOrderDO().setId(snapshot.order().getId())
                .setClearingStatus(PayFundOrderStatusEnum.PENDING.getStatus()));
        CountDownLatch firstQuery = new CountDownLatch(1);
        CountDownLatch releaseQuery = new CountDownLatch(1);
        AtomicInteger queries = new AtomicInteger();
        when(client.queryClearing("P-1", "YOP-1", "PARENT-OLD", "COLLECTOR-OLD"))
                .thenAnswer(invocation -> {
                    queries.incrementAndGet();
                    firstQuery.countDown();
                    assertThat(releaseQuery.await(5, TimeUnit.SECONDS)).isTrue();
                    return Map.of("clearStatus", "PROCESSING");
                });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> service.processDivide(snapshot.order().getId()));
            assertThat(firstQuery.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> second = executor.submit(() -> service.processDivide(snapshot.order().getId()));
            second.get(2, TimeUnit.SECONDS);
            releaseQuery.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThat(queries).hasValue(1);
            assertThat(fundOrderMapper.selectById(snapshot.order().getId()).getRetryCount()).isEqualTo(1);
            assertThat(divideMapper.selectByFundOrderId(snapshot.order().getId())).isNull();
        } finally {
            releaseQuery.countDown();
            executor.shutdownNow();
        }
    }

}
