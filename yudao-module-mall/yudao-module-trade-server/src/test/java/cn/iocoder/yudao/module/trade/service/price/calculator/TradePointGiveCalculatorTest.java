package cn.iocoder.yudao.module.trade.service.price.calculator;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.config.MemberConfigApi;
import cn.iocoder.yudao.module.member.api.config.dto.MemberConfigRespDTO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradePointGiveCalculatorTest {

    @Test
    void calculate_usesDiscountedProductPayPrice() {
        TradePointGiveCalculator calculator = newCalculator(true, 1);
        TradePriceCalculateRespBO result = priceResult(item(6800, 0, 3600, 0));

        calculator.calculate(new TradePriceCalculateReqBO(), result);

        assertThat(result.getGivePoint()).isEqualTo(36);
    }

    @Test
    void calculate_doesNotGivePointsForDeliveryPrice() {
        TradePointGiveCalculator calculator = newCalculator(true, 1);
        TradePriceCalculateRespBO result = priceResult(item(6800, 1000, 4600, 0));

        calculator.calculate(new TradePriceCalculateReqBO(), result);

        assertThat(result.getGivePoint()).isEqualTo(36);
    }

    @Test
    void calculate_addsProductFixedPointsToConsumptionPoints() {
        TradePointGiveCalculator calculator = newCalculator(true, 1);
        TradePriceCalculateRespBO result = priceResult(item(6800, 0, 3600, 30));

        calculator.calculate(new TradePriceCalculateReqBO(), result);

        assertThat(result.getGivePoint()).isEqualTo(66);
        assertThat(result.getItems().get(0).getGivePoint()).isEqualTo(66);
    }

    @Test
    void calculate_allocatesConsumptionPointsAcrossItemsWithoutLoss() {
        TradePointGiveCalculator calculator = newCalculator(true, 1);
        TradePriceCalculateRespBO result = priceResult(
                item(1000, 0, 1000, 0), item(3000, 500, 3500, 0));

        calculator.calculate(new TradePriceCalculateReqBO(), result);

        assertThat(result.getGivePoint()).isEqualTo(40);
        assertThat(result.getItems()).extracting(TradePriceCalculateRespBO.OrderItem::getGivePoint)
                .containsExactly(10, 30);
    }

    @Test
    void calculate_returnsZeroWhenDisabledOrRateIsZero() {
        TradePriceCalculateRespBO disabledResult = priceResult(item(1000, 0, 1000, 0));
        newCalculator(false, 1).calculate(new TradePriceCalculateReqBO(), disabledResult);
        assertThat(disabledResult.getGivePoint()).isZero();

        TradePriceCalculateRespBO zeroRateResult = priceResult(item(1000, 0, 1000, 0));
        newCalculator(true, 0).calculate(new TradePriceCalculateReqBO(), zeroRateResult);
        assertThat(zeroRateResult.getGivePoint()).isZero();
    }

    @Test
    void calculate_keepsFixedPointsWhenConsumptionPointsAreDisabled() {
        TradePriceCalculateRespBO result = priceResult(item(1000, 0, 1000, 30));

        newCalculator(false, 1).calculate(new TradePriceCalculateReqBO(), result);

        assertThat(result.getGivePoint()).isEqualTo(30);
    }

    @Test
    void calculate_handlesLargeOrderAmountsWithoutOverflow() {
        TradePointGiveCalculator calculator = newCalculator(true, Integer.MAX_VALUE);
        TradePriceCalculateRespBO result = priceResult(item(Integer.MAX_VALUE, 0, Integer.MAX_VALUE, 0));

        calculator.calculate(new TradePriceCalculateReqBO(), result);

        assertThat(result.getGivePoint()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void calculate_handlesEmptyAndNegativeProductAmounts() {
        TradePointGiveCalculator calculator = newCalculator(true, 1);
        TradePriceCalculateRespBO emptyResult = priceResult();
        calculator.calculate(new TradePriceCalculateReqBO(), emptyResult);
        assertThat(emptyResult.getGivePoint()).isZero();

        TradePriceCalculateRespBO result = priceResult(
                item(1000, 1200, 1000, 0), item(1000, 0, 1000, 0));
        calculator.calculate(new TradePriceCalculateReqBO(), result);
        assertThat(result.getGivePoint()).isEqualTo(10);
    }

    @Test
    void calculate_ignoresMalformedItemsWithoutBreakingValidItems() {
        TradePriceCalculateRespBO result = priceResult(
                item(1000, 0, null, 30), item(1000, Integer.MIN_VALUE, 1000, 0));

        newCalculator(true, 1).calculate(new TradePriceCalculateReqBO(), result);

        assertThat(result.getGivePoint()).isEqualTo(40);
    }

    @Test
    void calculate_handlesNullItemList() {
        TradePriceCalculateRespBO result = new TradePriceCalculateRespBO()
                .setItems(null).setGivePoint(12);

        newCalculator(false, 1).calculate(new TradePriceCalculateReqBO(), result);

        assertThat(result.getGivePoint()).isZero();
    }

    private static TradePointGiveCalculator newCalculator(boolean deductEnabled, int givePointPerYuan) {
        MemberConfigApi memberConfigApi = mock(MemberConfigApi.class);
        when(memberConfigApi.getConfig()).thenReturn(CommonResult.success(new MemberConfigRespDTO()
                .setPointTradeDeductEnable(deductEnabled)
                .setPointTradeGivePoint(givePointPerYuan)));
        TradePointGiveCalculator calculator = new TradePointGiveCalculator();
        ReflectionTestUtils.setField(calculator, "memberConfigApi", memberConfigApi);
        return calculator;
    }

    private static TradePriceCalculateRespBO priceResult(TradePriceCalculateRespBO.OrderItem... items) {
        int payPrice = 0;
        for (TradePriceCalculateRespBO.OrderItem item : items) {
            payPrice += item.getPayPrice() == null ? 0 : item.getPayPrice();
        }
        return new TradePriceCalculateRespBO()
                .setItems(new ArrayList<>(List.of(items)))
                .setPrice(new TradePriceCalculateRespBO.Price()
                        .setPayPrice(payPrice).setDeliveryPrice(0))
                .setGivePoint(0);
    }

    private static TradePriceCalculateRespBO.OrderItem item(int price, int deliveryPrice,
                                                             Integer payPrice, int givePoint) {
        return new TradePriceCalculateRespBO.OrderItem()
                .setSelected(true).setPrice(price).setCount(1)
                .setDeliveryPrice(deliveryPrice).setPayPrice(payPrice)
                .setGivePoint(givePoint);
    }

}
