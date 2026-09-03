package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.trade.controller.internal.logistics.vo.PrintBridgeStatusReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintDeviceDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintEventDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintTaskDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsWaybillDO;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintDeviceMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintEventMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsPrintTaskMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.logistics.TradeLogisticsWaybillMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogisticsPrintBridgeServiceImplTest {

    @Mock
    private TradeLogisticsPrintDeviceMapper deviceMapper;
    @Mock
    private TradeLogisticsPrintTaskMapper taskMapper;
    @Mock
    private TradeLogisticsPrintEventMapper eventMapper;
    @Mock
    private TradeLogisticsWaybillMapper waybillMapper;
    @Mock
    private TradeOrderUpdateService orderUpdateService;
    @Mock
    private FileApi fileApi;
    @Mock
    private TradeOrderMapper orderMapper;

    private LogisticsPrintBridgeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LogisticsPrintBridgeServiceImpl();
        ReflectionTestUtils.setField(service, "deviceMapper", deviceMapper);
        ReflectionTestUtils.setField(service, "taskMapper", taskMapper);
        ReflectionTestUtils.setField(service, "eventMapper", eventMapper);
        ReflectionTestUtils.setField(service, "waybillMapper", waybillMapper);
        ReflectionTestUtils.setField(service, "orderUpdateService", orderUpdateService);
        ReflectionTestUtils.setField(service, "fileApi", fileApi);
        ReflectionTestUtils.setField(service, "orderMapper", orderMapper);
    }

    @Test
    void authenticate_rejectsTokenBoundToAnotherDeviceCode() {
        TradeLogisticsPrintDeviceDO device = new TradeLogisticsPrintDeviceDO()
                .setDeviceCode("packing-01").setStatus(0);
        when(deviceMapper.selectByTokenHashIgnoreTenant(LogisticsTokenUtils.hash("secret-token"))).thenReturn(device);

        assertThatThrownBy(() -> service.authenticate("secret-token", "packing-02", "另一台电脑"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void authenticate_firstPollAtomicallyAdoptsPrintBridgeIdentity() {
        TradeLogisticsPrintDeviceDO pending = new TradeLogisticsPrintDeviceDO().setId(9L).setTenantId(3L)
                .setDeviceCode("generated-device-id").setDeviceName("打印工作站-123456").setStatus(0)
                .setEnrollmentKey("ACTIVE")
                .setTokenCreatedTime(LocalDateTime.now());
        when(deviceMapper.selectByTokenHashIgnoreTenant(LogisticsTokenUtils.hash("secret-token")))
                .thenReturn(pending);
        when(deviceMapper.bindPendingDevice(9L, "generated-device-id", "仓库电脑"))
                .thenReturn(1);

        TradeLogisticsPrintDeviceDO actual = service.authenticate("secret-token", "generated-device-id", "仓库电脑");

        assertThat(actual.getDeviceCode()).isEqualTo("generated-device-id");
        assertThat(actual.getDeviceName()).isEqualTo("仓库电脑");
    }

    @Test
    void authenticate_firstPollWithoutOptionalDeviceHeadersUsesGeneratedIdentity() {
        TradeLogisticsPrintDeviceDO pending = new TradeLogisticsPrintDeviceDO().setId(9L).setTenantId(3L)
                .setDeviceCode("generated-device-id").setDeviceName("打印工作站-123456").setStatus(0)
                .setEnrollmentKey("ACTIVE").setTokenCreatedTime(LocalDateTime.now());
        when(deviceMapper.selectByTokenHashIgnoreTenant(LogisticsTokenUtils.hash("secret-token")))
                .thenReturn(pending);
        when(deviceMapper.bindPendingDevice(9L, "generated-device-id", "PrintBridge 设备"))
                .thenReturn(1);

        TradeLogisticsPrintDeviceDO actual = service.authenticate("secret-token", null, null);

        assertThat(actual.getDeviceCode()).isEqualTo("generated-device-id");
        verify(deviceMapper).bindPendingDevice(9L, "generated-device-id", "PrintBridge 设备");
    }

    @Test
    void authenticate_boundTokenAllowsMissingOptionalDeviceHeader() {
        TradeLogisticsPrintDeviceDO bound = new TradeLogisticsPrintDeviceDO()
                .setDeviceCode("bound-device").setStatus(0);
        when(deviceMapper.selectByTokenHashIgnoreTenant(LogisticsTokenUtils.hash("secret-token")))
                .thenReturn(bound);

        assertThat(service.authenticate("secret-token", null, null)).isSameAs(bound);
    }

    @Test
    void authenticate_pendingTokenRejectsDifferentDeviceIdentity() {
        TradeLogisticsPrintDeviceDO pending = new TradeLogisticsPrintDeviceDO().setId(9L).setTenantId(3L)
                .setDeviceCode("configured-device-id").setDeviceName("打印工作站-123456").setStatus(0)
                .setEnrollmentKey("ACTIVE").setTokenCreatedTime(LocalDateTime.now());
        when(deviceMapper.selectByTokenHashIgnoreTenant(LogisticsTokenUtils.hash("secret-token")))
                .thenReturn(pending);

        assertThatThrownBy(() -> service.authenticate("secret-token", "different-device-id", "另一台电脑"))
                .isInstanceOf(ServiceException.class);
        verify(deviceMapper, never()).bindPendingDevice(any(), any(), any());
    }

    @Test
    void authenticate_rejectsExpiredFirstPollEnrollment() {
        TradeLogisticsPrintDeviceDO pending = new TradeLogisticsPrintDeviceDO().setId(9L).setTenantId(3L)
                .setDeviceCode("pending-123").setEnrollmentKey("ACTIVE").setStatus(0)
                .setTokenCreatedTime(LocalDateTime.now().minusMinutes(11));
        when(deviceMapper.selectByTokenHashIgnoreTenant(LogisticsTokenUtils.hash("secret-token")))
                .thenReturn(pending);

        assertThatThrownBy(() -> service.authenticate("secret-token", "generated-device-id", "仓库电脑"))
                .isInstanceOf(ServiceException.class);
        verify(deviceMapper, never()).bindPendingDevice(any(), any(), any());
    }

    @Test
    void authenticate_boundTokenCannotBeReusedByAnotherDevice() {
        TradeLogisticsPrintDeviceDO bound = new TradeLogisticsPrintDeviceDO().setId(9L).setTenantId(3L)
                .setDeviceCode("bound-device").setStatus(0);
        when(deviceMapper.selectByTokenHashIgnoreTenant(LogisticsTokenUtils.hash("secret-token")))
                .thenReturn(bound);

        assertThatThrownBy(() -> service.authenticate("secret-token", "another-device", "另一台电脑"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void authenticate_rejectsOversizedDeviceIdentityBeforeDatabaseLookup() {
        assertThatThrownBy(() -> service.authenticate("secret-token", "x".repeat(129), "仓库电脑"))
                .isInstanceOf(ServiceException.class);

        verifyNoInteractions(deviceMapper);
    }

    @Test
    void report_doesNotWrapSuccessReceiptAndDeliveryInOneTransaction() throws Exception {
        Transactional transactional = LogisticsPrintBridgeServiceImpl.class
                .getMethod("report", TradeLogisticsPrintDeviceDO.class, PrintBridgeStatusReqVO.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNull();
    }

    @Test
    void pull_rejectsPublicOrInsecureLabelUrl() {
        TradeLogisticsPrintDeviceDO device = new TradeLogisticsPrintDeviceDO()
                .setId(1L).setTenantId(9L).setStatus(0);
        TradeLogisticsPrintTaskDO task = new TradeLogisticsPrintTaskDO().setId(2L).setDeviceId(1L)
                .setJobId("JOB-1").setRequestId("REQ-1").setStatus(LogisticsPrintTaskStatusEnum.PENDING.name())
                .setFormat("image").setLabelUrl("http://files.example/label.png")
                .setPaperWidthMm(100).setPaperHeightMm(150).setCopies(1);
        when(taskMapper.selectClaimable(eq(1L), any())).thenReturn(task);
        when(fileApi.isPrivatePresignedGetSupported())
                .thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(true));
        when(fileApi.presignGetUrl(task.getLabelUrl(), 900))
                .thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(task.getLabelUrl()));

        assertThatThrownBy(() -> service.pull(device, "packing-01"))
                .hasRootCauseInstanceOf(ServiceException.class);
    }

    @Test
    void pull_includesConfiguredPrinterName() {
        TradeLogisticsPrintDeviceDO device = new TradeLogisticsPrintDeviceDO()
                .setId(1L).setTenantId(9L).setStatus(0).setPrinterName("Deli GS050DY");
        TradeLogisticsPrintTaskDO task = new TradeLogisticsPrintTaskDO().setId(2L).setDeviceId(1L)
                .setJobId("JOB-1").setRequestId("REQ-1").setStatus(LogisticsPrintTaskStatusEnum.PENDING.name())
                .setFormat("image").setLabelUrl("private://label.png")
                .setPaperWidthMm(76).setPaperHeightMm(130).setCopies(1);
        when(taskMapper.selectClaimable(eq(1L), any())).thenReturn(task);
        when(fileApi.isPrivatePresignedGetSupported())
                .thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(true));
        when(fileApi.presignGetUrl(task.getLabelUrl(), 900))
                .thenReturn(cn.iocoder.yudao.framework.common.pojo.CommonResult.success(
                        "https://files.example/label.png?signature=x&expires=1"));

        var response = service.pull(device, "仓库电脑");

        assertThat(response.getPrinterName()).isEqualTo("Deli GS050DY");
    }

    @Test
    void report_successIsIdempotentAndDeliversOrderOnce() {
        TradeLogisticsPrintDeviceDO device = new TradeLogisticsPrintDeviceDO()
                .setId(1L).setTenantId(9L).setDeviceCode("packing-01").setStatus(0);
        TradeLogisticsPrintTaskDO task = new TradeLogisticsPrintTaskDO().setId(2L).setWaybillId(3L)
                .setDeviceId(1L).setJobId("JOB-1").setStatus(LogisticsPrintTaskStatusEnum.ACCEPTED.name());
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(3L).setOrderId(4L)
                .setWaybillNo("SF123456").setDeliveryStatus("PENDING");
        PrintBridgeStatusReqVO request = new PrintBridgeStatusReqVO().setEvent("status").setEventId("EVENT-1")
                .setJobId("JOB-1").setStatus("success").setMessage("submitted to system print queue");
        when(eventMapper.selectByEventId("EVENT-1")).thenReturn(null, new TradeLogisticsPrintEventDO());
        when(taskMapper.selectByJobIdForUpdate("JOB-1")).thenReturn(task);
        when(waybillMapper.selectByIdForUpdate(3L)).thenReturn(waybill);

        service.report(device, request);
        service.report(device, request);

        verify(eventMapper, times(1)).insert(any(TradeLogisticsPrintEventDO.class));
        verify(taskMapper, times(1)).updateById(any(TradeLogisticsPrintTaskDO.class));
        verify(orderUpdateService, times(1)).deliveryOrder(argThat(delivery ->
                delivery.getId().equals(4L) && delivery.getLogisticsNo().equals("SF123456")));
    }

    @Test
    void report_deliveryFailureDoesNotRejectPersistedPrintSuccess() {
        TradeLogisticsPrintDeviceDO device = new TradeLogisticsPrintDeviceDO()
                .setId(1L).setTenantId(9L).setDeviceCode("packing-01").setStatus(0);
        TradeLogisticsPrintTaskDO task = new TradeLogisticsPrintTaskDO().setId(2L).setWaybillId(3L)
                .setDeviceId(1L).setJobId("JOB-1").setStatus(LogisticsPrintTaskStatusEnum.ACCEPTED.name());
        TradeLogisticsWaybillDO waybill = new TradeLogisticsWaybillDO().setId(3L).setOrderId(4L)
                .setWaybillNo("SF123456").setDeliveryStatus("PENDING");
        PrintBridgeStatusReqVO request = new PrintBridgeStatusReqVO().setEvent("status").setEventId("EVENT-1")
                .setJobId("JOB-1").setStatus("success");
        when(taskMapper.selectByJobIdForUpdate("JOB-1")).thenReturn(task);
        when(waybillMapper.selectByIdForUpdate(3L)).thenReturn(waybill);
        doThrow(new RuntimeException("delivery unavailable")).when(orderUpdateService).deliveryOrder(any());

        service.report(device, request);

        verify(eventMapper).insert(any(TradeLogisticsPrintEventDO.class));
        verify(taskMapper).updateById(argThat((TradeLogisticsPrintTaskDO updated) ->
                LogisticsPrintTaskStatusEnum.SUCCESS.name().equals(updated.getStatus())));
    }

    @Test
    void compensateDeliveredOrders_continuesAfterOneOrderFails() {
        TradeLogisticsPrintTaskDO first = new TradeLogisticsPrintTaskDO().setId(1L).setWaybillId(11L)
                .setStatus(LogisticsPrintTaskStatusEnum.SUCCESS.name()).setTestFlag(false);
        TradeLogisticsPrintTaskDO second = new TradeLogisticsPrintTaskDO().setId(2L).setWaybillId(12L)
                .setStatus(LogisticsPrintTaskStatusEnum.SUCCESS.name()).setTestFlag(false);
        TradeLogisticsWaybillDO firstWaybill = new TradeLogisticsWaybillDO().setId(11L).setOrderId(21L)
                .setLogisticsId(8L).setWaybillNo("SF-1").setDeliveryStatus("PENDING");
        TradeLogisticsWaybillDO secondWaybill = new TradeLogisticsWaybillDO().setId(12L).setOrderId(22L)
                .setLogisticsId(8L).setWaybillNo("SF-2").setDeliveryStatus("PENDING");
        when(taskMapper.selectListByStatus(LogisticsPrintTaskStatusEnum.SUCCESS.name()))
                .thenReturn(java.util.List.of(first, second));
        when(waybillMapper.selectByIdForUpdate(11L)).thenReturn(firstWaybill);
        when(waybillMapper.selectByIdForUpdate(12L)).thenReturn(secondWaybill);
        doThrow(new RuntimeException("first delivery failed")).doNothing()
                .when(orderUpdateService).deliveryOrder(any());

        assertThat(service.compensateDeliveredOrders()).isEqualTo(1);
        verify(orderUpdateService, times(2)).deliveryOrder(any());
    }

}
