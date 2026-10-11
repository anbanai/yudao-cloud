package cn.iocoder.yudao.module.product.framework.merchant;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.mysql.merchant.ProductMerchantMapper;
import cn.iocoder.yudao.module.product.service.merchant.ProductMerchantService;
import cn.iocoder.yudao.module.system.api.merchant.SystemMerchantUserApi;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantSecurityServiceTest {

    @Mock
    private ProductMerchantMapper merchantMapper;
    @Mock
    private ProductMerchantService merchantService;
    @Mock
    private SystemMerchantUserApi systemMerchantUserApi;
    @InjectMocks
    private MerchantSecurityService merchantSecurityService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requireMerchantAccountId_returnsOnlyMerchantPrincipal() {
        setPrincipal(new LoginUser().setId(17L).setUserType(UserTypeEnum.MERCHANT.getValue()));

        assertThat(merchantSecurityService.requireMerchantAccountId()).isEqualTo(17L);
    }

    @Test
    void requireMerchantAccountId_rejectsAdminPrincipal() {
        setPrincipal(new LoginUser().setId(17L).setUserType(UserTypeEnum.ADMIN.getValue()));

        assertThrows(AccessDeniedException.class, merchantSecurityService::requireMerchantAccountId);
        verifyNoInteractions(merchantMapper, merchantService);
    }

    @Test
    void requireOwnedMerchant_rejectsMerchantOwnedByAnotherAccount() {
        setPrincipal(new LoginUser().setId(17L).setUserType(UserTypeEnum.MERCHANT.getValue()));
        when(merchantMapper.selectByOwnerAccountId(17L)).thenReturn(null);

        assertThrows(AccessDeniedException.class, () -> merchantSecurityService.requireOwnedMerchant(99L));
        verify(merchantMapper).selectByOwnerAccountId(17L);
        verifyNoInteractions(merchantService);
    }

    @Test
    void requireOperatingMerchant_checksApprovedStateThroughDomainService() {
        setPrincipal(new LoginUser().setId(17L).setUserType(UserTypeEnum.MERCHANT.getValue()));
        ProductMerchantDO merchant = new ProductMerchantDO().setId(99L).setOwnerAccountId(17L);
        when(merchantMapper.selectByOwnerAccountId(17L)).thenReturn(merchant);
        when(systemMerchantUserApi.validateMerchantBinding(17L, 99L)).thenReturn(CommonResult.success(true));

        assertThat(merchantSecurityService.requireOperatingMerchant()).isSameAs(merchant);
        verify(merchantService).validateMerchantAvailable(99L);
    }

    @Test
    void requireOperatingMerchant_rejectsDisabledAccountEvenWithCachedToken() {
        setPrincipal(new LoginUser().setId(17L).setUserType(UserTypeEnum.MERCHANT.getValue()));
        when(merchantMapper.selectByOwnerAccountId(17L))
                .thenReturn(new ProductMerchantDO().setId(99L).setOwnerAccountId(17L));
        when(systemMerchantUserApi.validateMerchantBinding(17L, 99L)).thenReturn(CommonResult.success(false));

        assertThrows(AccessDeniedException.class, merchantSecurityService::requireOperatingMerchant);
        verifyNoInteractions(merchantService);
    }

    private static void setPrincipal(LoginUser loginUser) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null));
    }

}
