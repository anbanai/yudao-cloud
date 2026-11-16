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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradePointDiscountRateTest {

    @Test
    void rateCapsPointsToPostDiscountMerchandiseAmount() {
        TradePriceCalculateRespBO result = result(item(800, 200, "0.25"));
        calculator(1000).calculate(request(), result);
        assertThat(result.getItems().get(0).getPointPrice()).isEqualTo(200);
        assertThat(result.getItems().get(0).getUsePoint()).isEqualTo(200);
        assertThat(result.getPrice().getPayPrice()).isEqualTo(600);
    }

    @Test
    void rateExcludesShippingFromTheDiscountableMerchandiseAmount() {
        TradePriceCalculateRespBO result = result(item(1300, 0, "0.90"));
        result.getItems().get(0).setDeliveryPrice(300);
        result.getPrice().setDeliveryPrice(300).setPayPrice(1300);
        calculator(5000).calculate(request(), result);
        assertThat(result.getItems().get(0).getPointPrice()).isEqualTo(900);
        assertThat(result.getPrice().getDeliveryPrice()).isEqualTo(300);
        assertThat(result.getPrice().getPayPrice()).isEqualTo(700);
    }

    @Test
    void omittedRateRetainsGlobalPointPolicyAndExplicitZeroDisablesIt() {
        TradePriceCalculateRespBO legacy = result(item(1000, 0, null));
        calculator(5000).calculate(request(), legacy);
        assertThat(legacy.getPrice().getPointPrice()).isEqualTo(999);

        TradePriceCalculateRespBO disabled = result(item(1000, 0, "0"));
        calculator(5000).calculate(request(), disabled);
        assertThat(disabled.getPrice().getPointPrice()).isZero();
    }

    private static TradePointUsePriceCalculator calculator(int points) {
        MemberUserApi users = mock(MemberUserApi.class);
        when(users.getUser(2L)).thenReturn(CommonResult.success(new MemberUserRespDTO().setId(2L).setPoint(points)));
        MemberConfigApi configs = mock(MemberConfigApi.class);
        when(configs.getConfig()).thenReturn(CommonResult.success(new MemberConfigRespDTO()
                .setPointTradeDeductEnable(true).setPointTradeDeductUnitPrice(1).setPointTradeDeductMaxPrice(0)));
        TradePointUsePriceCalculator calculator = new TradePointUsePriceCalculator();
        ReflectionTestUtils.setField(calculator, "memberUserApi", users);
        ReflectionTestUtils.setField(calculator, "memberConfigApi", configs);
        return calculator;
    }

    private static TradePriceCalculateReqBO request() { return new TradePriceCalculateReqBO().setUserId(2L).setPointStatus(true); }

    private static TradePriceCalculateRespBO result(TradePriceCalculateRespBO.OrderItem item) {
        return new TradePriceCalculateRespBO().setType(TradeOrderTypeEnum.NORMAL.getType())
                .setItems(Collections.singletonList(item)).setPromotions(new java.util.ArrayList<>())
                .setPrice(new TradePriceCalculateRespBO.Price().setTotalPrice(item.getPrice()).setDiscountPrice(0)
                        .setDeliveryPrice(item.getDeliveryPrice()).setCouponPrice(item.getCouponPrice()).setPointPrice(0)
                        .setVipPrice(0).setPayPrice(item.getPayPrice()));
    }

    private static TradePriceCalculateRespBO.OrderItem item(int payable, int coupon, String rate) {
        return new TradePriceCalculateRespBO.OrderItem().setSelected(true).setCount(1).setPrice(payable + coupon)
                .setPayPrice(payable).setDiscountPrice(0).setDeliveryPrice(0).setCouponPrice(coupon)
                .setPointPrice(0).setVipPrice(0).setUsePoint(0)
                .setPointDiscountRate(rate == null ? null : new BigDecimal(rate));
    }
}
