package cn.iocoder.yudao.module.promotion.controller.app.coupon;

import cn.iocoder.yudao.module.promotion.service.coupon.CouponNewcomerService;
import cn.iocoder.yudao.module.system.api.social.SocialUserApi;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppCouponTemplateControllerTest {

    @Mock
    private SocialUserApi socialUserApi;
    @Mock
    private CouponNewcomerService newcomerService;
    @InjectMocks
    private AppCouponTemplateController controller;

    private final AppCouponTemplateController.WeixinVisitorReqVO visitor =
            new AppCouponTemplateController.WeixinVisitorReqVO("wx-code");

    @Test
    void returningVisitorUsesBoundMemberOrderHistory() {
        when(socialUserApi.getBoundWeixinMember(visitor.code())).thenReturn(success(9L));
        when(newcomerService.canShowVisitorEntry(9L)).thenReturn(true, false);
        assertTrue(controller.canShowWeixinVisitorEntry(visitor).getCheckedData());
        assertFalse(controller.canShowWeixinVisitorEntry(visitor).getCheckedData());
    }

    @Test
    void unknownAndUnboundIdentitiesStayHidden() {
        when(socialUserApi.getBoundWeixinMember(visitor.code())).thenReturn(success(null));
        assertFalse(controller.canShowWeixinVisitorEntry(visitor).getCheckedData());
        verifyNoInteractions(newcomerService);
    }
}
