package cn.iocoder.yudao.module.trade.service.order;

import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderItemDO;
import cn.iocoder.yudao.module.trade.dal.dataobject.order.TradeOrderSubDO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds merchant fulfilment orders from the platform order snapshot.
 * The item order is preserved so the generated merchant order numbers are deterministic.
 */
final class TradeOrderSubOrderAssembler {

    private TradeOrderSubOrderAssembler() {
    }

    static List<Group> assemble(TradeOrderDO order, List<TradeOrderItemDO> items) {
        Map<Long, List<TradeOrderItemDO>> grouped = new LinkedHashMap<>();
        for (TradeOrderItemDO item : items) {
            grouped.computeIfAbsent(item.getMerchantId(), ignored -> new ArrayList<>()).add(item);
        }
        List<Group> result = new ArrayList<>(grouped.size());
        for (List<TradeOrderItemDO> merchantItems : grouped.values()) {
            TradeOrderItemDO first = merchantItems.get(0);
            TradeOrderSubDO subOrder = new TradeOrderSubDO()
                    .setOrderId(order.getId())
                    .setUserId(order.getUserId())
                    .setMerchantId(first.getMerchantId())
                    .setMerchantName(first.getMerchantName())
                    .setStatus(order.getStatus())
                    .setProductCount(sum(merchantItems, TradeOrderItemDO::getCount))
                    .setTotalPrice(sumProductPrices(merchantItems))
                    .setDiscountPrice(sum(merchantItems, TradeOrderItemDO::getDiscountPrice))
                    .setCouponPrice(sum(merchantItems, TradeOrderItemDO::getCouponPrice))
                    .setPointPrice(sum(merchantItems, TradeOrderItemDO::getPointPrice))
                    .setVipPrice(sum(merchantItems, TradeOrderItemDO::getVipPrice))
                    .setAdjustPrice(sum(merchantItems, TradeOrderItemDO::getAdjustPrice))
                    .setDeliveryPrice(sum(merchantItems, TradeOrderItemDO::getDeliveryPrice))
                    .setPayPrice(sum(merchantItems, TradeOrderItemDO::getPayPrice))
                    .setDeliveryType(order.getDeliveryType())
                    .setRefundStatus(0)
                    .setAfterSaleStatus(0);
            result.add(new Group(subOrder, merchantItems));
        }
        return result;
    }

    private static int sumProductPrices(List<TradeOrderItemDO> items) {
        int sum = 0;
        for (TradeOrderItemDO item : items) {
            sum += value(item.getPrice()) * value(item.getCount());
        }
        return sum;
    }

    private static int sum(List<TradeOrderItemDO> items, java.util.function.Function<TradeOrderItemDO, Integer> getter) {
        int sum = 0;
        for (TradeOrderItemDO item : items) {
            sum += value(getter.apply(item));
        }
        return sum;
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    record Group(TradeOrderSubDO subOrder, List<TradeOrderItemDO> items) {
    }
}
