package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.module.trade.controller.admin.order.vo.TradeOrderPageReqVO;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeOrderQueryServiceImplTest {

    @Test
    void getOrderSummary_includesProductCountForUnrefundedOrders() {
        TradeOrderQueryServiceImpl service = new TradeOrderQueryServiceImpl();
        TradeOrderMapper orderMapper = mock(TradeOrderMapper.class);
        ReflectionTestUtils.setField(service, "tradeOrderMapper", orderMapper);
        when(orderMapper.selectOrderSummaryGroupByRefundStatus(new TradeOrderPageReqVO(),
                java.util.Collections.emptySet())).thenReturn(List.of(
                Map.of("refundStatus", 0, "count", 2L, "price", 1500L, "productCount", 3),
                Map.of("refundStatus", 10, "count", 1L, "price", 500L, "productCount", 1)));

        var summary = service.getOrderSummary(new TradeOrderPageReqVO());

        assertThat(summary.getOrderCount()).isEqualTo(2L);
        assertThat(summary.getOrderPayPrice()).isEqualTo(1500L);
        assertThat(summary.getProductCount()).isEqualTo(3L);
        assertThat(summary.getAfterSaleCount()).isEqualTo(1L);
    }
}
