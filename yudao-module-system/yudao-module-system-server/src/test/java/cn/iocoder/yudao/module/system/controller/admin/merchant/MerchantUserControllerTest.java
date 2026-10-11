package cn.iocoder.yudao.module.system.controller.admin.merchant;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.module.system.controller.admin.merchant.vo.MerchantUserPageReqVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MerchantUserControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void merchantTokenCannotCallAdminEndpointEvenIfItsIdMatchesAnAdmin() {
        Long overlappingUserId = 10L;
        LoginUser merchant = new LoginUser().setId(overlappingUserId)
                .setUserType(UserTypeEnum.MERCHANT.getValue());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(merchant, null, Collections.emptyList()));
        MerchantUserController controller = new MerchantUserController();

        assertThatThrownBy(() -> controller.getMerchantUserPage(new MerchantUserPageReqVO()))
                .isInstanceOf(AccessDeniedException.class);
    }

}
