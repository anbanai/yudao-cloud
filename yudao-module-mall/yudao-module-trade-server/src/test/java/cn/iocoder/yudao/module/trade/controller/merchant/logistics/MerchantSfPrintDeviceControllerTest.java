package cn.iocoder.yudao.module.trade.controller.merchant.logistics;

import cn.iocoder.yudao.module.trade.framework.merchant.MerchantSecurityService;
import cn.iocoder.yudao.module.trade.service.logistics.LogisticsManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantSfPrintDeviceControllerTest {

    @Test
    void enrollDeviceUsesLoggedInMerchantAndPreventsCredentialCaching() {
        var security = mock(MerchantSecurityService.class);
        var management = mock(LogisticsManagementService.class);
        when(security.requireOperatingMerchantId()).thenReturn(41L);
        var response = new MockHttpServletResponse();

        new MerchantSfPrintDeviceController(security, management).enrollDevice(response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        verify(management).enrollDevice(41L);
    }
}
