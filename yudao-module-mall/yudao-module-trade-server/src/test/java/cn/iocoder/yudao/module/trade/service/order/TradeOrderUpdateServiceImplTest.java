package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pay.api.order.PayOrderApi;
import cn.iocoder.yudao.module.pay.api.order.dto.PayOrderUpdatePriceReqDTO;
import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderUpdatePriceReqVO;
import cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderCreateReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderItemMapper;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderSubMapper;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TradeOrderUpdateServiceImplTest {

    private final TradeOrderUpdateServiceImpl service = new TradeOrderUpdateServiceImpl();

    @Test
    void validateExpectedPrice_acceptsMatchingSnapshotAndLegacyClient() {
        TradePriceCalculateRespBO actual = actualPrice(8801, 3889);

        assertDoesNotThrow(() -> service.validateExpectedPrice(2L, new AppTradeOrderCreateReqVO(), actual));
        assertDoesNotThrow(() -> service.validateExpectedPrice(2L,
                new AppTradeOrderCreateReqVO().setExpectedPayPrice(8801).setExpectedUsePoint(3889), actual));
    }

    @Test
    void validateExpectedPrice_rejectsChangedPayPriceOrPoints() {
        TradePriceCalculateRespBO actual = actualPrice(8801, 3889);

        ServiceException priceException = assertThrows(ServiceException.class, () -> service.validateExpectedPrice(2L,
                new AppTradeOrderCreateReqVO().setExpectedPayPrice(8800).setExpectedUsePoint(3889), actual));
        assertThat(priceException.getCode()).isEqualTo(1_011_000_040);

        ServiceException pointException = assertThrows(ServiceException.class, () -> service.validateExpectedPrice(2L,
                new AppTradeOrderCreateReqVO().setExpectedPayPrice(8801).setExpectedUsePoint(3888), actual));
        assertThat(pointException.getCode()).isEqualTo(1_011_000_040);
    }

    @Test
    void updateOrderPrice_updatesPaymentAllocationWithMerchantSubOrderAmounts() {
        TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
        TradeOrderItemMapper itemMapper = mock(TradeOrderItemMapper.class);
        TradeOrderSubMapper subMapper = mock(TradeOrderSubMapper.class);
        PayOrderApi payOrderApi = mock(PayOrderApi.class);
        ReflectionTestUtils.setField(service, "tradeOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "tradeOrderItemMapper", itemMapper);
        ReflectionTestUtils.setField(service, "tradeOrderSubMapper", subMapper);
        ReflectionTestUtils.setField(service, "payOrderApi", payOrderApi);
        TradeOrderDO order = new TradeOrderDO().setId(1L).setPayOrderId(20L).setPayStatus(false)
                .setAdjustPrice(0).setPayPrice(100).setStatus(TradeOrderStatusEnum.UNPAID.getStatus());
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(itemMapper.selectListByOrderId(1L)).thenReturn(List.of(new TradeOrderItemDO().setId(101L)
                .setOrderId(1L).setSubOrderId(10L).setMerchantId(2L).setPrice(100).setCount(1)
                .setAdjustPrice(0).setPayPrice(100)));
        when(subMapper.selectListByOrderId(1L)).thenReturn(List.of(new TradeOrderSubDO().setId(10L)
                .setOrderId(1L).setMerchantId(2L).setPayPrice(100)));
        when(payOrderApi.updatePayOrderPrice(anyLong(), anyInt())).thenReturn(CommonResult.success(true));
        when(payOrderApi.updatePayOrderPriceWithDividePlan(any())).thenReturn(CommonResult.success(true));

        service.updateOrderPrice(new TradeOrderUpdatePriceReqVO().setId(1L).setAdjustPrice(50));

        ArgumentCaptor<PayOrderUpdatePriceReqDTO> request = ArgumentCaptor.forClass(PayOrderUpdatePriceReqDTO.class);
        verify(payOrderApi).updatePayOrderPriceWithDividePlan(request.capture());
        assertThat(request.getValue().getPayPrice()).isEqualTo(150);
        assertThat(request.getValue().getDividePlan()).singleElement().satisfies(plan -> {
            assertThat(plan.getTradeOrderId()).isEqualTo(1L);
            assertThat(plan.getTradeOrderSubId()).isEqualTo(10L);
            assertThat(plan.getMerchantId()).isEqualTo(2L);
            assertThat(plan.getAmount()).isEqualTo(150);
        });
    }

    private static TradePriceCalculateRespBO actualPrice(int payPrice, int usePoint) {
        return new TradePriceCalculateRespBO()
                .setPrice(new TradePriceCalculateRespBO.Price().setPayPrice(payPrice))
                .setUsePoint(usePoint);
    }

}
