package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TradeOrderSubOrderAssemblerTest {

    @Test
    void assemble_groupsItemsAndPreservesOrderPriceBreakdown() {
        TradeOrderDO order = new TradeOrderDO().setId(50L).setUserId(2L).setStatus(0)
                .setDeliveryType(1).setPayPrice(280);
        List<TradeOrderItemDO> items = List.of(
                item(1L, "商家甲", 100, 2, 10, 20, 10, 5, 4, 0, 159),
                item(1L, "商家甲", 50, 1, 0, 5, 0, 0, 2, 0, 47),
                item(2L, "商家乙", 100, 1, 10, 10, 5, 5, 4, 0, 74));

        List<TradeOrderSubOrderAssembler.Group> groups = TradeOrderSubOrderAssembler.assemble(order, items);

        assertThat(groups).hasSize(2);
        TradeOrderSubDO merchantA = groups.get(0).subOrder();
        assertThat(merchantA.getMerchantId()).isEqualTo(1L);
        assertThat(merchantA.getMerchantName()).isEqualTo("商家甲");
        assertThat(merchantA.getProductCount()).isEqualTo(3);
        assertThat(merchantA.getTotalPrice()).isEqualTo(250);
        assertThat(merchantA.getDiscountPrice()).isEqualTo(10);
        assertThat(merchantA.getCouponPrice()).isEqualTo(25);
        assertThat(merchantA.getPointPrice()).isEqualTo(10);
        assertThat(merchantA.getVipPrice()).isEqualTo(5);
        assertThat(merchantA.getDeliveryPrice()).isEqualTo(6);
        assertThat(merchantA.getPayPrice()).isEqualTo(206);
        assertThat(groups.get(1).subOrder().getPayPrice()).isEqualTo(74);
        assertThat(groups.stream().mapToInt(group -> group.subOrder().getPayPrice()).sum())
                .isEqualTo(order.getPayPrice());
        assertThat(groups.get(0).items()).containsExactly(items.get(0), items.get(1));
        assertThat(groups.get(1).items()).containsExactly(items.get(2));
    }

    @Test
    void aggregateStatus_ignoresCanceledSubOrdersWhenAllActiveSubOrdersComplete() {
        List<TradeOrderSubDO> subOrders = List.of(
                new TradeOrderSubDO().setStatus(TradeOrderStatusEnum.CANCELED.getStatus()),
                new TradeOrderSubDO().setStatus(TradeOrderStatusEnum.COMPLETED.getStatus()));

        assertThat(TradeOrderSubOrderAssembler.aggregateStatus(subOrders))
                .isEqualTo(TradeOrderStatusEnum.COMPLETED.getStatus());
    }

    @Test
    void aggregateStatus_isDeliveredUntilEveryActiveSubOrderIsCompleted() {
        List<TradeOrderSubDO> subOrders = List.of(
                new TradeOrderSubDO().setStatus(TradeOrderStatusEnum.DELIVERED.getStatus()),
                new TradeOrderSubDO().setStatus(TradeOrderStatusEnum.COMPLETED.getStatus()));

        assertThat(TradeOrderSubOrderAssembler.aggregateStatus(subOrders))
                .isEqualTo(TradeOrderStatusEnum.DELIVERED.getStatus());
    }

    @Test
    void aggregateStatus_isCanceledWhenEverySubOrderIsCanceled() {
        List<TradeOrderSubDO> subOrders = List.of(
                new TradeOrderSubDO().setStatus(TradeOrderStatusEnum.CANCELED.getStatus()),
                new TradeOrderSubDO().setStatus(TradeOrderStatusEnum.CANCELED.getStatus()));

        assertThat(TradeOrderSubOrderAssembler.aggregateStatus(subOrders))
                .isEqualTo(TradeOrderStatusEnum.CANCELED.getStatus());
    }

    private static TradeOrderItemDO item(Long merchantId, String merchantName, int price, int count,
                                         int discount, int coupon, int point, int vip, int delivery,
                                         int adjust, int pay) {
        return new TradeOrderItemDO().setMerchantId(merchantId).setMerchantName(merchantName)
                .setPrice(price).setCount(count).setDiscountPrice(discount).setCouponPrice(coupon)
                .setPointPrice(point).setVipPrice(vip).setDeliveryPrice(delivery)
                .setAdjustPrice(adjust).setPayPrice(pay);
    }
}
