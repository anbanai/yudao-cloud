package cn.iocoder.yudao.module.pay.service.payfund;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderDividePlanDTO;
import cn.iocoder.yudao.module.pay.api.refund.dto.PayRefundFundAllocationDTO;
import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.order.PayOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundDetailDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.PayYeepayMerchantAccountDO;
import cn.iocoder.yudao.module.pay.dal.mysql.order.PayOrderMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundDetailMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundOrderMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayDivideRequestMapper;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayDivideRequestDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOrderDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayRefundFundDetailDO;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay.PayYeepayMerchantAccountMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay.PayYeepayEventMapper;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.PayYeepayEventDO;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayRefundFundDetailMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.refund.PayRefundMapper;
import cn.iocoder.yudao.module.pay.dal.dataobject.refund.PayRefundDO;
import cn.iocoder.yudao.module.pay.enums.fund.PayDivideRequestStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayFundDetailStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayFundOrderStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayRefundFundDetailStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayReconciliationRecordStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayYeepayEventStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayYeepayMerchantAccountStatusEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayYeepayOnboardingStatusEnum;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClientConfig;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.dto.refund.PayRefundRespDTO;
import cn.iocoder.yudao.module.pay.dal.redis.no.PayNoRedisDAO;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayFundStatusAndAmountTest extends BaseMockitoUnitTest {

    @Test
    void paymentSuccessWithoutExplicitClearingStateCannotStartDivision() {
        PayFundOrderDO order = new PayFundOrderDO().setId(4L).setChannelId(20L)
                .setPayOrderNo("PAY-4").setChannelOrderNo("YOP-4")
                .setCollectionMerchantNo("COLLECTOR").setParentMerchantNo("PARENT")
                .setSuccessTime(java.time.LocalDateTime.now())
                .setClearingStatus(PayFundOrderStatusEnum.PENDING.getStatus());
        PayChannelDO channel = new PayChannelDO().setId(20L).setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setConfig(new YeepayPayClientConfig());
        YeepayPayClient client = mock(YeepayPayClient.class);
        when(fundOrderMapper.selectById(4L)).thenReturn(order);
        when(channelService.getChannel(20L)).thenReturn(channel);
        when(channelService.getPayClient(20L)).thenReturn(client);
        when(client.queryClearing("PAY-4", "YOP-4", "PARENT", "COLLECTOR"))
                .thenReturn(Map.of("status", "SUCCESS", "code", "OPR00000"));

        fundService.processDivide(4L);

        ArgumentCaptor<PayFundOrderDO> captor = ArgumentCaptor.forClass(PayFundOrderDO.class);
        verify(fundOrderMapper, org.mockito.Mockito.atLeastOnce()).updateById(captor.capture());
        assertEquals(PayFundOrderStatusEnum.PROCESSING.getStatus(), captor.getValue().getClearingStatus());
        verify(divideRequestMapper, never()).insert(any(PayDivideRequestDO.class));
    }

    @InjectMocks
    private PayFundServiceImpl fundService;
    @Mock
    private PayFundOrderMapper fundOrderMapper;
    @Mock
    private PayFundDetailMapper fundDetailMapper;
    @Mock
    private PayDivideRequestMapper divideRequestMapper;
    @Mock
    private PayYeepayMerchantAccountMapper merchantAccountMapper;
    @Mock
    private PayNoRedisDAO noRedisDAO;
    @Mock
    private PayOrderMapper payOrderMapper;
    @Mock
    private PayYeepayEventMapper yeepayEventMapper;
    @Mock
    private PayRefundMapper refundMapper;
    @Mock
    private PayRefundFundDetailMapper refundFundDetailMapper;
    @Mock
    private PayChannelService channelService;

    @BeforeEach
    void useDirectTransactionMethodsInMockitoTests() {
        ReflectionTestUtils.setField(fundService, "self", fundService);
    }

    @Test
    void repeatedPaymentSuccessDoesNotResetCompletedFunds() {
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(2L).setPayOrderId(1L)
                .setStatus(PayFundOrderStatusEnum.SUCCESS.getStatus())
                .setClearingStatus(PayFundOrderStatusEnum.SUCCESS.getStatus())
                .setDivideStatus(PayFundOrderStatusEnum.SUCCESS.getStatus());
        when(fundOrderMapper.selectByPayOrderId(1L)).thenReturn(fundOrder);
        fundService.onPaymentSuccess(new PayOrderDO().setId(1L)
                .setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode()), null);
        ArgumentCaptor<PayFundOrderDO> update = ArgumentCaptor.forClass(PayFundOrderDO.class);
        verify(fundOrderMapper).updateById(update.capture());
        assertNull(update.getValue().getClearingStatus());
        assertNull(update.getValue().getDivideStatus());
    }

    @Test
    void prepareRefund_yeepayRejectsMissingAllocations() {
        assertYeepayRefundRejectsAbsentAllocation(null);
    }

    @Test
    void prepareRefund_yeepayRejectsEmptyAllocations() {
        assertYeepayRefundRejectsAbsentAllocation(List.of());
    }

    private void assertYeepayRefundRejectsAbsentAllocation(List<PayRefundFundAllocationDTO> allocations) {
        when(fundOrderMapper.selectByPayOrderId(3L)).thenReturn(new PayFundOrderDO().setId(4L));
        when(fundDetailMapper.selectListByFundOrderId(4L)).thenReturn(List.of(new PayFundDetailDO()
                .setId(5L).setMerchantId(6L).setTradeOrderSubId(7L).setAmount(300)));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> fundService.prepareRefund(
                new PayRefundDO().setId(2L).setRefundPrice(100), new PayOrderDO().setId(3L),
                new PayChannelDO().setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode()), allocations));

        assertEquals("易宝退款必须提供商家资金分配", failure.getMessage());
        verify(refundFundDetailMapper, never()).insert(any(PayRefundFundDetailDO.class));
        verify(fundDetailMapper, never()).updateById(any(PayFundDetailDO.class));
    }

    @Test
    void refundAllocationMustMatchEveryProvidedBusinessReference() {
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(4L);
        PayFundDetailDO detail = new PayFundDetailDO().setId(5L).setMerchantId(6L)
                .setTradeOrderSubId(7L).setAmount(300).setMerchantNo("SUB-6")
                .setStatus(PayFundDetailStatusEnum.PENDING.getStatus());
        when(fundOrderMapper.selectByPayOrderId(3L)).thenReturn(fundOrder);
        when(fundDetailMapper.selectListByFundOrderId(4L)).thenReturn(List.of(detail));
        assertThrows(IllegalArgumentException.class, () -> fundService.prepareRefund(
                new PayRefundDO().setId(2L).setRefundPrice(100), new PayOrderDO().setId(3L),
                new PayChannelDO().setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode()),
                List.of(new PayRefundFundAllocationDTO().setFundDetailId(5L).setMerchantId(999L).setAmount(100))));
        verify(refundFundDetailMapper, never()).insert(any(PayRefundFundDetailDO.class));
    }

    @Test
    void partialRefundNotificationChecksRefundAmountInsteadOfOriginalTotal() {
        PayRefundDO refund = new PayRefundDO().setId(2L).setOrderId(3L).setNo("R-2").setRefundPrice(100);
        PayFundOrderDO order = new PayFundOrderDO().setId(4L).setPayOrderNo("P-3").setAmount(300)
                .setCollectionMerchantNo("COLLECTOR").setParentMerchantNo("PARENT");
        when(refundMapper.selectByNo("R-2")).thenReturn(refund);
        when(fundOrderMapper.selectByPayOrderId(3L)).thenReturn(order);
        when(refundFundDetailMapper.selectListByRefundNo("R-2"))
                .thenReturn(List.of(new PayRefundFundDetailDO().setAmount(100)));
        assertTrue(fundService.validateRefundNotify(Map.of("orderId", "P-3", "refundRequestId", "R-2",
                "merchantNo", "COLLECTOR", "parentMerchantNo", "PARENT", "refundAmount", "1.00")));
    }

    @Test
    void positiveAmountsAreAcceptedAndInvalidAmountsRejected() {
        assertTrue(PayFundAmountValidator.isPositive(1));
        assertTrue(PayFundAmountValidator.isPositive(Integer.MAX_VALUE));
        assertFalse(PayFundAmountValidator.isPositive(null));
        assertFalse(PayFundAmountValidator.isPositive(0));
        assertFalse(PayFundAmountValidator.isPositive(-1));

        assertDoesNotThrow(() -> PayFundAmountValidator.requirePositive(1));
        assertThrows(IllegalArgumentException.class, () -> PayFundAmountValidator.requirePositive(null));
        assertThrows(IllegalArgumentException.class, () -> PayFundAmountValidator.requirePositive(0));
        assertThrows(IllegalArgumentException.class, () -> PayFundAmountValidator.requirePositive(-1));
    }

    @Test
    void isRefundReady_returnsFalseWhenRefundHasNoFundDetails() {
        when(refundMapper.selectById(1L)).thenReturn(new PayRefundDO().setId(1L).setNo("R-1"));
        when(refundFundDetailMapper.selectListByRefundNo("R-1")).thenReturn(List.of());

        assertFalse(fundService.isRefundReady(1L));
    }

    @Test
    void onRefundResult_countsOnlyAnAtomicStatusTransition() {
        PayRefundDO refund = new PayRefundDO().setId(41L).setNo("R-41")
                .setRefundPrice(100).setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode());
        PayRefundFundDetailDO detail = new PayRefundFundDetailDO().setId(42L).setRefundId(41L)
                .setRefundNo("R-41").setFundOrderId(43L).setFundDetailId(44L).setAmount(100)
                .setRefundStatus(PayRefundFundDetailStatusEnum.PENDING.getStatus())
                .setStatus(PayRefundFundDetailStatusEnum.PENDING.getStatus());
        PayRefundFundDetailDO duplicate = new PayRefundFundDetailDO().setId(42L).setRefundId(41L)
                .setRefundNo("R-41").setFundOrderId(43L).setFundDetailId(44L).setAmount(100)
                .setRefundStatus(PayRefundFundDetailStatusEnum.PENDING.getStatus())
                .setStatus(PayRefundFundDetailStatusEnum.PENDING.getStatus());
        PayRefundRespDTO notify = PayRefundRespDTO.successOf("Y-REFUND-41", null, "R-41", Map.of());
        when(refundFundDetailMapper.selectListByRefundNo("R-41")).thenReturn(List.of(detail), List.of(duplicate));
        when(refundFundDetailMapper.updateRefundStatusIfNotSuccess(any())).thenReturn(1, 0);

        fundService.onRefundResult(refund, notify);
        fundService.onRefundResult(refund, notify);

        verify(fundDetailMapper).incrementRefundedAmount(44L, 100);
        verify(fundOrderMapper).incrementRefundPrice(43L, 100);
    }

    @Test
    void prepareRefund_reusesPersistedDivideBackRequestIdWhenQueryingProcessingReturn() throws Exception {
        PayRefundDO refund = new PayRefundDO().setId(2L).setNo("R-2").setRefundPrice(100)
                .setReason("售后").setAppId(10L).setChannelId(20L)
                .setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode());
        PayOrderDO order = new PayOrderDO().setId(3L).setNo("P-3").setPrice(100);
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(4L).setNo("F-4")
                .setPayOrderId(3L).setChannelId(20L).setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setChannelOrderNo("Y-4");
        PayFundDetailDO fundDetail = new PayFundDetailDO().setId(5L).setFundOrderId(4L)
                .setMerchantId(6L).setMerchantNo("SUB-6").setAmount(100)
                .setStatus(PayFundDetailStatusEnum.SUCCESS.getStatus()).setChannelDetailNo("CHANNEL-D-5");
        PayRefundFundDetailDO refundDetail = new PayRefundFundDetailDO().setId(7L).setRefundId(2L)
                .setRefundNo("R-2").setFundOrderId(4L).setFundDetailId(5L).setAmount(100)
                .setDivideDetailNo("CHANNEL-D-5").setDivideBackRequestId("R-2-BACK-PERSISTED")
                .setDivideBackStatus(PayRefundFundDetailStatusEnum.PROCESSING.getStatus());
        PayRefundFundAllocationDTO allocation = new PayRefundFundAllocationDTO()
                .setFundDetailId(5L).setAmount(100);
        YeepayPayClientConfig config = new YeepayPayClientConfig().setFullAmountDivideConfirmed(true).setFeeBearerMode("PLATFORM_FEE_ACCOUNT")
                .setCollectionMerchantNo("COLLECTION")
                .setParentMerchantNo("PARENT");
        PayChannelDO channel = new PayChannelDO().setId(20L).setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setConfig(config);
        YeepayPayClient client = mock(YeepayPayClient.class);
        when(channelService.getPayClient(20L)).thenReturn(client);
        when(client.queryDivideBack("Y-4", "R-2-BACK-PERSISTED"))
                .thenReturn(Map.of("status", "PROCESSING"));
        when(fundOrderMapper.selectByPayOrderId(3L)).thenReturn(fundOrder);
        when(fundDetailMapper.selectListByFundOrderId(4L)).thenReturn(List.of(fundDetail));
        when(refundFundDetailMapper.selectByRefundIdAndFundDetailId(2L, 5L)).thenReturn(refundDetail);
        when(refundFundDetailMapper.selectListByFundDetailId(5L)).thenReturn(List.of(refundDetail));

        when(channelService.getChannel(20L)).thenReturn(channel);
        when(refundMapper.selectById(2L)).thenReturn(refund);
        when(fundOrderMapper.selectById(4L)).thenReturn(fundOrder);
        when(refundFundDetailMapper.selectListByRefundNo("R-2")).thenReturn(List.of(refundDetail));
        when(divideRequestMapper.selectByFundOrderId(4L)).thenReturn(new PayDivideRequestDO()
                .setRequestNo("D-4").setStatus(PayDivideRequestStatusEnum.SUCCESS.getStatus()));
        fundService.prepareRefund(refund, order, channel, List.of(allocation));
        fundService.executeRefundPreparation(refund.getId());
        verify(client).queryDivideBack("Y-4", "R-2-BACK-PERSISTED");
        verify(client, never()).applyDivideBack(any(), any(), any(), any());
    }


    @Test
    void fundStatusesExposeExpectedTerminalStateHelpers() {
        assertTrue(PayFundOrderStatusEnum.isPending(PayFundOrderStatusEnum.PENDING.getStatus()));
        assertTrue(PayFundOrderStatusEnum.isSuccess(PayFundOrderStatusEnum.SUCCESS.getStatus()));
        assertTrue(PayFundOrderStatusEnum.isFailed(PayFundOrderStatusEnum.FAILED.getStatus()));
        assertTrue(PayFundOrderStatusEnum.isClosed(PayFundOrderStatusEnum.CLOSED.getStatus()));

        assertTrue(PayFundDetailStatusEnum.isSuccess(PayFundDetailStatusEnum.SUCCESS.getStatus()));
        assertTrue(PayFundDetailStatusEnum.isFailed(PayFundDetailStatusEnum.FAILED.getStatus()));
        assertTrue(PayDivideRequestStatusEnum.isSuccess(PayDivideRequestStatusEnum.SUCCESS.getStatus()));
        assertTrue(PayDivideRequestStatusEnum.isFailed(PayDivideRequestStatusEnum.FAILED.getStatus()));
        assertTrue(PayRefundFundDetailStatusEnum.isSuccess(PayRefundFundDetailStatusEnum.SUCCESS.getStatus()));
        assertTrue(PayRefundFundDetailStatusEnum.isFailed(PayRefundFundDetailStatusEnum.FAILED.getStatus()));
        assertTrue(PayYeepayMerchantAccountStatusEnum.isEnabled(
                PayYeepayMerchantAccountStatusEnum.ENABLED.getStatus()));
        assertTrue(PayYeepayEventStatusEnum.isProcessed(PayYeepayEventStatusEnum.PROCESSED.getStatus()));
        assertTrue(PayReconciliationRecordStatusEnum.isCompleted(
                PayReconciliationRecordStatusEnum.COMPLETED.getStatus()));
    }

    @Test
    void preparePayment_persistsStableDivideDetailNoBeforeCallingYeepay() {
        when(fundOrderMapper.selectByPayOrderId(1L)).thenReturn(null);
        when(noRedisDAO.generate(any(String.class))).thenReturn("F-1", "F-2");
        when(merchantAccountMapper.selectByAppIdAndMerchantId(10L, 20L))
                .thenReturn(new PayYeepayMerchantAccountDO().setMerchantId(20L).setMerchantNo("SUB-20")
                        .setOnboardingStatus(PayYeepayOnboardingStatusEnum.COMPLETED.getStatus())
                        .setStatus(PayYeepayMerchantAccountStatusEnum.ENABLED.getStatus()));
        PayOrderDividePlanDTO plan = new PayOrderDividePlanDTO().setTradeOrderId(100L)
                .setTradeOrderSubId(101L).setMerchantId(20L).setAmount(100);
        PayOrderDO order = new PayOrderDO().setId(1L).setAppId(10L).setPrice(100)
                .setNo("P-1").setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setDividePlanData(JsonUtils.toJsonString(List.of(plan)));
        YeepayPayClientConfig config = new YeepayPayClientConfig().setFullAmountDivideConfirmed(true).setFeeBearerMode("PLATFORM_FEE_ACCOUNT")
                .setCollectionMerchantNo("COLLECTION")
                .setParentMerchantNo("PARENT");
        PayChannelDO channel = new PayChannelDO().setId(2L).setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setConfig(config);

        fundService.preparePayment(order, channel, "PAY-EXT-1");

        ArgumentCaptor<PayFundOrderDO> fundOrderCaptor = ArgumentCaptor.forClass(PayFundOrderDO.class);
        verify(fundOrderMapper).insert(fundOrderCaptor.capture());
        assertEquals("PAY-EXT-1", fundOrderCaptor.getValue().getPayOrderNo());
        ArgumentCaptor<PayFundDetailDO> captor = ArgumentCaptor.forClass(PayFundDetailDO.class);
        verify(fundDetailMapper).insert(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("DD-F-2", captor.getValue().getDivideDetailNo());
    }

    @Test
    void buildPrePayDivideDetail_usesPersistedMerchantAndAmountSnapshots() {
        PayOrderDO order = new PayOrderDO().setId(1L).setPrice(1234)
                // Deliberately different from the persisted details: pre-pay must use the snapshot.
                .setDividePlanData(JsonUtils.toJsonString(List.of(
                        new PayOrderDividePlanDTO().setMerchantId(999L).setMerchantNo("CURRENT-MERCHANT")
                                .setAmount(1234))));
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(11L).setAmount(1234);
        PayFundDetailDO first = new PayFundDetailDO().setId(102L).setMerchantNo("SUB-2").setAmount(234);
        PayFundDetailDO second = new PayFundDetailDO().setId(101L).setMerchantNo("SUB-1").setAmount(1000);
        when(fundOrderMapper.selectByPayOrderId(1L)).thenReturn(fundOrder);
        when(fundDetailMapper.selectListByFundOrderId(11L)).thenReturn(List.of(first, second));

        String divideDetail = fundService.buildPrePayDivideDetail(order);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parsed = (List<Map<String, Object>>) (List<?>)
                JsonUtils.parseArray(divideDetail, Map.class);
        assertEquals(2, parsed.size());
        assertEquals("SUB-1", parsed.get(0).get("ledgerNo"));
        assertEquals("10.00", parsed.get(0).get("amount"));
        assertEquals("SUB-2", parsed.get(1).get("ledgerNo"));
        assertEquals("2.34", parsed.get(1).get("amount"));
    }

    @Test
    void buildPrePayDivideDetail_rejectsSnapshotAmountMismatch() {
        PayOrderDO order = new PayOrderDO().setId(1L).setPrice(1234);
        when(fundOrderMapper.selectByPayOrderId(1L))
                .thenReturn(new PayFundOrderDO().setId(11L).setAmount(1234));
        when(fundDetailMapper.selectListByFundOrderId(11L))
                .thenReturn(List.of(new PayFundDetailDO().setId(101L).setMerchantNo("SUB-1").setAmount(1233)));

        assertThrows(RuntimeException.class, () -> fundService.buildPrePayDivideDetail(order));
    }

    @Test
    void acceptYeepayEvent_sanitizesSensitivePayloadBeforePersistence() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "E-1");
        payload.put("orderId", "P-1");
        payload.put("sign", "private-signature");
        payload.put("openId", "user-open-id");
        payload.put("idCardNo", "110101199001010011");
        payload.put("bankCardNo", "6222000000000000");
        when(yeepayEventMapper.selectByEventId("payment:E-1")).thenReturn(null);

        org.junit.jupiter.api.Assertions.assertTrue(fundService.acceptYeepayEvent("payment", payload));

        ArgumentCaptor<PayYeepayEventDO> captor = ArgumentCaptor.forClass(PayYeepayEventDO.class);
        verify(yeepayEventMapper).insert(captor.capture());
        String storedPayload = captor.getValue().getPayload();
        org.junit.jupiter.api.Assertions.assertFalse(storedPayload.contains("private-signature"));
        org.junit.jupiter.api.Assertions.assertFalse(storedPayload.contains("user-open-id"));
        org.junit.jupiter.api.Assertions.assertFalse(storedPayload.contains("110101199001010011"));
        org.junit.jupiter.api.Assertions.assertFalse(storedPayload.contains("6222000000000000"));
        org.junit.jupiter.api.Assertions.assertTrue(storedPayload.contains("P-1"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getRawDataRef().startsWith("sha256:"));
    }

    @Test
    void acceptYeepayEvent_reopensFailedEventUntilRetryLimit() {
        PayYeepayEventDO existing = new PayYeepayEventDO().setId(9L)
                .setEventId("divide:E-2").setStatus(PayYeepayEventStatusEnum.FAILED.getStatus()).setRetryCount(1);
        when(yeepayEventMapper.selectByEventId("divide:E-2")).thenReturn(existing);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "E-2");

        org.junit.jupiter.api.Assertions.assertTrue(fundService.acceptYeepayEvent("divide", payload));

        ArgumentCaptor<PayYeepayEventDO> captor = ArgumentCaptor.forClass(PayYeepayEventDO.class);
        verify(yeepayEventMapper).updateById(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(PayYeepayEventStatusEnum.PROCESSING.getStatus(),
                captor.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(2, captor.getValue().getRetryCount());
    }

    @Test
    void acceptYeepayEvent_keepsProcessedDuplicateAsAlreadyHandled() {
        PayYeepayEventDO existing = new PayYeepayEventDO().setId(10L)
                .setEventId("payment:E-3").setStatus(PayYeepayEventStatusEnum.PROCESSED.getStatus()).setRetryCount(0);
        when(yeepayEventMapper.selectByEventId("payment:E-3")).thenReturn(existing);

        org.junit.jupiter.api.Assertions.assertFalse(fundService.acceptYeepayEvent("payment", Map.of("eventId", "E-3")));
    }

    @Test
    void eventDigest_isStableWhenMapKeyOrderChanges() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("status", "SUCCESS");
        first.put("orderId", "P-1");
        first.put("details", new LinkedHashMap<>(Map.of("amount", 100, "merchantNo", "M-1")));

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("details", new LinkedHashMap<>(Map.of("merchantNo", "M-1", "amount", 100)));
        second.put("orderId", "P-1");
        second.put("status", "SUCCESS");

        org.junit.jupiter.api.Assertions.assertEquals(
                PayFundServiceImpl.digestEventPayload(first), PayFundServiceImpl.digestEventPayload(second));
    }

    @Test
    void eventExternalId_ignoresCallbackTimestampChanges() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("orderId", "P-1");
        first.put("status", "SUCCESS");
        first.put("successTime", "2026-10-10 12:00:00");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("orderId", "P-1");
        second.put("status", "SUCCESS");
        second.put("successTime", "2026-10-10 12:00:05");

        org.junit.jupiter.api.Assertions.assertEquals(
                PayFundServiceImpl.eventExternalId(first), PayFundServiceImpl.eventExternalId(second));
    }

    @Test
    void eventExternalId_rejectsPayloadWithoutBusinessIdentifier() {
        assertThrows(IllegalArgumentException.class,
                () -> PayFundServiceImpl.eventExternalId(Map.of("signature", "envelope-only")));
        assertThrows(IllegalArgumentException.class,
                () -> PayFundServiceImpl.eventExternalId(Map.of("status", "SUCCESS")));
    }

    @Test
    void processDivide_queriesStableRequestAfterUnknownResult() {
        PayFundOrderDO order = new PayFundOrderDO().setId(51L).setNo("F-51")
                .setSuccessTime(java.time.LocalDateTime.now()).setChannelId(20L)
                .setPayOrderNo("P-51").setChannelOrderNo("Y-51")
                .setClearingStatus(PayFundOrderStatusEnum.SUCCESS.getStatus());
        PayDivideRequestDO request = new PayDivideRequestDO().setId(512L).setFundOrderId(51L)
                .setRequestNo("DIVIDE-F-51").setAmount(100)
                .setStatus(PayDivideRequestStatusEnum.PROCESSING.getStatus()).setRetryCount(1);
        PayChannelDO channel = new PayChannelDO().setId(20L).setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setConfig(new YeepayPayClientConfig());
        YeepayPayClient client = mock(YeepayPayClient.class);
        when(fundOrderMapper.selectById(51L)).thenReturn(order);
        when(channelService.getChannel(20L)).thenReturn(channel);
        when(channelService.getPayClient(20L)).thenReturn(client);
        when(divideRequestMapper.selectByFundOrderId(51L)).thenReturn(request);
        when(fundDetailMapper.selectListByFundOrderId(51L)).thenReturn(List.of(
                new PayFundDetailDO().setId(511L).setAmount(100).setDivideAmount(100)));
        when(client.queryDivide("P-51", "Y-51", "DIVIDE-F-51"))
                .thenReturn(Map.of("status", "PROCESSING"));

        fundService.processDivide(51L);

        verify(client).queryDivide("P-51", "Y-51", "DIVIDE-F-51");
        verify(client, never()).applyDivide(any(), any(), any(), any());
        verify(divideRequestMapper, never()).insert(any(PayDivideRequestDO.class));
        assertEquals(PayDivideRequestStatusEnum.PROCESSING.getStatus(), request.getStatus());
    }

    @Test
    void processDivide_matchesSuccessResponseByMerchantAndAmount() {
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(81L).setNo("F-81")
                .setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setChannelId(81L).setSuccessTime(java.time.LocalDateTime.now())
                .setPayOrderNo("P-81").setChannelOrderNo("Y-81").setAmount(300)
                .setClearingStatus(PayFundOrderStatusEnum.SUCCESS.getStatus());
        PayFundDetailDO first = new PayFundDetailDO().setId(811L).setFundOrderId(81L)
                .setMerchantNo("SUB-1").setAmount(100).setDivideAmount(100);
        PayFundDetailDO second = new PayFundDetailDO().setId(812L).setFundOrderId(81L)
                .setMerchantNo("SUB-2").setAmount(200).setDivideAmount(200);
        PayDivideRequestDO request = new PayDivideRequestDO().setId(813L).setFundOrderId(81L)
                .setRequestNo("DIVIDE-F-81").setAmount(300)
                .setStatus(PayDivideRequestStatusEnum.RETRYING.getStatus()).setRetryCount(1);
        PayChannelDO channel = new PayChannelDO().setId(81L).setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setConfig(new YeepayPayClientConfig().setFullAmountDivideConfirmed(true).setFeeBearerMode("PLATFORM_FEE_ACCOUNT")
                .setCollectionMerchantNo("COLLECTION")
                        .setParentMerchantNo("PARENT"));
        YeepayPayClient client = mock(YeepayPayClient.class);
        when(fundOrderMapper.selectById(81L)).thenReturn(fundOrder);
        when(fundDetailMapper.selectListByFundOrderId(81L)).thenReturn(List.of(first, second));
        when(channelService.getChannel(81L)).thenReturn(channel);
        when(channelService.getPayClient(81L)).thenReturn(client);
        when(divideRequestMapper.selectByFundOrderId(81L)).thenReturn(request);
        when(client.applyDivide(eq("P-81"), eq("Y-81"), eq("DIVIDE-F-81"), any()))
                .thenReturn(new LinkedHashMap<>(Map.of(
                        "status", "SUCCESS",
                        "divideAmount", "3.00",
                        "divideDetail", "[{\"divideDetailNo\":\"CHANNEL-2\",\"ledgerNo\":\"SUB-2\",\"amount\":\"2.00\"},"
                                + "{\"divideDetailNo\":\"CHANNEL-1\",\"ledgerNo\":\"SUB-1\",\"amount\":\"1.00\"}]")));

        fundService.processDivide(81L);

        assertEquals(PayFundDetailStatusEnum.SUCCESS.getStatus(), first.getStatus());
        assertEquals("CHANNEL-1", first.getChannelDetailNo());
        assertEquals(PayFundDetailStatusEnum.SUCCESS.getStatus(), second.getStatus());
        assertEquals("CHANNEL-2", second.getChannelDetailNo());
        assertEquals(PayDivideRequestStatusEnum.SUCCESS.getStatus(), request.getStatus());
        verify(fundDetailMapper, org.mockito.Mockito.times(2)).updateById(any(PayFundDetailDO.class));
    }

    @Test
    void processDivide_rejectsSuccessResponseWithMismatchedMerchantOrAmount() {
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(82L).setNo("F-82")
                .setCollectionMerchantNo("COLLECTION").setParentMerchantNo("PARENT")
                .setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setChannelId(82L).setSuccessTime(java.time.LocalDateTime.now())
                .setPayOrderNo("P-82").setChannelOrderNo("Y-82").setAmount(300)
                .setClearingStatus(PayFundOrderStatusEnum.SUCCESS.getStatus());
        PayFundDetailDO detail = new PayFundDetailDO().setId(821L).setFundOrderId(82L)
                .setMerchantNo("SUB-1").setAmount(300).setDivideAmount(300);
        PayDivideRequestDO request = new PayDivideRequestDO().setId(822L).setFundOrderId(82L)
                .setChannelId(82L).setChannelCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setRequestNo("DIVIDE-F-82").setAmount(300)
                .setStatus(PayDivideRequestStatusEnum.RETRYING.getStatus()).setRetryCount(1);
        PayChannelDO channel = new PayChannelDO().setId(82L).setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode())
                .setConfig(new YeepayPayClientConfig().setFullAmountDivideConfirmed(true).setFeeBearerMode("PLATFORM_FEE_ACCOUNT")
                .setCollectionMerchantNo("COLLECTION")
                        .setParentMerchantNo("PARENT"));
        YeepayPayClient client = mock(YeepayPayClient.class);
        when(fundOrderMapper.selectById(82L)).thenReturn(fundOrder);
        when(fundDetailMapper.selectListByFundOrderId(82L)).thenReturn(List.of(detail));
        when(channelService.getChannel(82L)).thenReturn(channel);
        when(channelService.getPayClient(82L)).thenReturn(client);
        when(divideRequestMapper.selectByFundOrderId(82L)).thenReturn(request);
        when(client.applyDivide(eq("P-82"), eq("Y-82"), eq("DIVIDE-F-82"), any(), eq("PARENT"), eq("COLLECTION")))
                .thenReturn(new LinkedHashMap<>(Map.of(
                        "status", "SUCCESS",
                        "divideAmount", "3.00",
                        "divideDetail", List.of(new LinkedHashMap<>(Map.of(
                                "divideDetailNo", "CHANNEL-1", "ledgerNo", "WRONG-SUB", "amount", "3.00"))))));

        fundService.processDivide(82L);

        assertEquals(PayDivideRequestStatusEnum.PROCESSING.getStatus(), request.getStatus());
        assertNull(detail.getStatus());
        assertNull(detail.getChannelDetailNo());
        verify(fundOrderMapper).updateById(argThat((PayFundOrderDO order) ->
                Objects.equals(order.getId(), 82L)
                        && Objects.equals(order.getDivideStatus(), PayDivideRequestStatusEnum.PROCESSING.getStatus())));
    }

    @Test
    void handleDivideBack_updatesEachMerchantRowByDivideDetailNo() {
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(61L).setNo("F-61")
                .setPayOrderNo("P-61").setChannelOrderNo("Y-61").setAmount(300)
                .setCollectionMerchantNo("COLLECTION").setParentMerchantNo("PARENT");
        PayRefundFundDetailDO first = new PayRefundFundDetailDO().setId(611L).setFundOrderId(61L)
                .setFundDetailId(601L).setDivideDetailNo("CHANNEL-1").setAmount(100);
        PayRefundFundDetailDO second = new PayRefundFundDetailDO().setId(612L).setFundOrderId(61L)
                .setFundDetailId(602L).setDivideDetailNo("CHANNEL-2").setAmount(200);
        when(refundFundDetailMapper.selectListByDivideBackRequestId("BACK-61"))
                .thenReturn(List.of(first, second));
        when(fundOrderMapper.selectById(61L)).thenReturn(fundOrder);
        when(refundFundDetailMapper.updateDivideBackStatusIfNotSuccess(any())).thenReturn(1, 1);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", "Y-61");
        payload.put("merchantNo", "COLLECTION");
        payload.put("parentMerchantNo", "PARENT");
        payload.put("divideBackRequestId", "BACK-61");
        payload.put("status", "SUCCESS");
        payload.put("divideBackDetail", List.of(
                new LinkedHashMap<>(Map.of("divideDetailNo", "CHANNEL-2", "amount", "2.00")),
                new LinkedHashMap<>(Map.of("divideDetailNo", "CHANNEL-1", "amount", "1.00"))));

        fundService.handleDivideBack(payload);

        ArgumentCaptor<PayRefundFundDetailDO> captor = ArgumentCaptor.forClass(PayRefundFundDetailDO.class);
        verify(refundFundDetailMapper, org.mockito.Mockito.times(2)).updateDivideBackStatusIfNotSuccess(captor.capture());
        assertEquals(List.of(612L, 611L), captor.getAllValues().stream()
                .map(PayRefundFundDetailDO::getId).toList());
        verify(fundDetailMapper).incrementReturnedAmount(602L, 200);
        verify(fundDetailMapper).incrementReturnedAmount(601L, 100);
    }

    @Test
    void handleDivideBack_acceptsOfficialAggregateResponseWithoutDetails() {
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(71L).setNo("F-71")
                .setPayOrderNo("P-71").setChannelOrderNo("Y-71").setAmount(300)
                .setCollectionMerchantNo("COLLECTION").setParentMerchantNo("PARENT");
        PayRefundFundDetailDO first = new PayRefundFundDetailDO().setId(711L).setFundOrderId(71L)
                .setFundDetailId(701L).setDivideDetailNo("CHANNEL-1").setAmount(100);
        PayRefundFundDetailDO second = new PayRefundFundDetailDO().setId(712L).setFundOrderId(71L)
                .setFundDetailId(702L).setDivideDetailNo("CHANNEL-2").setAmount(200);
        when(refundFundDetailMapper.selectListByDivideBackRequestId("BACK-71"))
                .thenReturn(List.of(first, second));
        when(fundOrderMapper.selectById(71L)).thenReturn(fundOrder);
        when(refundFundDetailMapper.updateDivideBackStatusIfNotSuccess(any())).thenReturn(1, 1);

        fundService.handleDivideBack(new LinkedHashMap<>(Map.of(
                "orderId", "Y-71", "merchantNo", "COLLECTION", "parentMerchantNo", "PARENT",
                "divideBackRequestId", "BACK-71", "status", "SUCCESS")));

        verify(fundDetailMapper).incrementReturnedAmount(701L, 100);
        verify(fundDetailMapper).incrementReturnedAmount(702L, 200);
    }

    @Test
    void handleDivide_acceptsFirstSuccessCallbackWithNewChannelDetailNosAndShuffledDetails() {
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(11L).setNo("F-11")
                .setPayOrderNo("P-11").setChannelOrderNo("Y-11").setAmount(300)
                .setCollectionMerchantNo("COLLECTION").setParentMerchantNo("PARENT");
        PayDivideRequestDO request = new PayDivideRequestDO().setId(12L).setFundOrderId(11L)
                .setRequestNo("D-11").setAmount(300).setStatus(PayDivideRequestStatusEnum.PROCESSING.getStatus());
        PayFundDetailDO first = new PayFundDetailDO().setId(101L).setFundOrderId(11L)
                .setMerchantNo("SUB-1").setAmount(100).setDivideDetailNo("LOCAL-1");
        PayFundDetailDO second = new PayFundDetailDO().setId(102L).setFundOrderId(11L)
                .setMerchantNo("SUB-2").setAmount(200).setDivideDetailNo("LOCAL-2");
        when(divideRequestMapper.selectByRequestNo("D-11")).thenReturn(request);
        when(fundOrderMapper.selectById(11L)).thenReturn(fundOrder);
        when(fundDetailMapper.selectListByFundOrderId(11L)).thenReturn(List.of(first, second));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", "Y-11");
        payload.put("merchantNo", "COLLECTION");
        payload.put("parentMerchantNo", "PARENT");
        payload.put("divideRequestId", "D-11");
        payload.put("status", "SUCCESS");
        payload.put("amount", "3.00");
        payload.put("divideDetail", List.of(
                new LinkedHashMap<>(Map.of("divideDetailNo", "CHANNEL-2", "ledgerNo", "SUB-2", "amount", "2.00")),
                new LinkedHashMap<>(Map.of("divideDetailNo", "CHANNEL-1", "ledgerNo", "SUB-1", "amount", "1.00"))));

        fundService.handleDivide(payload);

        ArgumentCaptor<PayFundDetailDO> captor = ArgumentCaptor.forClass(PayFundDetailDO.class);
        verify(fundDetailMapper, org.mockito.Mockito.times(2)).updateById(captor.capture());
        Map<Long, String> channelDetailNos = new java.util.HashMap<>();
        for (PayFundDetailDO updated : captor.getAllValues()) {
            channelDetailNos.put(updated.getId(), updated.getChannelDetailNo());
        }
        org.junit.jupiter.api.Assertions.assertEquals("CHANNEL-1", channelDetailNos.get(101L));
        org.junit.jupiter.api.Assertions.assertEquals("CHANNEL-2", channelDetailNos.get(102L));
    }

    @Test
    void handleDivide_acceptsOfficialResponseShapeWithoutRootMerchantFields() {
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(31L).setNo("F-31")
                .setPayOrderNo("P-31").setChannelOrderNo("Y-31").setAmount(300)
                .setCollectionMerchantNo("COLLECTION").setParentMerchantNo("PARENT");
        PayDivideRequestDO request = new PayDivideRequestDO().setId(32L).setFundOrderId(31L)
                .setRequestNo("D-31").setAmount(300).setStatus(PayDivideRequestStatusEnum.PROCESSING.getStatus());
        PayFundDetailDO first = new PayFundDetailDO().setId(301L).setFundOrderId(31L)
                .setMerchantNo("SUB-1").setAmount(100).setDivideDetailNo("LOCAL-1");
        PayFundDetailDO second = new PayFundDetailDO().setId(302L).setFundOrderId(31L)
                .setMerchantNo("SUB-2").setAmount(200).setDivideDetailNo("LOCAL-2");
        when(divideRequestMapper.selectByRequestNo("D-31")).thenReturn(request);
        when(fundOrderMapper.selectById(31L)).thenReturn(fundOrder);
        when(fundDetailMapper.selectListByFundOrderId(31L)).thenReturn(List.of(first, second));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", "Y-31");
        payload.put("divideRequestId", "D-31");
        payload.put("status", "SUCCESS");
        payload.put("divideAmount", "3.00");
        payload.put("divideDetail", "[{\"divideDetailNo\":\"CHANNEL-1\",\"ledgerNo\":\"SUB-1\",\"amount\":\"1.00\"},"
                + "{\"divideDetailNo\":\"CHANNEL-2\",\"ledgerNo\":\"SUB-2\",\"amount\":\"2.00\"}]");

        assertDoesNotThrow(() -> fundService.handleDivide(payload));
        verify(fundDetailMapper, org.mockito.Mockito.times(2)).updateById(any(PayFundDetailDO.class));
    }

    @Test
    void handleDivide_allowsProcessingCallbackWithoutDetails() {
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(21L).setNo("F-21")
                .setPayOrderNo("P-21").setChannelOrderNo("Y-21").setAmount(100)
                .setCollectionMerchantNo("COLLECTION").setParentMerchantNo("PARENT");
        PayDivideRequestDO request = new PayDivideRequestDO().setId(22L).setFundOrderId(21L)
                .setRequestNo("D-21").setAmount(100).setStatus(PayDivideRequestStatusEnum.PROCESSING.getStatus());
        PayFundDetailDO detail = new PayFundDetailDO().setId(201L).setFundOrderId(21L)
                .setMerchantNo("SUB-1").setAmount(100).setDivideDetailNo("LOCAL-1");
        when(divideRequestMapper.selectByRequestNo("D-21")).thenReturn(request);
        when(fundOrderMapper.selectById(21L)).thenReturn(fundOrder);
        when(fundDetailMapper.selectListByFundOrderId(21L)).thenReturn(List.of(detail));

        fundService.handleDivide(new LinkedHashMap<>(Map.of(
                "orderId", "Y-21", "merchantNo", "COLLECTION", "parentMerchantNo", "PARENT",
                "divideRequestId", "D-21", "status", "PROCESSING", "amount", "1.00")));

        ArgumentCaptor<PayFundDetailDO> captor = ArgumentCaptor.forClass(PayFundDetailDO.class);
        verify(fundDetailMapper).updateById(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(PayFundDetailStatusEnum.PROCESSING.getStatus(),
                captor.getValue().getStatus());
    }

    @Test
    void processFailedYeepayEvents_marksPaymentAndRefundEventsIgnoredInsteadOfLeavingProcessing() {
        PayYeepayEventDO event = new PayYeepayEventDO().setId(31L).setEventId("payment:P-31")
                .setEventType("payment").setStatus(PayYeepayEventStatusEnum.RECEIVED.getStatus())
                .setRetryCount(1).setPayload(JsonUtils.toJsonString(Map.of("orderId", "P-31")));
        when(yeepayEventMapper.selectListByStatus(any())).thenReturn(List.of(event));
        when(yeepayEventMapper.claimForProcessing(any(), any())).thenReturn(true);

        assertEquals(1, fundService.processFailedYeepayEvents());

        ArgumentCaptor<PayYeepayEventDO> captor = ArgumentCaptor.forClass(PayYeepayEventDO.class);
        verify(yeepayEventMapper).updateById(captor.capture());
        assertEquals(PayYeepayEventStatusEnum.IGNORED.getStatus(), captor.getValue().getStatus());
        assertTrue(captor.getValue().getLastError().contains("查询任务"));
    }
    @Test
    void cumulativeRefundCannotOverflowIntegerAndBypassOriginalAmount() {
        PayFundOrderDO fundOrder = new PayFundOrderDO().setId(4L);
        PayFundDetailDO detail = new PayFundDetailDO().setId(5L).setMerchantId(6L)
                .setAmount(Integer.MAX_VALUE).setMerchantNo("SUB-6")
                .setStatus(PayFundDetailStatusEnum.PENDING.getStatus());
        when(fundOrderMapper.selectByPayOrderId(3L)).thenReturn(fundOrder);
        when(fundDetailMapper.selectListByFundOrderId(4L)).thenReturn(List.of(detail));
        when(refundFundDetailMapper.selectListByFundDetailId(5L)).thenReturn(List.of(
                new PayRefundFundDetailDO().setId(11L).setAmount(Integer.MAX_VALUE - 20)
                        .setStatus(PayRefundFundDetailStatusEnum.SUCCESS.getStatus())));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> fundService.prepareRefund(
                new PayRefundDO().setId(2L).setRefundPrice(100), new PayOrderDO().setId(3L),
                new PayChannelDO().setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode()),
                List.of(new PayRefundFundAllocationDTO().setFundDetailId(5L).setMerchantId(6L).setAmount(100))));
        assertEquals("累计退款超过商家原分账金额", failure.getMessage());
        verify(refundFundDetailMapper, never()).insert(any(PayRefundFundDetailDO.class));
    }

}
