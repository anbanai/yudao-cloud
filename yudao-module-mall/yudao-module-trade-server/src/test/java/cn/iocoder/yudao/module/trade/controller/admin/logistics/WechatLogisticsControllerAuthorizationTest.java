package cn.iocoder.yudao.module.trade.controller.admin.logistics;

import cn.iocoder.yudao.module.trade.controller.admin.logistics.vo.WechatLogisticsWaybillCreateReqVO;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WechatLogisticsControllerAuthorizationTest {

    @Test
    void shouldAllowOrderDeliveryPermissionForOneClickWaybillFlow() throws NoSuchMethodException {
        PreAuthorize createAuthorization = WechatLogisticsController.class
                .getMethod("createWaybill", WechatLogisticsWaybillCreateReqVO.class)
                .getAnnotation(PreAuthorize.class);
        PreAuthorize confirmAuthorization = WechatLogisticsController.class
                .getMethod("confirmPrint", Long.class)
                .getAnnotation(PreAuthorize.class);

        assertEquals("@ss.hasAnyPermissions('trade:logistics:waybill:create', 'trade:order:update')",
                createAuthorization.value());
        assertEquals("@ss.hasAnyPermissions('trade:logistics:waybill:confirm-print', 'trade:order:update')",
                confirmAuthorization.value());
    }
}
