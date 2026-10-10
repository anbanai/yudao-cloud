package cn.iocoder.yudao.module.pay.controller.admin.notify;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PayNotifyControllerRouteTest {

    @Test
    void testYeepayCallbacksRequireTrustedChannelId() throws Exception {
        for (String methodName : List.of("notifyYeepayPayment", "notifyYeepayClearing",
                "notifyYeepayDivide", "notifyYeepayRefund", "notifyYeepayDivideBack")) {
            Method method = PayNotifyController.class.getDeclaredMethod(methodName,
                    Long.class, java.util.Map.class, String.class, java.util.Map.class);
            RequestParam channelId = method.getParameters()[0].getAnnotation(RequestParam.class);
            assertTrue(channelId.required(), methodName + " must require the server-generated channelId");
        }
    }
}
