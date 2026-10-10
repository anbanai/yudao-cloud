package cn.iocoder.yudao.module.pay.service.refund;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundCreateReqDTO;
import cn.iocoder.yudao.module.pay.api.fund.dto.PayFundRetryReqDTO;
import cn.iocoder.yudao.module.pay.dal.dataobject.app.PayAppDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayRefundFundDetailDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.refund.PayRefundDO;
import cn.iocoder.yudao.module.pay.dal.mysql.refund.PayRefundMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundOrderMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundOperationMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayRefundFundDetailMapper;
import cn.iocoder.yudao.module.pay.dal.redis.no.PayNoRedisDAO;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.enums.order.PayOrderStatusEnum;
import cn.iocoder.yudao.module.pay.enums.refund.PayRefundStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayRefundFundDetailStatusEnum;
import cn.iocoder.yudao.module.pay.framework.pay.config.PayProperties;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.service.app.PayAppService;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import cn.iocoder.yudao.module.pay.service.notify.PayNotifyService;
import cn.iocoder.yudao.module.pay.service.order.PayOrderService;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundService;
import cn.iocoder.yudao.module.pay.service.payfund.PayFundOperationsService;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Import({PayRefundServiceImpl.class, PayFundOperationsService.class})
class PayRefundYeepaySubmissionTest extends BaseDbUnitTest {

    @Resource
    private PayRefundServiceImpl refundService;
    @Resource
    private PayRefundMapper refundMapper;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private PayFundOperationsService operations;
    @Resource
    private PayFundOrderMapper fundOrderMapper;
    @Resource
    private PayRefundFundDetailMapper refundFundMapper;
    @Resource
    private PayFundOperationMapper operationMapper;
    @MockitoBean
    private PayProperties properties;
    @MockitoBean
    private PayNoRedisDAO noRedisDAO;
    @MockitoBean
    private PayOrderService orderService;
    @MockitoBean
    private PayAppService appService;
    @MockitoBean
    private PayChannelService channelService;
    @MockitoBean
    private PayNotifyService notifyService;
    @MockitoBean
    private PayFundService fundService;

    private YeepayPayClient client;

