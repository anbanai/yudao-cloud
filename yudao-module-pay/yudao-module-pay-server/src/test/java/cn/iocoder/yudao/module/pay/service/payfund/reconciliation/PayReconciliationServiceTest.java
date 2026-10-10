package cn.iocoder.yudao.module.pay.service.payfund.reconciliation;

import cn.iocoder.yudao.module.pay.dal.dataobject.channel.PayChannelDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.PayReconciliationDifferenceDO;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.yeepay.PayReconciliationRecordDO;
import cn.iocoder.yudao.module.pay.dal.mysql.channel.PayChannelMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundOrderMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayFundDetailMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayDivideRequestMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.PayRefundFundDetailMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.refund.PayRefundMapper;
import cn.iocoder.yudao.module.pay.dal.dataobject.payfund.PayFundOrderDO;
import cn.iocoder.yudao.module.pay.enums.fund.PayFundOrderStatusEnum;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay.PayReconciliationRecordMapper;
import cn.iocoder.yudao.module.pay.dal.mysql.payfund.yeepay.PayReconciliationDifferenceMapper;
import cn.iocoder.yudao.module.pay.enums.PayChannelEnum;
import cn.iocoder.yudao.module.pay.enums.fund.PayReconciliationRecordStatusEnum;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClient;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay.YeepayPayClientConfig;
import cn.iocoder.yudao.module.pay.service.channel.PayChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PayReconciliationServiceTest {
    private final PayReconciliationService service = new PayReconciliationService();
    private final PayChannelMapper channels = mock(PayChannelMapper.class);
    private final PayFundOrderMapper funds = mock(PayFundOrderMapper.class);
    private final PayFundDetailMapper details = mock(PayFundDetailMapper.class);
    private final PayDivideRequestMapper divides = mock(PayDivideRequestMapper.class);
    private final PayRefundFundDetailMapper refundDetails = mock(PayRefundFundDetailMapper.class);
    private final PayRefundMapper refunds = mock(PayRefundMapper.class);
    private final PayReconciliationRecordMapper records = mock(PayReconciliationRecordMapper.class);
    private final PayReconciliationDifferenceMapper differences = mock(PayReconciliationDifferenceMapper.class);
    private final PayChannelService channelService = mock(PayChannelService.class);
    private final YeepayPayClient client = mock(YeepayPayClient.class);
    private static final LocalDate DATE = LocalDate.of(2026, 10, 10);
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "channelMapper", channels);
        ReflectionTestUtils.setField(service, "fundOrderMapper", funds);
        ReflectionTestUtils.setField(service, "fundDetailMapper", details);
        ReflectionTestUtils.setField(service, "divideRequestMapper", divides);
        ReflectionTestUtils.setField(service, "refundFundDetailMapper", refundDetails);
        ReflectionTestUtils.setField(service, "refundMapper", refunds);
        ReflectionTestUtils.setField(service, "recordMapper", records);
        ReflectionTestUtils.setField(service, "differenceMapper", differences);
        ReflectionTestUtils.setField(service, "channelService", channelService);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-10-11T02:00:00Z"), ZoneId.of("Asia/Shanghai")));
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "reconciliation-unit-test");
        TableInfoHelper.initTableInfo(assistant, PayReconciliationRecordDO.class);
        TableInfoHelper.initTableInfo(assistant, PayReconciliationDifferenceDO.class);
        when(channelService.getPayClient(7L)).thenReturn(client);
        when(client.getConfig()).thenReturn(new YeepayPayClientConfig().setParentMerchantNo("PARENT"));
        when(channels.selectById(7L)).thenReturn(new PayChannelDO().setId(7L).setAppId(1L)
                .setCode(PayChannelEnum.YEEPAY_WX_LITE.getCode()).setStatus(1));
        doAnswer(inv -> { ((PayReconciliationRecordDO) inv.getArgument(0)).setId(11L); return 1; }).when(records).insert(any(PayReconciliationRecordDO.class));
        when(records.update(any(), any())).thenReturn(1);
        when(records.selectPage(any(cn.iocoder.yudao.framework.common.pojo.PageParam.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(new cn.iocoder.yudao.framework.common.pojo.PageResult<>(List.of(), 0L));
    }
    @Test void unknownBillSchema_persistsFinancialAnomalyAndNeverCompletesBatch() {
        // Stub the existing overload too, so the RED failure concerns missing processing rather than a null SDK result.
        when(client.downloadDailyBill(DATE, false, "PARENT")).thenReturn("unknown-schema".getBytes(StandardCharsets.UTF_8));
        service.reconcile(7L, DATE, "PARENT");
        var diff = ArgumentCaptor.forClass(PayReconciliationDifferenceDO.class);
        verify(differences).insert(diff.capture());
        assertEquals("BILL_DOWNLOAD_OR_SCHEMA", diff.getValue().getBizType());
        assertEquals(0, diff.getValue().getStatus());
        var updates = ArgumentCaptor.forClass(PayReconciliationRecordDO.class);
        verify(records, atLeastOnce()).update(updates.capture(), any());
        assertTrue(updates.getAllValues().stream().anyMatch(r -> PayReconciliationRecordStatusEnum.MANUAL_REQUIRED.getStatus().equals(r.getStatus())));
        assertFalse(updates.getAllValues().stream().anyMatch(r -> PayReconciliationRecordStatusEnum.COMPLETED.getStatus().equals(r.getStatus())));
    }
    @Test void dailyJob_includesDisabledChannelAndRunsAfterOfficialAvailability() {
        when(channels.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(List.of(new PayChannelDO().setId(7L).setAppId(1L).setCode("yeepay_wx_lite").setStatus(1)));
        assertEquals(1, service.processDaily(DATE));
        verify(channelService, atLeastOnce()).getPayClient(7L);
        verify(records).insert(any(PayReconciliationRecordDO.class));
    }

    @Test void matchedBills_completeBatchWithChecksumsAndNoRawBill() {
        paymentBills();
        service.reconcile(7L, DATE, "PARENT");
        var updates = ArgumentCaptor.forClass(PayReconciliationRecordDO.class);
        verify(records, atLeastOnce()).update(updates.capture(), any());
        var done = updates.getAllValues().stream().filter(r -> PayReconciliationRecordStatusEnum.COMPLETED.getStatus().equals(r.getStatus())).findFirst().orElseThrow();
        assertEquals(1, done.getMatchedCount()); assertEquals(100L, done.getMatchedAmount());
        assertTrue(done.getRawData().startsWith("tradeSha256="));
        assertFalse(done.getRawData().contains("商品"));
        verify(differences, never()).insert(any(PayReconciliationDifferenceDO.class));
    }

    @Test void unresolvedPriorDifference_keepsMatchingRerunInManualReview() {
        paymentBills(); when(differences.selectCount(any())).thenReturn(1L);
        service.reconcile(7L, DATE, "PARENT");
        assertFinalStatus(PayReconciliationRecordStatusEnum.MANUAL_REQUIRED.getStatus());
        verify(differences, never()).delete(any());
    }

    @Test void clearingQueryWrongAmount_createsUnresolvedAnomalyEvenWhenClearStatusMatches() {
        paymentBills();
        when(client.queryClearing("PAY1", "YOP1", "PARENT", "COLLECT")).thenReturn(Map.of("clearStatus", "SUCCESS",
                "orderId", "PAY1", "parentMerchantNo", "PARENT", "merchantNo", "COLLECT", "uniqueOrderNo", "YOP1", "orderAmount", "2.00"));
        when(differences.selectCount(any())).thenReturn(1L);
        service.reconcile(7L, DATE, "PARENT");
        var difference = ArgumentCaptor.forClass(PayReconciliationDifferenceDO.class);
        verify(differences).insert(difference.capture());
        assertEquals("CLEARING", difference.getValue().getBizType());
        assertEquals(100L, difference.getValue().getDifferenceAmount());
        assertFinalStatus(PayReconciliationRecordStatusEnum.MANUAL_REQUIRED.getStatus());
    }

    @Test void foreignParentAndUnavailableDate_failBeforeDownloading() {
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(7L, DATE, "FOREIGN"));
        assertThrows(IllegalArgumentException.class, () -> service.reconcile(7L, DATE.plusDays(1), "PARENT"));
        verify(client, never()).downloadDailyBill(any(), anyBoolean(), anyString());
    }

    @Test void failedLeaseClaim_doesNotDownloadOrCompleteConcurrentWorker() {
        when(records.update(any(), any())).thenReturn(0);
        assertEquals(11L, service.reconcile(7L, DATE, "PARENT"));
        verify(client, never()).downloadDailyBill(any(), anyBoolean(), anyString());
        verify(records).update(any(), any());
    }

    @Test void manualResolution_requiresAuditAndUsesPendingStatusCompareAndSet() {
        assertThrows(IllegalArgumentException.class, () -> service.resolve(12L, 9L, "", "凭证"));
        when(differences.selectById(12L)).thenReturn(new PayReconciliationDifferenceDO().setId(12L).setStatus(0));
        when(differences.update(any(), any())).thenReturn(1);
        service.resolve(12L, 9L, "已核实", "YOP凭证001");
        var update = ArgumentCaptor.forClass(PayReconciliationDifferenceDO.class);
        verify(differences).update(update.capture(), any());
        assertEquals(9L, update.getValue().getOperatorId());
        assertEquals("YOP凭证001", update.getValue().getExternalEvidence());
        when(differences.update(any(), any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.resolve(12L, 9L, "已核实", "YOP凭证001"));
    }

    private void paymentBills() {
        String trade = "账单日期,2026-10-10,对账单所属商户编号,PARENT\n交易总金额,1.00,退款总金额,0.00\n交易总笔数,1,退款总笔数,0\n交易手续费金额,0.00,退款退回手续费金额,0.00\n"
                + "请求时间,完成时间,收单商户编号,商户请求号,易宝订单号,业务类型,交易类型,金额,手续费,手续费承担方商编,商品名称,对账备注,原收款订单商户请求号,原收款订单易宝订单号,手续费收取类型,支付产品\n"
                + "2026-10-10 01:00:00,2026-10-10 01:00:01,COLLECT,PAY1,YOP1,收款,标准收款,1.00,0,PARENT,商品,备注,,,实收,小程序\n";
        String divide = "账单日期,2026-10-10,对账单所属商户编号,PARENT\n分账总金额,0.00,分账退回总金额,0.00\n分账总笔数,0,分账退回总笔数,0\n"
                + "请求时间,完成时间,收单商户编号,商户收款请求号,易宝收款订单号,商户分账请求号,易宝分账明细单号,分账接收方编号,分账金额,分账说明,分账类型,原商户分账请求号\n";
        when(client.downloadDailyBill(DATE, false, "PARENT")).thenReturn(trade.getBytes(StandardCharsets.UTF_8));
        when(client.downloadDailyBill(DATE, true, "PARENT")).thenReturn(divide.getBytes(StandardCharsets.UTF_8));
        var fund = new PayFundOrderDO().setId(99L).setPayOrderNo("PAY1").setChannelOrderNo("YOP1")
                .setParentMerchantNo("PARENT").setCollectionMerchantNo("COLLECT").setAmount(100)
                .setSuccessTime(LocalDateTime.of(2026, 10, 10, 1, 0, 1)).setClearingStatus(PayFundOrderStatusEnum.SUCCESS.getStatus());
        when(funds.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(fund));
        when(funds.selectByPayOrderNo("PAY1")).thenReturn(fund);
        when(client.queryClearing("PAY1", "YOP1", "PARENT", "COLLECT")).thenReturn(Map.of("clearStatus", "SUCCESS",
                "orderId", "PAY1", "parentMerchantNo", "PARENT", "merchantNo", "COLLECT", "uniqueOrderNo", "YOP1", "orderAmount", "1.00"));
    }
    private void assertFinalStatus(Integer status) {
        var updates = ArgumentCaptor.forClass(PayReconciliationRecordDO.class);
        verify(records, atLeastOnce()).update(updates.capture(), any());
        assertEquals(status, updates.getAllValues().get(updates.getAllValues().size() - 1).getStatus());
    }
}
