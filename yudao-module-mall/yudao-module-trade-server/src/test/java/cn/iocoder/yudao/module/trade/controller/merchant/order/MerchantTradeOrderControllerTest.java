package cn.iocoder.yudao.module.trade.controller.merchant.order;

import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderSubMapper;
import cn.iocoder.yudao.module.trade.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsWaybillService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantTradeOrderControllerTest {

    @Test
    void authenticatedMerchantsCanReadOrdersAndOtherOperationsStayRestricted() throws Exception {
        for (var method : MerchantTradeOrderController.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(org.springframework.web.bind.annotation.GetMapping.class)) {
                org.junit.jupiter.api.Assertions.assertEquals("isAuthenticated()", method.getAnnotation(PreAuthorize.class).value());
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals("denyAll()",
                MerchantTradeOrderController.class.getAnnotation(PreAuthorize.class).value());
    }

    @Mock
    private TradeOrderSubMapper orderSubMapper;
    @Mock
    private TradeOrderItemMapper orderItemMapper;
    @Mock
    private TradeOrderQueryService orderQueryService;
    @Mock
    private LogisticsWaybillService logisticsWaybillService;
    @Mock
    private MerchantSecurityService merchantSecurityService;

    @InjectMocks
    private MerchantTradeOrderController controller;

    @Test
    void getDetail_rejectsSubOrderOwnedByAnotherMerchant() {
        when(merchantSecurityService.requireOperatingMerchantId()).thenReturn(11L);
        when(orderSubMapper.selectByIdAndMerchantId(22L, 11L)).thenReturn(null);

        assertThrows(AccessDeniedException.class, () -> controller.getDetail(22L));
        verify(orderQueryService, never()).getOrder(org.mockito.ArgumentMatchers.anyLong());
        verify(orderItemMapper, never()).selectListBySubOrderId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void getDetail_mapsTheAuthorizedOrdersReceiver() {
        when(merchantSecurityService.requireOperatingMerchantId()).thenReturn(11L);
        when(orderSubMapper.selectByIdAndMerchantId(22L, 11L))
                .thenReturn(new TradeOrderSubDO().setId(22L).setOrderId(33L).setMerchantId(11L));
        when(orderQueryService.getOrder(33L)).thenReturn(new TradeOrderDO().setReceiverName("收件人")
                .setReceiverMobile("13800000000").setReceiverAreaId(330110).setReceiverDetailAddress("仓前路一号"));

        var receiver = controller.getDetail(22L).getCheckedData().getReceiver();
        org.assertj.core.api.Assertions.assertThat(receiver).extracting("name", "mobile", "areaId", "detailAddress")
                .containsExactly("收件人", "13800000000", 330110, "仓前路一号");
    }

    @Test
    void delivery_requiresMerchantSubOrderId() {
        when(merchantSecurityService.requireOperatingMerchantId()).thenReturn(11L);
        TradeOrderDeliveryReqVO reqVO = new TradeOrderDeliveryReqVO();
        reqVO.setId(33L);

        assertThrows(IllegalArgumentException.class, () -> controller.delivery(reqVO));
        verify(logisticsWaybillService, never()).deliverManually(reqVO);
    }

}
