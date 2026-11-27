package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.infra.api.file.dto.FileCreateRespDTO;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.product.api.sku.ProductSkuApi;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.*;
import cn.iocoder.yudao.module.trade.dal.dataobject.delivery.DeliveryExpressDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.*;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderSubMapper;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressService;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsWaybillStatusEnum;
import cn.iocoder.yudao.module.trade.enums.logistics.WechatLogisticsPrintStatusEnum;
import cn.iocoder.yudao.module.trade.enums.logistics.WechatLogisticsWaybillStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfApiException;
import cn.iocoder.yudao.module.trade.framework.logistics.sf.SfLogisticsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogisticsWaybillServiceImplTest {

    @Test
    void createWaybill_doesNotKeepSfSideEffectInsideLocalTransaction() throws Exception {
        Transactional transactional = LogisticsWaybillServiceImpl.class
                .getMethod("createWaybill", LogisticsWaybillCreateReqVO.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNull();
    }

    @Mock private TradeOrderMapper orderMapper;
    @Mock private TradeOrderSubMapper orderSubMapper;
    @Mock private TradeOrderItemMapper itemMapper;
    @Mock private TradeLogisticsAccountMapper accountMapper;
    @Mock private TradeLogisticsWaybillMapper waybillMapper;
    @Mock private TradeWechatLogisticsWaybillMapper wechatWaybillMapper;
    @Mock private TradeLogisticsPrintDeviceMapper deviceMapper;
    @Mock private TradeLogisticsPrintTaskMapper taskMapper;
    @Mock private TradeLogisticsTraceMapper traceMapper;
    @Mock private SfLogisticsClient sfClient;
    @Mock private LogisticsLabelNormalizer labelNormalizer;
    @Mock private FileApi fileApi;
    @Mock private ProductSkuApi productSkuApi;
    @Mock private SfLabelTemplateResolver templateResolver;
    @Mock private TradeMerchantLogisticsConfigService merchantLogisticsConfigService;
    @Mock private DeliveryExpressService deliveryExpressService;
    private LogisticsWaybillServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LogisticsWaybillServiceImpl();
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "orderSubMapper", orderSubMapper);
        ReflectionTestUtils.setField(service, "merchantLogisticsConfigService", merchantLogisticsConfigService);
        ReflectionTestUtils.setField(service, "deliveryExpressService", deliveryExpressService);
        ReflectionTestUtils.setField(service, "orderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "accountMapper", accountMapper);
        ReflectionTestUtils.setField(service, "waybillMapper", waybillMapper);
        ReflectionTestUtils.setField(service, "wechatWaybillMapper", wechatWaybillMapper);
        ReflectionTestUtils.setField(service, "deviceMapper", deviceMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "traceMapper", traceMapper);
        ReflectionTestUtils.setField(service, "sfClient", sfClient);
        ReflectionTestUtils.setField(service, "labelNormalizer", labelNormalizer);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "productSkuApi", productSkuApi);
        TradeOrderSubDO subOrder = new TradeOrderSubDO();
        subOrder.setId(20L);
        subOrder.setOrderId(10L);
        subOrder.setMerchantId(30L);
        subOrder.setMerchantName("商家 A");
        subOrder.setStatus(10);
        subOrder.setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        lenient().when(orderSubMapper.selectById(20L)).thenReturn(subOrder);
        ReflectionTestUtils.setField(service, "templateResolver", templateResolver);
        lenient().when(merchantLogisticsConfigService.getRequiredShippingConfig(30L)).thenReturn(
                new TradeMerchantLogisticsConfigDO().setMonthlyCard("CARD-30").setServiceCode("2")
                        .setSenderName("A 仓").setDefaultWeightKg(java.math.BigDecimal.ONE)
                        .setPaperWidthMm(76).setPaperHeightMm(130).setDpi(203));
        lenient().when(templateResolver.requireTemplate(any(), eq(76), eq(130), eq(203))).thenReturn("template-a");
    }

    @Test
    void getWaybill_onlyDisplaysCardRecordedAtCreation() {
        var legacy = new TradeLogisticsWaybillDO().setId(40L).setMerchantId(30L);
        var recorded = new TradeLogisticsWaybillDO().setId(41L).setMerchantId(30L)
                .setCreateRequestSnapshot("{\"monthlyCard\":\"OLD-1948\"}");
        when(waybillMapper.selectById(40L)).thenReturn(legacy);
        when(waybillMapper.selectById(41L)).thenReturn(recorded);

        assertThat(service.getWaybill(40L).getMerchantMonthlyCardMasked()).isNull();
        assertThat(service.getWaybill(41L).getMerchantMonthlyCardMasked()).isEqualTo("****1948");
        verifyNoInteractions(merchantLogisticsConfigService);
    }

    @Test
    void createWaybill_timeoutPersistsUnknownAndDoesNotCreatePrintTask() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        TradeLogisticsAccountDO account = account();
        TradeLogisticsPrintDeviceDO device = readyDevice();
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(accountMapper.selectDefaultEnabled()).thenReturn(account);
        when(deviceMapper.selectDefaultEnabled(30L)).thenReturn(device);
        when(waybillMapper.selectByOrderIdForUpdate(10L, 20L)).thenReturn(null);
        doAnswer(invocation -> {
            ((TradeLogisticsWaybillDO) invocation.getArgument(0)).setId(3L);
            return 1;
        }).when(waybillMapper).insert(any(TradeLogisticsWaybillDO.class));
        when(sfClient.createWaybill(eq(account), any()))
                .thenThrow(new SfApiException("TIMEOUT", "timeout", true, null));

        var result = service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L).setSubOrderId(20L));

        assertThat(result.getStatus()).isEqualTo(LogisticsWaybillStatusEnum.UNKNOWN.name());
        assertThat(result.getErrorCode()).isEqualTo("TIMEOUT");
        verify(sfClient).createWaybill(eq(account), argThat(payload -> payload.path("monthlyCard").asText().equals("CARD-30")
                && payload.path("contactInfoList").get(0).path("contact").asText().equals("A 仓")));
        verify(waybillMapper).insert(argThat((TradeLogisticsWaybillDO waybill) -> waybill.getMerchantId().equals(30L)));
        verify(taskMapper, never()).insert(any(TradeLogisticsPrintTaskDO.class));
        verify(sfClient, never()).getLabel(any(), anyString(), any());
    }

    @Test
    void siblingSubOrdersUseTheirOwnMerchantSettingsAndDevices() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(orderSubMapper.selectById(21L)).thenReturn(TradeOrderSubDO.builder().id(21L).orderId(10L)
                .merchantId(31L).status(10).refundStatus(TradeOrderRefundStatusEnum.NONE.getStatus()).build());
        when(accountMapper.selectDefaultEnabled()).thenReturn(account());
        when(merchantLogisticsConfigService.getRequiredShippingConfig(31L)).thenReturn(new TradeMerchantLogisticsConfigDO()
                .setMonthlyCard("CARD-31").setServiceCode("1").setSenderName("B 仓").setDefaultWeightKg(BigDecimal.TEN)
                .setPaperWidthMm(100).setPaperHeightMm(150).setDpi(203));
        when(deviceMapper.selectDefaultEnabled(30L)).thenReturn(readyDevice());
        when(deviceMapper.selectDefaultEnabled(31L)).thenReturn(readyDevice().setId(6L).setMerchantId(31L));
        when(templateResolver.requireTemplate(any(), eq(100), eq(150), eq(203))).thenReturn("template-b");
        when(sfClient.createWaybill(any(), any())).thenThrow(new SfApiException("TIMEOUT", "timeout", true, null));

        service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L).setSubOrderId(20L));
        service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L).setSubOrderId(21L));

        var captor = ArgumentCaptor.forClass(TradeLogisticsWaybillDO.class);
        verify(waybillMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting("merchantId", "requestedDeviceId", "paperWidthMm", "templateCode")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(30L, 2L, 76, "template-a"),
                        org.assertj.core.groups.Tuple.tuple(31L, 6L, 100, "template-b"));
        var requests = captor.getAllValues().stream().map(w -> JsonUtils.parseTree(w.getCreateRequestSnapshot())).toList();
        assertThat(requests).extracting(r -> r.path("monthlyCard").asText()).containsExactly("CARD-30", "CARD-31");
        assertThat(requests).extracting(r -> r.path("contactInfoList").get(0).path("contact").asText())
                .containsExactly("A 仓", "B 仓");
        assertThat(requests).extracting(r -> r.path("totalWeight").intValue()).containsExactly(1, 10);
        assertThat(requests).extracting(r -> r.path("expressTypeId").intValue()).containsExactly(2, 1);
    }

    @Test
    void failedWaybillRetriesTheStoredRequestWithoutReloadingMerchantSettings() {
        var waybill = new TradeLogisticsWaybillDO().setId(3L).setOrderId(10L).setSubOrderId(20L).setMerchantId(30L)
                .setStatus("FAILED").setAccountId(1L).setPaperWidthMm(76).setPaperHeightMm(130).setDpi(203)
                .setTemplateCode("original-template").setCreateRequestSnapshot("""
                        {"monthlyCard":"OLD-CARD","orderId":"original","totalWeight":3.5,"expressTypeId":1}
                        """);
        when(waybillMapper.selectByOrderIdForUpdate(10L, 20L)).thenReturn(waybill);
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(new TradeOrderDO().setId(10L).setStatus(10)
                .setDeliveryType(1).setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus()));
        when(deviceMapper.selectDefaultEnabled(30L)).thenReturn(readyDevice());
        var account = account();
        when(accountMapper.selectById(1L)).thenReturn(account);
        when(sfClient.createWaybill(eq(account), any())).thenReturn(new SfLogisticsClient.WaybillResult(
                "original", "SF-1", JsonUtils.parseTree("{}")));
        when(fileApi.isPrivateMasterSupported()).thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(true));
        when(sfClient.getLabel(account, "SF-1", "original-template")).thenReturn(new byte[]{1});
        when(labelNormalizer.normalizePdf(any(), eq(76), eq(130), eq(203))).thenReturn(new byte[]{2});
        when(fileApi.createPrivateFile(any(), eq("SF-1.png"), eq("trade/logistics/9/labels"), eq("image/png")))
                .thenReturn(new FileCreateRespDTO().setId(99L).setUrl("test-label"));
        TenantContextHolder.setTenantId(9L);
        try {
            var result = service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L).setSubOrderId(20L));
            assertThat(result.getJobId()).startsWith("JOB-");
            verify(sfClient).createWaybill(account, JsonUtils.parseTree(waybill.getCreateRequestSnapshot()));
            verifyNoInteractions(merchantLogisticsConfigService, productSkuApi, itemMapper, templateResolver);
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void legacyReprintCanTargetOnlyItsOriginalUnassignedDevice() {
        var waybill = new TradeLogisticsWaybillDO().setId(3L).setMerchantId(30L).setStatus("CREATED")
                .setRequestedDeviceId(2L).setLabelFileId(99L);
        when(waybillMapper.selectByIdForUpdate(3L)).thenReturn(waybill);
        when(deviceMapper.selectById(2L)).thenReturn(readyDevice().setMerchantId(null));
        assertThat(service.reprint(3L, 2L).getDeviceId()).isEqualTo(2L);
        assertThatThrownBy(() -> service.reprint(3L, 7L))
                .isInstanceOf(cn.iocoder.yudao.framework.common.exception.ServiceException.class);
        verify(taskMapper, times(1)).insert(any(TradeLogisticsPrintTaskDO.class));
    }

    @Test
    void createWaybill_refundingOrderIsRejectedBeforeCallingSf() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.PART.getStatus());
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);

        assertThatThrownBy(() -> service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L).setSubOrderId(20L)))
                .isInstanceOf(cn.iocoder.yudao.framework.common.exception.ServiceException.class)
                .extracting("code")
                .isEqualTo(cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants
                        .ORDER_DELIVERY_FAIL_REFUND_STATUS_NOT_NONE.getCode());
        verifyNoInteractions(sfClient);
    }

    @Test
    void createWaybill_existingTaskReturnsSameJobWithoutCallingSfAgain() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(3L).setOrderId(10L)
                .setOrderNo("T10").setStatus(LogisticsWaybillStatusEnum.CREATED.name());
        TradeLogisticsPrintTaskDO task = new TradeLogisticsPrintTaskDO().setJobId("JOB-1")
                .setStatus(LogisticsPrintTaskStatusEnum.PENDING.name());
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(waybillMapper.selectByOrderIdForUpdate(10L, 20L)).thenReturn(waybill);
        when(taskMapper.selectLatestByWaybillIdForUpdate(3L)).thenReturn(task);

        var result = service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L).setSubOrderId(20L));

        assertThat(result.getJobId()).isEqualTo("JOB-1");
        assertThat(result).extracting("reused").isEqualTo(true);
        verifyNoInteractions(sfClient);
    }

    @Test
    void createWaybill_failedTaskCreatesFreshPrintTaskWithoutCallingSfAgain() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(3L).setOrderId(10L)
                .setOrderNo("T10").setWaybillNo("SF1164867434966")
                .setStatus(LogisticsWaybillStatusEnum.CREATED.name()).setLabelFileId(99L)
                .setLabelUrl("https://files.example/label.png").setLabelChecksum("checksum")
                .setPaperWidthMm(100).setPaperHeightMm(150).setDpi(203);
        TradeLogisticsPrintTaskDO failedTask = new TradeLogisticsPrintTaskDO().setId(4L).setJobId("JOB-FAILED")
                .setWaybillId(3L).setStatus(LogisticsPrintTaskStatusEnum.FAILED.name());
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(deviceMapper.selectDefaultEnabled(30L)).thenReturn(readyDevice());
        when(waybillMapper.selectByOrderIdForUpdate(10L, 20L)).thenReturn(waybill);
        when(taskMapper.selectLatestByWaybillIdForUpdate(3L)).thenReturn(failedTask);

        var result = service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L).setSubOrderId(20L));

        assertThat(result.getPrintStatus()).isEqualTo(LogisticsPrintTaskStatusEnum.PENDING.name());
        assertThat(result.getJobId()).isNotEqualTo("JOB-FAILED");
        assertThat(result).extracting("reused").isEqualTo(false);
        verify(taskMapper).insert(argThat((TradeLogisticsPrintTaskDO task) -> task.getWaybillId().equals(3L)
                && task.getLabelFileId().equals(99L)
                && task.getStatus().equals(LogisticsPrintTaskStatusEnum.PENDING.name())));
        verifyNoInteractions(sfClient);
    }

    @Test
    void createWaybill_createdWithoutTaskUsesPrivateStorageWithoutCreatingSfOrderAgain() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        TradeLogisticsAccountDO account = account();
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(3L).setOrderId(10L)
                .setOrderNo("T10").setAccountId(account.getId()).setWaybillNo("SF1164862794041")
                .setStatus(LogisticsWaybillStatusEnum.CREATED.name()).setDeliveryStatus("PENDING");
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(accountMapper.selectById(account.getId())).thenReturn(account);
        when(deviceMapper.selectDefaultEnabled(30L)).thenReturn(readyDevice());
        when(waybillMapper.selectByOrderIdForUpdate(10L, 20L)).thenReturn(waybill);
        when(waybillMapper.selectByIdForUpdate(3L)).thenReturn(waybill);
        when(fileApi.isPrivateMasterSupported()).thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(true));
        when(sfClient.getLabel(account, "SF1164862794041", "fm_existing")).thenReturn(new byte[]{1});
        when(labelNormalizer.normalizePdf(any(), eq(100), eq(150), eq(203))).thenReturn(new byte[]{2, 3});
        when(fileApi.createPrivateFile(any(), eq("SF1164862794041.png"),
                eq("trade/logistics/9/labels"), eq("image/png")))
                .thenReturn(new FileCreateRespDTO().setId(99L).setUrl("https://files.example/label.png"));

        TenantContextHolder.setTenantId(9L);
        try {
            var result = service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L).setSubOrderId(20L));

            assertThat(result.getJobId()).startsWith("JOB-");
            assertThat(waybill.getLabelFileId()).isEqualTo(99L);
            ArgumentCaptor<TradeLogisticsPrintTaskDO> taskCaptor =
                    ArgumentCaptor.forClass(TradeLogisticsPrintTaskDO.class);
            verify(taskMapper).insert(taskCaptor.capture());
            assertThat(taskCaptor.getValue().getLabelFileId()).isEqualTo(99L);
            verify(sfClient, never()).createWaybill(any(), any());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void createWaybill_activeWechatWaybillIsRejected() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(wechatWaybillMapper.selectByOrderIdForUpdate(10L)).thenReturn(new TradeWechatLogisticsWaybillDO()
                .setOrderId(10L).setStatus(WechatLogisticsWaybillStatusEnum.CREATED.name())
                .setPrintStatus(WechatLogisticsPrintStatusEnum.PENDING.name()));

        assertThatThrownBy(() -> service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L).setSubOrderId(20L)))
                .isInstanceOf(cn.iocoder.yudao.framework.common.exception.ServiceException.class)
                .extracting("code")
                .isEqualTo(cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants
                        .WECHAT_LOGISTICS_WAYBILL_ALREADY_EXISTS.getCode());
        verifyNoInteractions(sfClient);
        verifyNoInteractions(accountMapper, deviceMapper);
    }

    @Test
    void createWaybill_cancelledWaybillCreatesNewAttemptWithUniqueProviderOrderNo() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        TradeLogisticsWaybillDO cancelled = new TradeLogisticsWaybillDO().setId(3L).setOrderId(10L)
                .setProviderOrderNo("YD-null-10").setStatus(LogisticsWaybillStatusEnum.CANCELLED.name());
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(accountMapper.selectDefaultEnabled()).thenReturn(account());
        when(deviceMapper.selectDefaultEnabled(30L)).thenReturn(readyDevice());
        when(waybillMapper.selectByOrderIdForUpdate(10L, 20L)).thenReturn(cancelled);
        doAnswer(invocation -> {
            ((TradeLogisticsWaybillDO) invocation.getArgument(0)).setId(4L);
            return 1;
        }).when(waybillMapper).insert(any(TradeLogisticsWaybillDO.class));
        when(sfClient.createWaybill(any(), any()))
                .thenThrow(new SfApiException("REJECTED", "rejected", false, null));

        var result = service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L).setSubOrderId(20L));

        ArgumentCaptor<TradeLogisticsWaybillDO> captor = ArgumentCaptor.forClass(TradeLogisticsWaybillDO.class);
        verify(waybillMapper).insert(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(4L);
        assertThat(captor.getValue().getProviderOrderNo()).startsWith("YD-null-10-")
                .isNotEqualTo(cancelled.getProviderOrderNo());
        assertThat(result.getStatus()).isEqualTo(LogisticsWaybillStatusEnum.FAILED.name());
    }

    @Test
    void createWaybill_deviceWithoutPrinterIsRejectedBeforeCallingSf() {
        TradeOrderDO order = new TradeOrderDO().setId(10L).setNo("T10").setStatus(10).setDeliveryType(1)
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(accountMapper.selectDefaultEnabled()).thenReturn(account());
        when(deviceMapper.selectDefaultEnabled(30L)).thenReturn(new TradeLogisticsPrintDeviceDO()
                .setId(2L).setDeviceCode("device-1").setStatus(0));

        assertThatThrownBy(() -> service.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(10L).setSubOrderId(20L)))
                .hasMessageContaining("未选择打印机");
        verifyNoInteractions(sfClient);
    }

    @Test
    void validateManualDelivery_failedWaybillDoesNotBlockFallback() {
        when(waybillMapper.selectActiveByOrderId(10L)).thenReturn(new TradeLogisticsWaybillDO()
                .setStatus(LogisticsWaybillStatusEnum.FAILED.name()));

        assertThatCode(() -> service.validateManualDelivery(10L, 8L, "SF-MANUAL"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateManualDelivery_unknownWaybillStillBlocksFallback() {
        when(waybillMapper.selectActiveByOrderId(10L)).thenReturn(new TradeLogisticsWaybillDO()
                .setStatus(LogisticsWaybillStatusEnum.UNKNOWN.name()));

        assertThatThrownBy(() -> service.validateManualDelivery(10L, 8L, "SF-MANUAL"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void validateManualDelivery_locksOrderBeforeCheckingActiveWaybill() {
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(new TradeOrderDO().setId(10L));
        when(deliveryExpressService.getDeliveryExpress(8L)).thenReturn(new DeliveryExpressDO().setCode("OTHER"));
        when(waybillMapper.selectActiveByOrderId(10L)).thenReturn(null);

        service.validateManualDelivery(10L, 8L, "SF-MANUAL");

        var inOrder = inOrder(orderMapper, waybillMapper);
        inOrder.verify(orderMapper).selectByIdForUpdate(10L);
        inOrder.verify(waybillMapper).selectActiveByOrderId(10L);
    }

    @Test
    void validateManualDelivery_rejectsSfExpress() {
        when(deliveryExpressService.getDeliveryExpress(8L)).thenReturn(new DeliveryExpressDO().setCode("SF"));

        assertThatThrownBy(() -> service.validateManualDelivery(10L, null, 8L, "SF-MANUAL"))
                .hasMessageContaining("顺丰必须通过顺丰打单流程发货");
        verifyNoInteractions(waybillMapper);
    }

    @Test
    void cancelWaybill_knownFailureKeepsTaskCancelledForManualReconciliation() {
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(3L).setAccountId(1L)
                .setProviderOrderNo("YD-9-10").setStatus(LogisticsWaybillStatusEnum.CREATED.name())
                .setDeliveryStatus("PENDING");
        TradeLogisticsPrintTaskDO task = new TradeLogisticsPrintTaskDO().setId(4L)
                .setStatus(LogisticsPrintTaskStatusEnum.PENDING.name());
        when(waybillMapper.selectByIdForUpdate(3L)).thenReturn(waybill);
        when(taskMapper.selectLatestByWaybillIdForUpdate(3L)).thenReturn(task);
        when(accountMapper.selectById(1L)).thenReturn(account());
        doThrow(new SfApiException("CANCEL_REJECTED", "cancel rejected"))
                .when(sfClient).cancelWaybill(any(), eq("YD-9-10"));

        assertThatThrownBy(() -> service.cancelWaybill(3L)).isInstanceOf(SfApiException.class);

        assertThat(waybill.getStatus()).isEqualTo(LogisticsWaybillStatusEnum.CANCEL_UNKNOWN.name());
        assertThat(task.getStatus()).isEqualTo(LogisticsPrintTaskStatusEnum.CANCELLED.name());
        verify(taskMapper).selectLatestByWaybillIdForUpdate(3L);
    }

    @Test
    void syncTrace_flattensRoutesInsideRouteResponses() {
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(3L).setAccountId(1L)
                .setWaybillNo("SF001").setStatus(LogisticsWaybillStatusEnum.CREATED.name());
        when(waybillMapper.selectById(3L)).thenReturn(waybill);
        when(accountMapper.selectById(1L)).thenReturn(account());
        when(sfClient.queryTrace(any(), eq("SF001"))).thenReturn(JsonUtils.parseTree("""
                {"routeResps":[{"mailNo":"SF001","routes":[
                  {"acceptTime":"2026-09-01 10:00:00","remark":"已揽收","opCode":"50"},
                  {"acceptTime":"2026-09-01 12:00:00","remark":"运输中","opCode":"30"},
                  {"acceptTime":"","remark":"缺少时间","opCode":"30"},
                  {"acceptTime":"2026-09-01 13:00:00","remark":"","opCode":"30"}
                ]}]}
                """));
        when(traceMapper.selectListByWaybillId(3L)).thenReturn(java.util.List.of());

        service.syncTrace(3L);

        ArgumentCaptor<TradeLogisticsTraceDO> captor = ArgumentCaptor.forClass(TradeLogisticsTraceDO.class);
        verify(traceMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(TradeLogisticsTraceDO::getContent)
                .containsExactly("已揽收", "运输中");
        assertThat(captor.getAllValues()).extracting(TradeLogisticsTraceDO::getOperateTime)
                .containsExactly(LocalDateTime.of(2026, 9, 1, 10, 0),
                        LocalDateTime.of(2026, 9, 1, 12, 0));
    }

    @Test
    void syncTrace_supportsLegacyTopLevelRoutes() {
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(3L).setAccountId(1L)
                .setWaybillNo("SF001").setStatus(LogisticsWaybillStatusEnum.CREATED.name());
        when(waybillMapper.selectById(3L)).thenReturn(waybill);
        when(accountMapper.selectById(1L)).thenReturn(account());
        when(sfClient.queryTrace(any(), eq("SF001"))).thenReturn(JsonUtils.parseTree("""
                {"routes":[
                  {"acceptTime":"2026-09-01 10:00:00","remark":"已揽收","opCode":"50"}
                ]}
                """));
        when(traceMapper.selectListByWaybillId(3L)).thenReturn(java.util.List.of());

        service.syncTrace(3L);

        ArgumentCaptor<TradeLogisticsTraceDO> captor = ArgumentCaptor.forClass(TradeLogisticsTraceDO.class);
        verify(traceMapper).insert(captor.capture());
        assertThat(captor.getValue().getContent()).isEqualTo("已揽收");
    }

    @Test
    void syncTrace_existingProviderEventIsNotDeletedOrInsertedAgain() {
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(3L).setAccountId(1L)
                .setWaybillNo("SF001").setStatus(LogisticsWaybillStatusEnum.CREATED.name());
        when(waybillMapper.selectById(3L)).thenReturn(waybill);
        when(accountMapper.selectById(1L)).thenReturn(account());
        when(sfClient.queryTrace(any(), eq("SF001"))).thenReturn(JsonUtils.parseTree("""
                {"routes":[
                  {"id":"ROUTE-1","acceptTime":"2026-09-01 10:00:00","remark":"已揽收","opCode":"50"}
                ]}
                """));
        when(traceMapper.selectByWaybillIdAndProviderEventId(3L, "ROUTE-1"))
                .thenReturn(new TradeLogisticsTraceDO().setId(9L));
        when(traceMapper.selectListByWaybillId(3L)).thenReturn(List.of());

        service.syncTrace(3L);

        verify(traceMapper, never()).delete(TradeLogisticsTraceDO::getWaybillId, 3L);
        verify(traceMapper, never()).insert(any(TradeLogisticsTraceDO.class));
    }

    private TradeLogisticsAccountDO account() {
        return new TradeLogisticsAccountDO().setId(1L).setLogisticsId(8L).setStatus(0)
                .setTemplateCode("fm_existing").setPaperWidthMm(100).setPaperHeightMm(150).setDpi(203);
    }

    private TradeLogisticsPrintDeviceDO readyDevice() {
        return new TradeLogisticsPrintDeviceDO().setMerchantId(30L).setId(2L).setDeviceCode("device-1")
                .setDeviceName("仓库电脑").setPrinterName("Deli GS050DY").setStatus(0);
    }
}
