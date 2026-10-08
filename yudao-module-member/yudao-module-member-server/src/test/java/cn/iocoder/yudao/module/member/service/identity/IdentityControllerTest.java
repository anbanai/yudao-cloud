package cn.iocoder.yudao.module.member.service.identity;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.member.controller.app.auth.AppAuthController;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthBridgeLoginReqVO;
import cn.iocoder.yudao.module.member.controller.app.auth.vo.AppAuthLoginRespVO;
import cn.iocoder.yudao.module.member.controller.app.identity.AppIdentityController;
import cn.iocoder.yudao.module.member.service.auth.MemberAuthService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityControllerTest {
    @Test void phoneSharingCarriesDisplayedRevisionAndAllowsRevisionlessWithdrawal() throws Exception {
        MemberIdentityService identity = mock(MemberIdentityService.class);
        AppIdentityController controller = new AppIdentityController(identity);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        when(identity.setPhoneSharing(null, true, "displayed-revision")).thenThrow(IdentityPolicy.conflict());
        var enable = json.readValue("{\"enabled\":true,\"consentRevision\":\"displayed-revision\"}", AppIdentityController.SharingRequest.class);
        assertEquals(1004019001, assertThrows(ServiceException.class, () -> controller.phoneSharing(enable)).getCode());
        var withdraw = json.readValue("{\"enabled\":false}", AppIdentityController.SharingRequest.class);
        assertEquals(0, controller.phoneSharing(withdraw).getCode());
        verify(identity).setPhoneSharing(null, false, null);
    }
    @Test void bridgeEnvelopeContainsOnlyMallSessionAndSupportsFreshRequestValidation() {
        AppAuthController controller = new AppAuthController(); MemberAuthService auth = mock(MemberAuthService.class);
        ReflectionTestUtils.setField(controller, "authService", auth);
        AppAuthBridgeLoginReqVO request = request();
        AppAuthLoginRespVO session = new AppAuthLoginRespVO(10L, "B-access", "B-refresh", LocalDateTime.now(), "B-openid");
        when(auth.bridgeLogin(request)).thenReturn(session);
        assertSame(session, controller.bridgeLogin(request).getData());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
            request.setRequestId("short"); assertFalse(factory.getValidator().validate(request).isEmpty());
            request.setRequestId("request-000000001"); request.setLoginCode("");
            assertFalse(factory.getValidator().validate(request).isEmpty());
        }
    }
    @Test void conflictDoesNotProducePartialSession() {
        AppAuthController controller = new AppAuthController(); MemberAuthService auth = mock(MemberAuthService.class);
        ReflectionTestUtils.setField(controller, "authService", auth);
        AppAuthBridgeLoginReqVO request = request(); when(auth.bridgeLogin(request)).thenThrow(IdentityPolicy.conflict());
        assertEquals(1004019001, assertThrows(ServiceException.class, () -> controller.bridgeLogin(request)).getCode());
    }
    @Test void unexpectedErrorsAreSanitizedBeforeGenericRequestBodyLogging() {
        AppAuthController controller = new AppAuthController(); MemberAuthService auth = mock(MemberAuthService.class);
        ReflectionTestUtils.setField(controller, "authService", auth);
        AppAuthBridgeLoginReqVO request = request(); when(auth.bridgeLogin(request)).thenThrow(new RuntimeException("raw phone / code"));
        ServiceException error = assertThrows(ServiceException.class, () -> controller.bridgeLogin(request));
        assertEquals(1004019002, error.getCode()); assertFalse(error.getMessage().contains("raw phone"));
    }
    private AppAuthBridgeLoginReqVO request() {
        AppAuthBridgeLoginReqVO request = new AppAuthBridgeLoginReqVO();
        request.setHandoffCode("opaque"); request.setLoginCode("fresh");
        request.setRequestId("request-000000001"); request.setSourceAppId("wxA"); return request;
    }
}
