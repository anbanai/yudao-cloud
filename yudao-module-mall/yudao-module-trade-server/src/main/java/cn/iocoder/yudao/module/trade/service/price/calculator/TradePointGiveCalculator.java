package cn.iocoder.yudao.module.trade.service.price.calculator;

import cn.hutool.core.util.BooleanUtil;
import cn.iocoder.yudao.module.member.api.config.MemberConfigApi;
import cn.iocoder.yudao.module.member.api.config.dto.MemberConfigRespDTO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.filterList;

/**
 * 赠送积分的 {@link TradePriceCalculator} 实现类
 *
 * @author owen
 */
@Component
@Order(TradePriceCalculator.ORDER_POINT_GIVE)
@Slf4j
public class TradePointGiveCalculator implements TradePriceCalculator {

    @Resource
    private MemberConfigApi memberConfigApi;

    @Override
    public void calculate(TradePriceCalculateReqBO param, TradePriceCalculateRespBO result) {
        // 1.1 校验积分功能是否开启
        int givePointPerYuan = Optional.ofNullable(memberConfigApi.getConfig().getCheckedData())
                .filter(config -> BooleanUtil.isTrue(config.getPointTradeDeductEnable()))
                .map(MemberConfigRespDTO::getPointTradeGivePoint)
                .orElse(0);
        if (givePointPerYuan <= 0) {
            TradePriceCalculatorHelper.recountAllGivePoint(result);
            return;
        }

        // 1.2 只统计优惠后的商品实付金额，运费不参与消费积分计算。
        long productPayPrice = calculateProductPayPrice(result);
        if (productPayPrice <= 0) {
            TradePriceCalculatorHelper.recountAllGivePoint(result);
            return;
        }

        // 2.1 计算赠送积分
        int givePoint = calculateGivePoint(productPayPrice, givePointPerYuan);
        if (givePoint <= 0) {
            TradePriceCalculatorHelper.recountAllGivePoint(result);
            return;
        }
        // 2.2 计算分摊的赠送积分
        List<TradePriceCalculateRespBO.OrderItem> orderItems = filterList(result.getItems(), item ->
                Boolean.TRUE.equals(item.getSelected()) && item.getPayPrice() != null
                        && calculateItemProductPayPrice(item) >= 0);
        if (orderItems.isEmpty()) {
            TradePriceCalculatorHelper.recountAllGivePoint(result);
            return;
        }
        List<Integer> dividePoints = TradePriceCalculatorHelper.dividePointValue(orderItems, givePoint);

        // 3.2 更新 SKU 赠送积分
        for (int i = 0; i < orderItems.size(); i++) {
            TradePriceCalculateRespBO.OrderItem orderItem = orderItems.get(i);
            // 商品可能赠送了积分，所以这里要加上
            long itemGivePoint = (long) Math.max(defaultValue(orderItem.getGivePoint()), 0) + dividePoints.get(i);
            orderItem.setGivePoint((int) Math.min(itemGivePoint, Integer.MAX_VALUE));
        }
        // 3.3 更新订单赠送积分
        TradePriceCalculatorHelper.recountAllGivePoint(result);
    }

    /**
     * 计算选中商品的优惠后实付金额，不含运费。
     */
    private long calculateProductPayPrice(TradePriceCalculateRespBO result) {
        if (result.getItems() == null) {
            return 0;
        }
        return result.getItems().stream()
                .filter(item -> Boolean.TRUE.equals(item.getSelected()))
                .mapToLong(item -> Math.max(calculateItemProductPayPrice(item), 0L))
                .sum();
    }

    private long calculateItemProductPayPrice(TradePriceCalculateRespBO.OrderItem item) {
        if (item.getPayPrice() == null) {
            return 0;
        }
        return (long) defaultValue(item.getPayPrice()) - Math.max(defaultValue(item.getDeliveryPrice()), 0L);
    }

    /**
     * 按“每 1 元赠送多少分”计算基础消费积分，结果限制在积分字段可表示的范围内。
     */
    private int calculateGivePoint(long productPayPrice, int givePointPerYuan) {
        BigDecimal givePoint = BigDecimal.valueOf(productPayPrice)
                .multiply(BigDecimal.valueOf(givePointPerYuan))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
        return givePoint.min(BigDecimal.valueOf(Integer.MAX_VALUE)).intValue();
    }

    private int defaultValue(Integer value) {
        return value == null ? 0 : value;
    }

}
