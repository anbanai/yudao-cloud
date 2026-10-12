package cn.iocoder.yudao.module.promotion.controller.app;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.product.api.spu.ProductSpuApi;
import cn.iocoder.yudao.module.promotion.controller.app.coupon.AppCouponTemplateController;
import cn.iocoder.yudao.module.promotion.enums.common.PromotionProductScopeEnum;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponService;
import cn.iocoder.yudao.module.promotion.service.coupon.CouponTemplateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppCouponTemplateMerchantVisibilityTest {

    @InjectMocks
    private AppCouponTemplateController controller;

    @Mock
    private CouponTemplateService couponTemplateService;
    @Mock
    private CouponService couponService;
    @Mock
    private ProductSpuApi productSpuApi;

    @Test
    void spuScope_doesNotMatchCouponForUnavailableSpu() {
        givenUnavailableSpu();
        givenNoCoupons();

        assertThat(controller.getCouponTemplateList(100L, PromotionProductScopeEnum.SPU.getScope(), 10)
                .getCheckedData()).isEmpty();

        verify(couponTemplateService).getCouponTemplateList(anyList(),
                eq(PromotionProductScopeEnum.SPU.getScope()), eq(-1L), eq(10));
    }

    @Test
    void categoryScope_doesNotMatchCouponForUnavailableSpu() {
        givenUnavailableSpu();
        givenNoCoupons();

        assertThat(controller.getCouponTemplateList(100L, PromotionProductScopeEnum.CATEGORY.getScope(), 10)
                .getCheckedData()).isEmpty();

        verify(couponTemplateService).getCouponTemplateList(anyList(),
                eq(PromotionProductScopeEnum.CATEGORY.getScope()), eq(-1L), eq(10));
    }

    private void givenUnavailableSpu() {
        when(productSpuApi.getAvailableSpuList(List.of(100L))).thenReturn(CommonResult.success(List.of()));
    }

    private void givenNoCoupons() {
        when(couponTemplateService.getCouponTemplateList(anyList(), any(), any(), eq(10))).thenReturn(List.of());
        when(couponService.getUserCanCanTakeMap(any(), anyList())).thenReturn(Map.of());
    }

}
