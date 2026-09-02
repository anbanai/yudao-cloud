package cn.iocoder.yudao.module.trade.controller.admin.logistics;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillBatchCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillRespVO;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsManagementService;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsWaybillService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.LOGISTICS_ACCOUNT_NOT_EXISTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SfPrintBridgeLogisticsControllerTest {

    @Test
    void batchCreateWaybillContinuesAfterSingleBusinessFailure() {
        LogisticsWaybillService waybillService = mock(LogisticsWaybillService.class);
        SfPrintBridgeLogisticsController controller = new SfPrintBridgeLogisticsController();
        ReflectionTestUtils.setField(controller, "managementService", mock(LogisticsManagementService.class));
        ReflectionTestUtils.setField(controller, "waybillService", waybillService);
        when(waybillService.createWaybill(new LogisticsWaybillCreateReqVO()
                .setOrderId(1L).setAccountId(10L).setDeviceId(20L)))
                .thenThrow(new ServiceException(LOGISTICS_ACCOUNT_NOT_EXISTS));
        when(waybillService.createWaybill(new LogisticsWaybillCreateReqVO()
                .setOrderId(2L).setAccountId(10L).setDeviceId(20L)))
                .thenReturn(new LogisticsWaybillRespVO().setOrderId(2L).setPrintStatus("PENDING"));

        List<LogisticsWaybillRespVO> results = controller.batchCreateWaybill(
                new LogisticsWaybillBatchCreateReqVO().setOrderIds(List.of(1L, 2L))
                        .setAccountId(10L).setDeviceId(20L)).getData();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getOrderId()).isEqualTo(1L);
        assertThat(results.get(0).getErrorMessage()).contains("顺丰物流账号不存在");
        assertThat(results.get(1).getPrintStatus()).isEqualTo("PENDING");
    }

    @Test
    void batchCreateWaybillProcessesDuplicateOrderIdOnce() {
        LogisticsWaybillService waybillService = mock(LogisticsWaybillService.class);
        SfPrintBridgeLogisticsController controller = new SfPrintBridgeLogisticsController();
        ReflectionTestUtils.setField(controller, "managementService", mock(LogisticsManagementService.class));
        ReflectionTestUtils.setField(controller, "waybillService", waybillService);
        when(waybillService.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(1L)))
                .thenReturn(new LogisticsWaybillRespVO().setOrderId(1L).setPrintStatus("PENDING"));
        when(waybillService.createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(2L)))
                .thenReturn(new LogisticsWaybillRespVO().setOrderId(2L).setPrintStatus("PENDING"));

        List<LogisticsWaybillRespVO> results = controller.batchCreateWaybill(
                new LogisticsWaybillBatchCreateReqVO().setOrderIds(List.of(1L, 1L, 2L))).getData();

        assertThat(results).extracting(LogisticsWaybillRespVO::getOrderId).containsExactly(1L, 2L);
        verify(waybillService, times(1)).createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(1L));
        verify(waybillService, times(1)).createWaybill(new LogisticsWaybillCreateReqVO().setOrderId(2L));
    }
}
