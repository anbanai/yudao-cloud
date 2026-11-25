package cn.iocoder.yudao.module.trade.controller.admin.logistics;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillBatchCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillCreateReqVO;
import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.LogisticsWaybillRespVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderSubMapper;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderRefundStatusEnum;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsManagementService;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsWaybillService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
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
    void enrollDevicePreventsCredentialResponseCaching() {
        LogisticsManagementService managementService = mock(LogisticsManagementService.class);
        SfPrintBridgeLogisticsController controller = new SfPrintBridgeLogisticsController();
        ReflectionTestUtils.setField(controller, "managementService", managementService);
        ReflectionTestUtils.setField(controller, "waybillService", mock(LogisticsWaybillService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.enrollDevice(response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verify(managementService).enrollDevice();
    }

    @Test
    void batchCreateWaybillContinuesAfterSingleBusinessFailure() {
        LogisticsWaybillService waybillService = mock(LogisticsWaybillService.class);
        SfPrintBridgeLogisticsController controller = new SfPrintBridgeLogisticsController();
        ReflectionTestUtils.setField(controller, "managementService", mock(LogisticsManagementService.class));
        ReflectionTestUtils.setField(controller, "waybillService", waybillService);
        TradeOrderSubMapper orderSubMapper = mock(TradeOrderSubMapper.class);
        ReflectionTestUtils.setField(controller, "orderSubMapper", orderSubMapper);
        when(orderSubMapper.selectListByOrderId(1L)).thenReturn(List.of(subOrder(11L, 1L, 101L)));
        when(orderSubMapper.selectListByOrderId(2L)).thenReturn(List.of(subOrder(22L, 2L, 202L)));
        when(waybillService.createWaybill(new LogisticsWaybillCreateReqVO()
                .setOrderId(1L).setSubOrderId(11L).setAccountId(10L).setDeviceId(20L)))
                .thenThrow(new ServiceException(LOGISTICS_ACCOUNT_NOT_EXISTS));
        when(waybillService.createWaybill(new LogisticsWaybillCreateReqVO()
                .setOrderId(2L).setSubOrderId(22L).setAccountId(10L).setDeviceId(20L)))
                .thenReturn(new LogisticsWaybillRespVO().setOrderId(2L).setSubOrderId(22L).setPrintStatus("PENDING"));

        List<LogisticsWaybillRespVO> results = controller.batchCreateWaybill(
                new LogisticsWaybillBatchCreateReqVO().setOrderIds(List.of(1L, 2L))
                        .setAccountId(10L).setDeviceId(20L)).getData();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getOrderId()).isEqualTo(1L);
        assertThat(results.get(0).getErrorMessage()).contains("顺丰物流账号不存在");
        assertThat(results.get(0).getSubOrderId()).isEqualTo(11L);
        assertThat(results.get(1).getSubOrderId()).isEqualTo(22L);
        assertThat(results.get(1).getPrintStatus()).isEqualTo("PENDING");
    }

    @Test
    void batchCreateWaybillProcessesDuplicateOrderIdOnce() {
        LogisticsWaybillService waybillService = mock(LogisticsWaybillService.class);
        SfPrintBridgeLogisticsController controller = new SfPrintBridgeLogisticsController();
        ReflectionTestUtils.setField(controller, "managementService", mock(LogisticsManagementService.class));
        ReflectionTestUtils.setField(controller, "waybillService", waybillService);
        TradeOrderSubMapper orderSubMapper = mock(TradeOrderSubMapper.class);
        ReflectionTestUtils.setField(controller, "orderSubMapper", orderSubMapper);
        when(orderSubMapper.selectListByOrderId(1L)).thenReturn(List.of(subOrder(11L, 1L, 101L)));
        when(orderSubMapper.selectListByOrderId(2L)).thenReturn(List.of(subOrder(22L, 2L, 202L)));
        LogisticsWaybillCreateReqVO first = new LogisticsWaybillCreateReqVO().setOrderId(1L).setSubOrderId(11L);
        LogisticsWaybillCreateReqVO second = new LogisticsWaybillCreateReqVO().setOrderId(2L).setSubOrderId(22L);
        when(waybillService.createWaybill(first))
                .thenReturn(new LogisticsWaybillRespVO().setOrderId(1L).setSubOrderId(11L).setPrintStatus("PENDING"));
        when(waybillService.createWaybill(second))
                .thenReturn(new LogisticsWaybillRespVO().setOrderId(2L).setSubOrderId(22L).setPrintStatus("PENDING"));

        List<LogisticsWaybillRespVO> results = controller.batchCreateWaybill(
                new LogisticsWaybillBatchCreateReqVO().setOrderIds(List.of(1L, 1L, 2L))).getData();

        assertThat(results).extracting(LogisticsWaybillRespVO::getSubOrderId).containsExactly(11L, 22L);
        verify(waybillService, times(1)).createWaybill(first);
        verify(waybillService, times(1)).createWaybill(second);
    }

    private static TradeOrderSubDO subOrder(Long id, Long orderId, Long merchantId) {
        return new TradeOrderSubDO().setId(id).setOrderId(orderId).setMerchantId(merchantId)
                .setMerchantName("商家" + merchantId).setStatus(TradeOrderStatusEnum.UNDELIVERED.getStatus())
                .setRefundStatus(TradeOrderRefundStatusEnum.NONE.getStatus());
    }
}
