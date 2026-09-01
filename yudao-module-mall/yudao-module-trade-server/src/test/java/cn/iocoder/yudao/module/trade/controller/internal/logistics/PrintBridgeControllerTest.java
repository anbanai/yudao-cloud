package cn.iocoder.yudao.module.trade.controller.internal.logistics;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.trade.controller.internal.logistics.vo.PrintBridgeStatusReqVO;
import cn.iocoder.yudao.module.trade.dal.dataobject.logistics.TradeLogisticsPrintDeviceDO;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsPrintBridgeService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PrintBridgeControllerTest {

    @Test
    void controller_ignoresRequestTenantUntilDeviceAuthentication() {
        assertThat(PrintBridgeController.class.getAnnotation(TenantIgnore.class)).isNotNull();
    }

    @Test
    void report_connectionTestReturnsNoContentWithoutPersistingEvent() {
        LogisticsPrintBridgeService service = mock(LogisticsPrintBridgeService.class);
        TradeLogisticsPrintDeviceDO device = new TradeLogisticsPrintDeviceDO().setId(1L);
        when(service.authenticate("secret", "packing-01")).thenReturn(device);
        PrintBridgeController controller = new PrintBridgeController();
        ReflectionTestUtils.setField(controller, "service", service);
        PrintBridgeStatusReqVO request = new PrintBridgeStatusReqVO().setEvent("connection_test")
                .setEventId("EVENT-TEST").setStatus("test");

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
        assertThat(controller.report("Bearer secret", "packing-01", true, request).getStatusCode().value())
                .isEqualTo(204);
        verify(service, never()).report(any(), any());
    }

}
