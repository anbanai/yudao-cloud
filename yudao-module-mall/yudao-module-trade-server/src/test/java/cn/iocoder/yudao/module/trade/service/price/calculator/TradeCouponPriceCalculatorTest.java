package cn.iocoder.yudao.module.trade.service.price.calculator;
import cn.iocoder.yudao.module.promotion.api.coupon.CouponApi;
import cn.iocoder.yudao.module.promotion.api.coupon.dto.CouponRespDTO;
import cn.iocoder.yudao.module.trade.service.price.bo.*;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
class TradeCouponPriceCalculatorTest {
 TradeCouponPriceCalculator calculator; CouponApi api;
 @BeforeEach void init(){calculator=new TradeCouponPriceCalculator();api=mock(CouponApi.class);ReflectionTestUtils.setField(calculator,"couponApi",api);}
 CouponRespDTO coupon(long id){return new CouponRespDTO().setId(id).setName("券").setCouponType(1).setCanStack(true).setStackLimit(2).setStackWithPromotion(true).setUsePrice(0).setProductScope(1).setDiscountType(1).setDiscountPrice(100).setValidStartTime(LocalDateTime.now().minusDays(1)).setValidEndTime(LocalDateTime.now().plusDays(1));}
 TradePriceCalculateRespBO result(){return new TradePriceCalculateRespBO().setType(0).setPrice(new TradePriceCalculateRespBO.Price()).setItems(new ArrayList<>(Collections.singletonList(new TradePriceCalculateRespBO.OrderItem().setSpuId(10L).setSkuId(20L).setCategoryId(1L).setSelected(true).setCount(1).setPrice(1000).setPayPrice(1000).setCouponPrice(0).setPointPrice(0).setDiscountPrice(0).setDeliveryPrice(0).setVipPrice(0)))).setPromotions(new ArrayList<>());}
 @Test void stacksAndDeduplicatesLegacyId(){when(api.getCouponListByUserId(anyLong(),anyInt())).thenReturn(CommonResult.success(Arrays.asList(coupon(1),coupon(2))));TradePriceCalculateRespBO r=result();calculator.calculate(new TradePriceCalculateReqBO().setUserId(1L).setCouponId(1L).setCouponIds(Arrays.asList(1L,2L)),r);assertEquals(Arrays.asList(1L,2L),r.getEffectiveCouponIds());assertEquals(200,r.getPrice().getCouponPrice());assertEquals(800,r.getPrice().getPayPrice());}
 @Test void vetoReturnsExplicitReason(){CouponRespDTO c=coupon(2).setCanStack(false);when(api.getCouponListByUserId(anyLong(),anyInt())).thenReturn(CommonResult.success(Arrays.asList(coupon(1),c)));TradePriceCalculateRespBO r=result();assertThrows(RuntimeException.class,()->calculator.calculate(new TradePriceCalculateReqBO().setUserId(1L).setCouponIds(Arrays.asList(1L,2L)),r));assertNotNull(r.getCouponExclusionReasons().get(2L));}
 @Test void settlementExplainsExclusionsAndConverterRetainsSelectedIds(){CouponRespDTO c=coupon(2).setCanStack(false);when(api.getCouponListByUserId(anyLong(),anyInt())).thenReturn(CommonResult.success(Arrays.asList(coupon(1),c)));TradePriceCalculateRespBO r=result();calculator.calculate(new TradePriceCalculateReqBO().setUserId(1L).setCouponIds(Arrays.asList(1L,2L)).setStrictCouponSelection(false),r);assertEquals(Collections.singletonList(1L),r.getEffectiveCouponIds());assertNotNull(r.getCouponExclusionReasons().get(2L));
 cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementReqVO request=new cn.iocoder.yudao.module.trade.controller.app.order.vo.AppTradeOrderSettlementReqVO().setItems(Collections.emptyList()).setCouponIds(Arrays.asList(1L,2L));
 assertEquals(request.getCouponIds(),cn.iocoder.yudao.module.trade.convert.order.TradeOrderConvert.INSTANCE.convert(1L,request,Collections.emptyList()).getCouponIds());
 assertEquals(r.getEffectiveCouponIds(),cn.iocoder.yudao.module.trade.convert.order.TradeOrderConvert.INSTANCE.convert(r,null).getEffectiveCouponIds());
 }
 @Test void exchangeRequiresOneBoundSkuAndFullyDiscountsIt(){CouponRespDTO c=coupon(1).setCouponType(2).setExchangeSpuId(10L).setExchangeSkuId(20L);when(api.getCouponListByUserId(anyLong(),anyInt())).thenReturn(CommonResult.success(Collections.singletonList(c)));TradePriceCalculateRespBO r=result();calculator.calculate(new TradePriceCalculateReqBO().setUserId(1L).setCouponId(1L),r);assertEquals(0,r.getPrice().getPayPrice());TradePriceCalculateRespBO wrong=result();wrong.getItems().get(0).setSkuId(21L);assertThrows(RuntimeException.class,()->calculator.calculate(new TradePriceCalculateReqBO().setUserId(1L).setCouponId(1L),wrong));}
}
