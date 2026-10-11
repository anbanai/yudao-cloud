package cn.iocoder.yudao.module.trade.framework.merchant;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.product.api.merchant.ProductMerchantApi;
import cn.iocoder.yudao.module.system.api.merchant.SystemMerchantUserApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MerchantSecurityServiceTest {

    @Mock
    private ProductMerchantApi productMerchantApi;
    @Mock
    private SystemMerchantUserApi systemMerchantUserApi;
    @InjectMocks
    private MerchantSecurityService merchantSecurityService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireOperatingMerchant_rejectsNullAvailabilityResponse() {
        setMerchantPrincipal(17L, 99L);
        when(systemMerchantUserApi.validateMerchantBinding(17L, 99L)).thenReturn(CommonResult.success(true));
        when(productMerchantApi.validateMerchantAvailable(99L)).thenReturn(null);

        assertThrows(AccessDeniedException.class, merchantSecurityService::requireOperatingMerchantId);
        verify(productMerchantApi).validateMerchantAvailable(99L);
    }

    @Test
    void requireOperatingMerchant_rejectsFalseAvailabilityResponse() {
        setMerchantPrincipal(17L, 99L);
        when(systemMerchantUserApi.validateMerchantBinding(17L, 99L)).thenReturn(CommonResult.success(true));
        when(productMerchantApi.validateMerchantAvailable(99L)).thenReturn(CommonResult.success(false));

        assertThrows(AccessDeniedException.class, merchantSecurityService::requireOperatingMerchantId);
        verify(productMerchantApi).validateMerchantAvailable(99L);
    }

    private static void setMerchantPrincipal(Long accountId, Long merchantId) {
        LoginUser loginUser = new LoginUser().setId(accountId).setUserType(UserTypeEnum.MERCHANT.getValue())
                .setInfo(Map.of("merchantId", merchantId.toString()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null));
    }

}
