package cn.iocoder.yudao.server;

import cn.iocoder.yudao.module.member.api.level.MemberLevelApi;
import cn.iocoder.yudao.module.member.api.user.MemberUserApi;
import cn.iocoder.yudao.module.member.api.user.dto.MemberUserRespDTO;
import cn.iocoder.yudao.module.promotion.api.coupon.CouponApi;
import cn.iocoder.yudao.module.promotion.api.coupon.CouponApiImpl;
import cn.iocoder.yudao.module.promotion.dal.dataobject.coupon.CouponTemplateDO;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponExchangeRecordMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponMapper;
import cn.iocoder.yudao.module.promotion.dal.mysql.coupon.CouponTemplateMapper;
import cn.iocoder.yudao.module.promotion.enums.coupon.CouponTakeTypeEnum;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponNewcomerService;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponServiceImpl;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponTemplateService;
import cn.iocoder.yudao.module.trade.api.order.TradeOrderApiImpl;
import cn.iocoder.yudao.module.trade.dal.mysql.order.TradeOrderMapper;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderQueryService;
import cn.iocoder.yudao.module.trade.service.order.TradeOrderUpdateService;
import cn.iocoder.yudao.module.trade.service.order.handler.TradeCouponOrderHandler;
import cn.iocoder.yudao.module.trade.service.price.calculator.TradeCouponPriceCalculator;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.test.util.ReflectionTestUtils;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponApiStartupTest {

    @Test
    void couponApiCanInitializeBeforeTradeAndStillCheckOrderHistory() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(ValidationAutoConfiguration.class, CouponApiConsumer.class, TradeOrderTestConfiguration.class,
                    CouponApiImpl.class, CouponServiceImpl.class,
                    CouponNewcomerService.class, TradeOrderApiImpl.class, TradeCouponOrderHandler.class,
                    TradeCouponPriceCalculator.class);
            context.registerBean(CouponTemplateService.class, () -> mock(CouponTemplateService.class));
            context.registerBean(CouponTemplateMapper.class, () -> mock(CouponTemplateMapper.class));
            context.registerBean(CouponMapper.class, () -> mock(CouponMapper.class));
            context.registerBean(CouponExchangeRecordMapper.class, () -> mock(CouponExchangeRecordMapper.class));
            context.registerBean(MemberUserApi.class, () -> mock(MemberUserApi.class));
            context.registerBean(MemberLevelApi.class, () -> mock(MemberLevelApi.class));
            context.registerBean(TradeOrderMapper.class, () -> mock(TradeOrderMapper.class));
            context.registerBean(TradeOrderQueryService.class, () -> mock(TradeOrderQueryService.class));
            context.refresh();

            CouponApi couponApi = context.getBean(CouponApi.class);
            assertThat(AopUtils.isAopProxy(couponApi)).isTrue();
            assertThat(ReflectionTestUtils.getField(context.getBean(TradeCouponOrderHandler.class), "couponApi"))
                    .isSameAs(couponApi);
            assertThat(ReflectionTestUtils.getField(context.getBean(TradeCouponPriceCalculator.class), "couponApi"))
                    .isSameAs(couponApi);

            long templateId = 1L;
            long userId = 2L;
            CouponTemplateDO template = new CouponTemplateDO().setId(templateId).setStatus(0)
                    .setNewcomerOnly(true).setTakeType(CouponTakeTypeEnum.USER.getType());
            when(context.getBean(CouponTemplateService.class).getCouponTemplate(templateId)).thenReturn(template);
            when(context.getBean(MemberUserApi.class).getUser(userId)).thenReturn(success(new MemberUserRespDTO()));
            when(context.getBean(TradeOrderQueryService.class).hasOrderHistory(userId)).thenReturn(true);

            assertThat(context.getBean(CouponNewcomerService.class).qualification(templateId, userId).getReasonCode())
                    .isEqualTo("ORDER_HISTORY_EXISTS");
        }
    }

    static class CouponApiConsumer {

        @Resource
        private CouponApi couponApi;

    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TradeOrderTestConfiguration {

        @Bean
        TradeOrderUpdateService tradeOrderUpdateService(TradeCouponOrderHandler orderHandler,
                                                       TradeCouponPriceCalculator priceCalculator) {
            // 模拟订单更新服务创建时加载订单处理器和计价器。
            return mock(TradeOrderUpdateService.class);
        }

    }

}
