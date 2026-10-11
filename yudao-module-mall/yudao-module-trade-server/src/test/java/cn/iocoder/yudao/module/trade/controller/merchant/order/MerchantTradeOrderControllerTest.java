package cn.iocoder.yudao.module.trade.controller.merchant.order;

import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderDeliveryReqVO;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantTradeOrderControllerTest {

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
    void delivery_requiresMerchantSubOrderId() {
        when(merchantSecurityService.requireOperatingMerchantId()).thenReturn(11L);
        TradeOrderDeliveryReqVO reqVO = new TradeOrderDeliveryReqVO();
        reqVO.setId(33L);

        assertThrows(IllegalArgumentException.class, () -> controller.delivery(reqVO));
        verify(logisticsWaybillService, never()).deliverManually(reqVO);
    }

}
