package cn.iocoder.yudao.module.trade.service.price.calculator;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.config.MemberConfigApi;
import cn.iocoder.yudao.module.member.api.config.dto.MemberConfigRespDTO;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.trade.enums.order.TradeOrderTypeEnum;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradePointUsePriceCalculatorTest {

    @Test
    void calculate_capsPointsToLeaveOneCentPayable() {
        TradePointUsePriceCalculator calculator = newCalculator(100_000, true, 1, 0);
        TradePriceCalculateReqBO param = new TradePriceCalculateReqBO()
                .setUserId(2L).setPointStatus(true);
        TradePriceCalculateRespBO result = priceResult(3890);

        calculator.calculate(param, result);

        assertThat(result.getUsePoint()).isEqualTo(3889);
        assertThat(result.getPrice().getPointPrice()).isEqualTo(3889);
        assertThat(result.getPrice().getPayPrice()).isEqualTo(1);
        assertThat(result.getItems().get(0).getUsePoint()).isEqualTo(3889);
        assertThat(result.getItems().get(0).getPointPrice()).isEqualTo(3889);
        assertThat(result.getMaxUsePoint()).isEqualTo(3889);
        assertThat(result.getMaxPointPrice()).isEqualTo(3889);
    }

    @Test
    void calculate_neverOffsetsDeliveryPrice() {
        TradePointUsePriceCalculator calculator = newCalculator(100_000, true, 1, 0);
        TradePriceCalculateRespBO result = priceResult(3890);
        result.getItems().get(0).setDeliveryPrice(8800).setPayPrice(12690);
        result.getPrice().setDeliveryPrice(8800).setPayPrice(12690);

        calculator.calculate(new TradePriceCalculateReqBO().setUserId(2L).setPointStatus(true), result);

        assertThat(result.getUsePoint()).isEqualTo(3889);
        assertThat(result.getPrice().getPointPrice()).isEqualTo(3889);
        assertThat(result.getPrice().getDeliveryPrice()).isEqualTo(8800);
        assertThat(result.getPrice().getPayPrice()).isEqualTo(8801);
    }

    @Test
    void calculate_usesSmallestOfBalanceConfigAndOrderCaps() {
        assertPointResult(newCalculator(500, true, 2, 0), true, 2000, 500, 1000);
        assertPointResult(newCalculator(5000, true, 2, 600), true, 2000, 600, 1200);
        assertPointResult(newCalculator(5000, true, 2, 0), true, 1200, 599, 1198);
    }

    @Test
    void calculate_withoutSelectionOnlyReportsMaximum() {
        TradePointUsePriceCalculator calculator = newCalculator(5000, true, 2, 0);
        TradePriceCalculateRespBO result = priceResult(1200);

        calculator.calculate(new TradePriceCalculateReqBO().setUserId(2L).setPointStatus(false), result);

        assertThat(result.getMaxUsePoint()).isEqualTo(599);
        assertThat(result.getMaxPointPrice()).isEqualTo(1198);
        assertThat(result.getUsePoint()).isZero();
        assertThat(result.getPrice().getPointPrice()).isZero();
        assertThat(result.getPromotions()).isEmpty();
    }

    @Test
    void calculate_returnsZeroWhenPointCannotOffsetOneCent() {
        TradePointUsePriceCalculator calculator = newCalculator(100, true, 200, 0);
        TradePriceCalculateRespBO result = priceResult(200);

        calculator.calculate(new TradePriceCalculateReqBO().setUserId(2L).setPointStatus(true), result);

        assertThat(result.getMaxUsePoint()).isZero();
        assertThat(result.getMaxPointPrice()).isZero();
        assertThat(result.getUsePoint()).isZero();
        assertThat(result.getPromotions()).isEmpty();
    }

    @Test
    void calculate_returnsZeroMetadataWhenDisabledOrBalanceIsZero() {
        assertPointResult(newCalculator(100, false, 1, 0), true, 1000, 0, 0);
        assertPointResult(newCalculator(0, true, 1, 0), true, 1000, 0, 0);
    }

    @Test
    void calculate_doesNotChangePointMarketplaceOrder() {
        TradePointUsePriceCalculator calculator = newCalculator(100, true, 1, 0);
        TradePriceCalculateRespBO result = priceResult(1000)
                .setType(TradeOrderTypeEnum.POINT.getType())
                .setUsePoint(88);

        calculator.calculate(new TradePriceCalculateReqBO().setUserId(2L).setPointStatus(true), result);

        assertThat(result.getUsePoint()).isEqualTo(88);
        assertThat(result.getMaxUsePoint()).isZero();
        assertThat(result.getMaxPointPrice()).isZero();
    }

    @Test
    void calculate_largeValuesDoNotOverflow() {
        TradePointUsePriceCalculator calculator = newCalculator(Integer.MAX_VALUE, true, Integer.MAX_VALUE, 0);
        TradePriceCalculateRespBO result = priceResult(Integer.MAX_VALUE);

        calculator.calculate(new TradePriceCalculateReqBO().setUserId(2L).setPointStatus(true), result);

        assertThat(result.getMaxUsePoint()).isZero();
        assertThat(result.getMaxPointPrice()).isZero();
        assertThat(result.getPrice().getPayPrice()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void calculate_usesStableLargestRemainderAllocationForMultipleItems() {
        TradePointUsePriceCalculator calculator = newCalculator(100, true, 1, 0);
        TradePriceCalculateRespBO result = priceResult(1, 1, 1);

        calculator.calculate(new TradePriceCalculateReqBO().setUserId(2L).setPointStatus(true), result);

        assertThat(result.getUsePoint()).isEqualTo(2);
        assertThat(result.getItems()).extracting(TradePriceCalculateRespBO.OrderItem::getPointPrice)
                .containsExactly(1, 1, 0);
        assertThat(result.getItems()).extracting(TradePriceCalculateRespBO.OrderItem::getUsePoint)
                .containsExactly(1, 1, 0);
        assertThat(result.getItems()).extracting(TradePriceCalculateRespBO.OrderItem::getPayPrice)
                .containsExactly(0, 0, 1);
    }

    @Test
    void calculate_keepsEachItemPointPriceConsistentWithItsPoints() {
        TradePointUsePriceCalculator calculator = newCalculator(100, true, 2, 0);
        TradePriceCalculateRespBO result = priceResult(1, 2);

        calculator.calculate(new TradePriceCalculateReqBO().setUserId(2L).setPointStatus(true), result);

        assertThat(result.getUsePoint()).isEqualTo(1);
        assertThat(result.getItems()).extracting(TradePriceCalculateRespBO.OrderItem::getUsePoint)
                .containsExactly(0, 1);
        assertThat(result.getItems()).extracting(TradePriceCalculateRespBO.OrderItem::getPointPrice)
                .containsExactly(0, 2);
        assertThat(result.getItems()).extracting(TradePriceCalculateRespBO.OrderItem::getPayPrice)
                .containsExactly(1, 0);
    }

    private static void assertPointResult(TradePointUsePriceCalculator calculator, boolean pointStatus,
                                          int payPrice, int expectedPoint, int expectedPointPrice) {
        TradePriceCalculateRespBO result = priceResult(payPrice);
        calculator.calculate(new TradePriceCalculateReqBO().setUserId(2L).setPointStatus(pointStatus), result);
        assertThat(result.getMaxUsePoint()).isEqualTo(expectedPoint);
        assertThat(result.getMaxPointPrice()).isEqualTo(expectedPointPrice);
        assertThat(result.getUsePoint()).isEqualTo(pointStatus ? expectedPoint : 0);
        assertThat(result.getPrice().getPointPrice()).isEqualTo(pointStatus ? expectedPointPrice : 0);
    }

    private static TradePointUsePriceCalculator newCalculator(int userPoint, boolean deductEnabled,
                                                               int unitPrice, int maxPoint) {
        MemberUserApi memberUserApi = mock(MemberUserApi.class);
        when(memberUserApi.getUser(2L)).thenReturn(CommonResult.success(
                new MemberUserRespDTO().setId(2L).setPoint(userPoint)));
        MemberConfigApi memberConfigApi = mock(MemberConfigApi.class);
        when(memberConfigApi.getConfig()).thenReturn(CommonResult.success(
                new MemberConfigRespDTO()
                        .setPointTradeDeductEnable(deductEnabled)
                        .setPointTradeDeductUnitPrice(unitPrice)
                        .setPointTradeDeductMaxPrice(maxPoint)));

        TradePointUsePriceCalculator calculator = new TradePointUsePriceCalculator();
        ReflectionTestUtils.setField(calculator, "memberUserApi", memberUserApi);
        ReflectionTestUtils.setField(calculator, "memberConfigApi", memberConfigApi);
        return calculator;
    }

    private static TradePriceCalculateRespBO priceResult(int payPrice) {
        return priceResult(payPrice, new int[0]);
    }

    private static TradePriceCalculateRespBO priceResult(int firstPayPrice, int... otherPayPrices) {
        int[] payPrices = new int[otherPayPrices.length + 1];
        payPrices[0] = firstPayPrice;
        System.arraycopy(otherPayPrices, 0, payPrices, 1, otherPayPrices.length);
        List<TradePriceCalculateRespBO.OrderItem> items = new java.util.ArrayList<>();
        int totalPayPrice = 0;
        for (int i = 0; i < payPrices.length; i++) {
            items.add(priceItem(i + 1L, payPrices[i]));
            totalPayPrice += payPrices[i];
        }
        return new TradePriceCalculateRespBO()
                .setType(TradeOrderTypeEnum.NORMAL.getType())
                .setItems(items)
                .setPromotions(new java.util.ArrayList<>())
                .setPrice(new TradePriceCalculateRespBO.Price()
                        .setTotalPrice(totalPayPrice)
                        .setDiscountPrice(0)
                        .setDeliveryPrice(0)
                        .setCouponPrice(0)
                        .setPointPrice(0)
                        .setVipPrice(0)
                        .setPayPrice(totalPayPrice));
    }

    private static TradePriceCalculateRespBO.OrderItem priceItem(long skuId, int payPrice) {
        TradePriceCalculateRespBO.OrderItem item = new TradePriceCalculateRespBO.OrderItem()
                .setSelected(true)
                .setSkuId(skuId)
                .setCount(1)
                .setPrice(payPrice)
                .setDiscountPrice(0)
                .setDeliveryPrice(0)
                .setCouponPrice(0)
                .setPointPrice(0)
                .setVipPrice(0)
                .setPayPrice(payPrice)
                .setUsePoint(0);
        return item;
    }

}
