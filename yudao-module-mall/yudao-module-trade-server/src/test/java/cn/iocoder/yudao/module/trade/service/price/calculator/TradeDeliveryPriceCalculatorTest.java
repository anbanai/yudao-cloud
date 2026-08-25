package cn.iocoder.yudao.module.trade.service.price.calculator;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.member.api.address.MemberAddressApi;
import cn.iocoder.yudao.module.member.api.address.dto.MemberAddressRespDTO;
import cn.iocoder.yudao.module.trade.enums.delivery.DeliveryTypeEnum;
import cn.iocoder.yudao.module.trade.service.config.TradeConfigService;
import cn.iocoder.yudao.module.trade.service.delivery.DeliveryExpressTemplateService;
import cn.iocoder.yudao.module.trade.service.delivery.bo.DeliveryExpressTemplateRespBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateReqBO;
import cn.iocoder.yudao.module.trade.service.price.bo.TradePriceCalculateRespBO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.trade.enums.ErrorCodeConstants.PRICE_CALCULATE_DELIVERY_PRICE_TEMPLATE_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeDeliveryPriceCalculatorTest {

    @Test
    void calculate_regionalChargeBoAppliesFee() {
        TradeDeliveryPriceCalculator calculator = newCalculator(650102,
                Map.of(10L, templateBO(new DeliveryExpressTemplateRespBO.Charge()
                        .setStartCount(1D).setStartPrice(1200).setExtraCount(1D).setExtraPrice(0), null)));
        TradePriceCalculateReqBO param = new TradePriceCalculateReqBO()
                .setUserId(2L).setAddressId(1L).setDeliveryType(DeliveryTypeEnum.EXPRESS.getType());
        TradePriceCalculateRespBO result = priceResult(10L);

        calculator.calculate(param, result);

        assertThat(result.getPrice().getDeliveryPrice()).isEqualTo(1200);
        assertThat(result.getItems().get(0).getDeliveryPrice()).isEqualTo(1200);
    }

    @Test
    void calculate_countryFreeBoLeavesDeliveryPriceZero() {
        TradeDeliveryPriceCalculator calculator = newCalculator(110105,
                Map.of(10L, templateBO(null, new DeliveryExpressTemplateRespBO.Free()
                        .setFreePrice(0).setFreeCount(0))));
        TradePriceCalculateReqBO param = new TradePriceCalculateReqBO()
                .setUserId(2L).setAddressId(1L).setDeliveryType(DeliveryTypeEnum.EXPRESS.getType());
        TradePriceCalculateRespBO result = priceResult(10L);

        calculator.calculate(param, result);

        assertThat(result.getPrice().getDeliveryPrice()).isZero();
        assertThat(result.getItems().get(0).getDeliveryPrice()).isZero();
    }

    @Test
    void calculate_mixedMatchedTemplatesThrowsInsteadOfSkippingUnmatchedItem() {
        MemberAddressApi addressApi = mock(MemberAddressApi.class);
        DeliveryExpressTemplateService templateService = mock(DeliveryExpressTemplateService.class);
        TradeConfigService configService = mock(TradeConfigService.class);
        when(addressApi.getAddress(1L, 2L)).thenReturn(CommonResult.success(
                new MemberAddressRespDTO().setAreaId(650102)));
        when(configService.getTradeConfig()).thenReturn(null);
        when(templateService.getExpressTemplateMapByIdsAndArea(anyCollection(), eq(650102)))
                .thenReturn(Map.of(10L, new DeliveryExpressTemplateRespBO()
                        .setChargeMode(1)
                        .setCharge(new DeliveryExpressTemplateRespBO.Charge()
                                .setStartCount(1D).setStartPrice(100).setExtraCount(1D).setExtraPrice(0))));

        TradeDeliveryPriceCalculator calculator = new TradeDeliveryPriceCalculator();
        ReflectionTestUtils.setField(calculator, "addressApi", addressApi);
        ReflectionTestUtils.setField(calculator, "deliveryExpressTemplateService", templateService);
        ReflectionTestUtils.setField(calculator, "tradeConfigService", configService);

        TradePriceCalculateReqBO param = new TradePriceCalculateReqBO()
                .setUserId(2L).setAddressId(1L).setDeliveryType(DeliveryTypeEnum.EXPRESS.getType());
        TradePriceCalculateRespBO result = new TradePriceCalculateRespBO()
                .setFreeDelivery(false)
                .setPrice(new TradePriceCalculateRespBO.Price())
                .setItems(List.of(orderItem(10L), orderItem(20L)));

        ServiceException exception = assertThrows(ServiceException.class,
                () -> calculator.calculate(param, result));

        assertThat(exception.getCode()).isEqualTo(PRICE_CALCULATE_DELIVERY_PRICE_TEMPLATE_NOT_FOUND.getCode());
    }

    private static TradeDeliveryPriceCalculator newCalculator(Integer areaId,
                                                               Map<Long, DeliveryExpressTemplateRespBO> templates) {
        MemberAddressApi addressApi = mock(MemberAddressApi.class);
        DeliveryExpressTemplateService templateService = mock(DeliveryExpressTemplateService.class);
        TradeConfigService configService = mock(TradeConfigService.class);
        when(addressApi.getAddress(1L, 2L)).thenReturn(CommonResult.success(
                new MemberAddressRespDTO().setAreaId(areaId)));
        when(configService.getTradeConfig()).thenReturn(null);
        when(templateService.getExpressTemplateMapByIdsAndArea(anyCollection(), eq(areaId)))
                .thenReturn(templates);

        TradeDeliveryPriceCalculator calculator = new TradeDeliveryPriceCalculator();
        ReflectionTestUtils.setField(calculator, "addressApi", addressApi);
        ReflectionTestUtils.setField(calculator, "deliveryExpressTemplateService", templateService);
        ReflectionTestUtils.setField(calculator, "tradeConfigService", configService);
        return calculator;
    }

    private static DeliveryExpressTemplateRespBO templateBO(DeliveryExpressTemplateRespBO.Charge charge,
                                                             DeliveryExpressTemplateRespBO.Free free) {
        return new DeliveryExpressTemplateRespBO().setChargeMode(1).setCharge(charge).setFree(free);
    }

    private static TradePriceCalculateRespBO priceResult(Long templateId) {
        return new TradePriceCalculateRespBO()
                .setFreeDelivery(false)
                .setPrice(new TradePriceCalculateRespBO.Price())
                .setItems(List.of(orderItem(templateId)));
    }

    private static TradePriceCalculateRespBO.OrderItem orderItem(Long templateId) {
        return new TradePriceCalculateRespBO.OrderItem()
                .setSelected(true)
                .setCount(1)
                .setPrice(1000)
                .setDiscountPrice(0)
                .setDeliveryPrice(0)
                .setCouponPrice(0)
                .setPointPrice(0)
                .setVipPrice(0)
                .setPayPrice(1000)
                .setDeliveryTemplateId(templateId)
                .setDeliveryTypes(List.of(DeliveryTypeEnum.EXPRESS.getType()));
    }

}