    @BeforeEach
    void setUp() {
        when(properties.getRefundNoPrefix()).thenReturn("R");
        when(properties.getRefundNotifyUrl()).thenReturn("https://platform.test/pay/notify/refund");
        when(noRedisDAO.generate("R")).thenReturn("R-STABLE-1");
        when(appService.validPayApp("demo")).thenReturn(new PayAppDO().setId(1L)
                .setRefundNotifyUrl("https://business.test/refund"));
        PayOrderDO order = new PayOrderDO().setId(30L).setAppId(1L).setMerchantOrderId("ORDER-1")
                .setNo("P-STABLE-1").setPrice(300).setRefundPrice(0)
                .setStatus(PayOrderStatusEnum.SUCCESS.getStatus()).setChannelId(10L)
                .setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode()).setChannelOrderNo("YOP-ORDER-1");
        when(orderService.getOrder(1L, "ORDER-1")).thenReturn(order);
        when(orderService.getOrder(30L)).thenReturn(order);
        PayChannelDO channel = new PayChannelDO().setId(10L).setAppId(1L)
                .setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode());
        channel.setTenantId(1L);
        when(channelService.validPayChannel(10L)).thenReturn(channel);
        when(channelService.validExistingPayChannel(10L)).thenReturn(channel);
        client = mock(YeepayPayClient.class);
        when(channelService.getPayClient(10L)).thenReturn(client);
        when(fundService.prepareRefund(any(), any(), any(), any())).thenReturn(List.of());
        when(fundService.executeRefundPreparation(anyLong())).thenReturn(true);
        when(fundService.isRefundReady(anyLong())).thenReturn(true);
        when(fundService.getFundOrderByPayOrderId(30L)).thenReturn(new PayFundOrderDO().setChannelId(10L)
                .setParentMerchantNo("ORIGINAL-PARENT").setCollectionMerchantNo("ORIGINAL-COLLECTOR"));
        when(client.unifiedRefund(any())).thenReturn(PayRefundRespDTO.waitingOf("YOP-REFUND-1", "R-STABLE-1", null));
    }

    @Test
    void createRefund_sendsOnlyAfterOuterTransactionCommits() {
        AtomicBoolean networkHasTransaction = new AtomicBoolean(true);
        AtomicReference<PayRefundDO> committedRefund = new AtomicReference<>();
        when(client.unifiedRefund(any())).thenAnswer(invocation -> {
            // Submission catches uncertain outcomes, so assert observations after the call returns.
            networkHasTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
            ExecutorService reader = Executors.newSingleThreadExecutor();
            try {
                committedRefund.set(reader.submit(() -> refundMapper.selectByNo("R-STABLE-1"))
                        .get(5, TimeUnit.SECONDS));
            } finally {
                reader.shutdownNow();
            }
            return PayRefundRespDTO.waitingOf("YOP-REFUND-1", "R-STABLE-1", null);
        });
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            refundService.createRefund(request());
            verify(client, never()).unifiedRefund(any());
        });
        verify(client).unifiedRefund(argThat(request -> "R-STABLE-1".equals(request.getOutRefundNo())
                && "ORIGINAL-PARENT".equals(request.getParentMerchantNo())
                && "ORIGINAL-COLLECTOR".equals(request.getCollectionMerchantNo())));
        assertThat(networkHasTransaction).isFalse();
        assertThat(committedRefund.get()).isNotNull().satisfies(refund -> {
            assertThat(refund.getStatus()).isEqualTo(PayRefundStatusEnum.WAITING.getStatus());
            assertThat(refund.getRequestSubmittedTime()).isNotNull();
        });
    }

    @Test
    void createRefund_waitingResponsePersistsProviderRefundNumber() {
        Long refundId = refundService.createRefund(request());

        assertThat(refundMapper.selectById(refundId).getChannelRefundNo()).isEqualTo("YOP-REFUND-1");
        assertThat(refundMapper.selectById(refundId).getStatus()).isEqualTo(PayRefundStatusEnum.WAITING.getStatus());
    }

    @Test
    void createRefund_rolledBackOuterTransactionDoesNotSend() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            refundService.createRefund(request());
            status.setRollbackOnly();
        });

        assertThat(refundMapper.selectByNo("R-STABLE-1")).isNull();
        verify(client, never()).unifiedRefund(any());
    }

    @Test
    void createRefund_originalDisabledChannelCanRefund() {
        when(channelService.validPayChannel(10L)).thenThrow(new IllegalStateException("disabled for new payments"));

        Long refundId = refundService.createRefund(request());

        assertThat(refundMapper.selectById(refundId)).isNotNull();
        verify(client).unifiedRefund(any());
    }

    @Test
    void createRefund_fundBackTimeoutKeepsStableRefund() {
        when(fundService.executeRefundPreparation(anyLong())).thenThrow(new IllegalStateException("provider timeout"));

        Long refundId = refundService.createRefund(request());

        assertThat(refundMapper.selectById(refundId).getNo()).isEqualTo("R-STABLE-1");
        verify(fundService).executeRefundPreparation(refundId);
        verify(client, never()).unifiedRefund(any());
    }

    @Test
    void createRefund_unknownProviderResultOnlyQueriesOriginalRefundOnRetry() {
        when(client.unifiedRefund(any())).thenThrow(new IllegalStateException("provider timed out after acceptance"));
        when(client.getRefund("P-STABLE-1", "R-STABLE-1", "ORIGINAL-PARENT", "ORIGINAL-COLLECTOR", 100))
                .thenReturn(PayRefundRespDTO.waitingOf(null, "R-STABLE-1", null));

        Long refundId = refundService.createRefund(request());
        refundService.retryRefund(refundId);
        refundService.retryRefund(refundId);

        assertThat(refundMapper.selectById(refundId).getNo()).isEqualTo("R-STABLE-1");
        verify(client, times(1)).unifiedRefund(any());
        verify(client, times(2)).getRefund("P-STABLE-1", "R-STABLE-1", "ORIGINAL-PARENT", "ORIGINAL-COLLECTOR", 100);
    }

    @Test
    void createRefund_missingOriginalSnapshotDoesNotConsumeSubmissionPermit() {
        when(fundService.getFundOrderByPayOrderId(30L)).thenReturn(null);

        Long refundId = refundService.createRefund(request());

        assertThat(refundMapper.selectById(refundId).getRequestSubmittedTime()).isNull();
        verify(fundService, never()).executeRefundPreparation(anyLong());
        verify(client, never()).unifiedRefund(any());
        verify(client, never()).getRefund(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void manualRetry_failedSubmittedRefundQueriesOriginalIdAndSettlesVerifiedLateSuccess() {
        PayRefundDO failed = failedReturnedRefund();
        AtomicReference<PayRefundDO> committed = new AtomicReference<>();
        AtomicReference<Long> committedAuditCount = new AtomicReference<>();
        AtomicBoolean queryHasTransaction = new AtomicBoolean(true);
        when(client.getRefund("P-STABLE-1", "R-STABLE-1", "ORIGINAL-PARENT", "ORIGINAL-COLLECTOR", 100))
                .thenAnswer(invocation -> {
                    queryHasTransaction.set(TransactionSynchronizationManager.isActualTransactionActive());
                    ExecutorService reader = Executors.newSingleThreadExecutor();
                    try {
                        committed.set(reader.submit(() -> refundMapper.selectById(failed.getId())).get(5, TimeUnit.SECONDS));
                        committedAuditCount.set(reader.submit(() -> operationMapper.selectCount()).get(5, TimeUnit.SECONDS));
                    } finally {
                        reader.shutdownNow();
                    }
                    return PayRefundRespDTO.successOf("YOP-REFUND-1", LocalDateTime.now(), "R-STABLE-1", Map.of());
                });
        clearInvocations(fundService, orderService, notifyService);

        operations.retryRefund(retryCommand(failed.getId()));

        assertThat(queryHasTransaction).isFalse();
        assertThat(committedAuditCount.get()).isEqualTo(1L);
        assertThat(committed.get()).isNotNull().satisfies(refund -> {
            assertThat(refund.getStatus()).isEqualTo(PayRefundStatusEnum.WAITING.getStatus());
            assertThat(refund.getRequestSubmittedTime()).isEqualTo(failed.getRequestSubmittedTime());
        });
        PayRefundDO recovered = refundMapper.selectById(failed.getId());
        assertThat(recovered.getStatus()).isEqualTo(PayRefundStatusEnum.SUCCESS.getStatus());
        assertThat(recovered.getNo()).isEqualTo("R-STABLE-1");
        assertThat(recovered.getRequestSubmittedTime()).isEqualTo(failed.getRequestSubmittedTime());
        verify(orderService).updateOrderRefundPrice(30L, 100);
        verify(fundService).onRefundResult(any(), argThat(result -> PayRefundStatusEnum.isSuccess(result.getStatus())));
        verify(client, times(1)).unifiedRefund(any());
        verify(fundService, never()).executeRefundPreparation(anyLong());
        assertOriginalReturnPreserved(failed.getId());
    }

    @Test
    void manualRetry_repeatedProviderFailureKeepsSubmittedIdAndQueriesAfterCommittedAudit() {
        PayRefundDO failed = failedReturnedRefund();
        List<Integer> queryStates = new ArrayList<>();
        when(client.getRefund("P-STABLE-1", "R-STABLE-1", "ORIGINAL-PARENT", "ORIGINAL-COLLECTOR", 100))
                .thenAnswer(invocation -> {
                    queryStates.add(refundMapper.selectById(failed.getId()).getStatus());
                    return PayRefundRespDTO.failureOf("R-STABLE-1", Map.of());
                });
        clearInvocations(fundService);

        operations.retryRefund(retryCommand(failed.getId()));
        operations.retryRefund(retryCommand(failed.getId()));

        assertThat(queryStates).containsExactly(PayRefundStatusEnum.WAITING.getStatus(), PayRefundStatusEnum.WAITING.getStatus());
        PayRefundDO persisted = refundMapper.selectById(failed.getId());
        assertThat(persisted.getStatus()).isEqualTo(PayRefundStatusEnum.FAILURE.getStatus());
        assertThat(persisted.getRequestSubmittedTime()).isEqualTo(failed.getRequestSubmittedTime());
        assertThat(operationMapper.selectCount()).isEqualTo(2L);
        verify(client, times(1)).unifiedRefund(any());
        verify(client, times(2)).getRefund("P-STABLE-1", "R-STABLE-1", "ORIGINAL-PARENT", "ORIGINAL-COLLECTOR", 100);
        verify(fundService, never()).executeRefundPreparation(anyLong());
        assertOriginalReturnPreserved(failed.getId());
    }

    @Test
    void manualRetry_failedRefundWithoutSubmissionEvidenceRequiresManualResolutionAndNeverSends() {
        PayRefundDO failed = failedReturnedRefund();
        refundMapper.update(null, new LambdaUpdateWrapper<PayRefundDO>().eq(PayRefundDO::getId, failed.getId())
                .set(PayRefundDO::getRequestSubmittedTime, null).set(PayRefundDO::getChannelRefundNo, null));
        clearInvocations(client, fundService);

        operations.retryRefund(retryCommand(failed.getId()));

        assertThat(operationMapper.selectCount()).isEqualTo(1L);
        assertThat(refundMapper.selectById(failed.getId()).getStatus()).isEqualTo(PayRefundStatusEnum.FAILURE.getStatus());
        assertThat(refundMapper.selectById(failed.getId()).getRequestSubmittedTime()).isNull();
        PayRefundFundDetailDO row = refundFundMapper.selectByRefundId(failed.getId());
        assertThat(row.getStatus()).isEqualTo(PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus());
        assertThat(row.getLastError()).contains("提交凭证", "人工核实");
        verifyNoInteractions(client, fundService);
        assertOriginalReturnPreserved(failed.getId());
    }

    @Test
    void manualRetry_missingOperatorEvidenceRollsBackWithoutRearmingFailedRefund() {
        PayRefundDO failed = failedReturnedRefund();
        clearInvocations(client, fundService);

        assertThrows(IllegalArgumentException.class,
                () -> operations.retryRefund(retryCommand(failed.getId()).setExternalEvidence(" ")));

        assertThat(operationMapper.selectCount()).isZero();
        assertThat(refundMapper.selectById(failed.getId()).getStatus()).isEqualTo(PayRefundStatusEnum.FAILURE.getStatus());
        verifyNoInteractions(client, fundService);
        assertOriginalReturnPreserved(failed.getId());
    }

    private PayRefundDO failedReturnedRefund() {
        Long refundId = refundService.createRefund(request());
        refundService.notifyRefund(new PayChannelDO().setId(10L).setAppId(1L),
                PayRefundRespDTO.failureOf("R-STABLE-1", Map.of()));
        PayFundOrderDO fundOrder = new PayFundOrderDO().setNo("F-1").setAppId(1L).setChannelId(10L)
                .setPayOrderId(30L).setPayOrderNo("P-STABLE-1").setAmount(300).setPrice(300);
        fundOrderMapper.insert(fundOrder);
        refundFundMapper.insert(new PayRefundFundDetailDO().setNo("RF-1").setRefundId(refundId)
                .setRefundNo("R-STABLE-1").setFundOrderId(fundOrder.getId()).setAppId(1L).setAmount(100).setPrice(100)
                .setDivideDetailNo("YOP-D-1").setDivideBackRequestId("R-STABLE-1_BACK")
                .setDivideBackStatus(PayRefundFundDetailStatusEnum.SUCCESS.getStatus())
                .setRefundStatus(PayRefundFundDetailStatusEnum.FAILED.getStatus())
                .setStatus(PayRefundFundDetailStatusEnum.MANUAL_REQUIRED.getStatus()));
        return refundMapper.selectById(refundId);
    }

    private PayFundRetryReqDTO retryCommand(Long refundId) {
        return new PayFundRetryReqDTO().setTargetId(refundId).setOperatorId(9L)
                .setReason("核实原退款请求").setExternalEvidence("provider-evidence-42");
    }

    private void assertOriginalReturnPreserved(Long refundId) {
        PayRefundFundDetailDO row = refundFundMapper.selectByRefundId(refundId);
        assertThat(row.getDivideBackStatus()).isEqualTo(PayRefundFundDetailStatusEnum.SUCCESS.getStatus());
        assertThat(row.getDivideBackRequestId()).isEqualTo("R-STABLE-1_BACK");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 20})
    void notifyRefund_wrongOriginalChannelDoesNotUpdateRefund(int status) {
        Long refundId = refundService.createRefund(request());
        PayChannelDO differentChannel = new PayChannelDO().setId(11L).setAppId(1L);
        PayRefundRespDTO result = switch (status) {
            case 10 -> PayRefundRespDTO.successOf("SPOOFED-REFUND", LocalDateTime.now(), "R-STABLE-1", Map.of());
            case 20 -> PayRefundRespDTO.failureOf("FAILED", "wrong channel", "R-STABLE-1", Map.of());
            default -> PayRefundRespDTO.waitingOf("SPOOFED-REFUND", "R-STABLE-1", Map.of());
        };
        clearInvocations(fundService, orderService, notifyService);

        assertThrows(ServiceException.class,
                () -> refundService.notifyRefund(differentChannel, result));

        PayRefundDO persisted = refundMapper.selectById(refundId);
        assertThat(persisted.getStatus()).isEqualTo(PayRefundStatusEnum.WAITING.getStatus());
        assertThat(persisted.getChannelRefundNo()).isEqualTo("YOP-REFUND-1");
        verifyNoInteractions(fundService, orderService, notifyService);
    }

    private PayRefundCreateReqDTO request() {
        return new PayRefundCreateReqDTO().setAppKey("demo").setMerchantOrderId("ORDER-1")
                .setMerchantRefundId("BUSINESS-REFUND-1").setPrice(100).setReason("退货").setUserIp("127.0.0.1");
    }

}
