package cn.iocoder.yudao.module.trade.controller.admin.notify.logistics;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.trade.controller.notify.logistics.vo.SfRoutePushReqVO;
import cn.iocoder.yudao.module.trade.service.logistics.SfRoutePushService;
import jakarta.annotation.security.PermitAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SfRoutePushControllerTest {

    @Test
    void routePushReturnsExactSfSuccessContract() throws Exception {
        SfRoutePushService service = mock(SfRoutePushService.class);
        SfRoutePushController controller = controller(service);
        SfRoutePushReqVO request = JsonUtils.parseObject("""
                {"Body":{"WaybillRoute":[{"mailno":"SF001","orderid":"YD-1-2","id":"3"}]}}
                """, SfRoutePushReqVO.class);

        var response = controller.routePush(request);

        assertThat(response.getReturnCode()).isEqualTo("0000");
        assertThat(response.getReturnMsg()).isEqualTo("成功");
        assertThat(JsonUtils.toJsonString(response)).isEqualTo("{\"return_code\":\"0000\",\"return_msg\":\"成功\"}");
        assertThat(request.getBody().getWaybillRoute().get(0).getMailno()).isEqualTo("SF001");
        verify(service).process(request);
        Method method = SfRoutePushController.class.getMethod("routePush", SfRoutePushReqVO.class);
        assertThat(method.getAnnotation(PermitAll.class)).isNotNull();
        assertThat(method.getAnnotation(TenantIgnore.class)).isNotNull();
    }

    @Test
    void routePushReturnsExactSfFailureContractWithoutLeakingException() {
        SfRoutePushService service = mock(SfRoutePushService.class);
        SfRoutePushReqVO request = new SfRoutePushReqVO();
        doThrow(new IllegalArgumentException("secret database detail")).when(service).process(request);

        var response = controller(service).routePush(request);

        assertThat(response.getReturnCode()).isEqualTo("1000");
        assertThat(response.getReturnMsg()).isEqualTo("处理失败");
    }

    private static SfRoutePushController controller(SfRoutePushService service) {
        SfRoutePushController controller = new SfRoutePushController();
        ReflectionTestUtils.setField(controller, "service", service);
        return controller;
    }
}
