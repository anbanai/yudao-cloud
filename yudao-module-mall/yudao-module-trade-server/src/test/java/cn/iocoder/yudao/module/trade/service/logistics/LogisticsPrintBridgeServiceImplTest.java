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
import cn.iocoder.yudao.module.trade.enums.logistics.LogisticsPrintTaskStatusEnum;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    }

    @Test
    void authenticate_rejectsTokenBoundToAnotherDeviceCode() {
        TradeLogisticsPrintDeviceDO device = new TradeLogisticsPrintDeviceDO()
                .setDeviceCode("packing-01").setStatus(0);
        when(deviceMapper.selectByTokenHashIgnoreTenant(LogisticsTokenUtils.hash("secret-token"))).thenReturn(device);

        assertThatThrownBy(() -> service.authenticate("secret-token", "packing-02"))
                .isInstanceOf(ServiceException.class);
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

}
