package cn.iocoder.yudao.framework.web.core.util;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.web.config.WebProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class WebFrameworkUtilsTest {

    @BeforeEach
    void setUp() {
        WebProperties properties = new WebProperties();
        properties.setMerchantApi(new WebProperties.Api("/merchant-api", "**.controller.merchant.**"));
        new WebFrameworkUtils(properties);
    }

    @Test
    void getLoginUserType_merchantApi_returnsMerchant() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/merchant-api/product/merchant/application/mine");

        assertThat(WebFrameworkUtils.getLoginUserType(request)).isEqualTo(UserTypeEnum.MERCHANT.getValue());
    }

    @Test
    void getLoginUserType_adminAndAppApi_preserveExistingTypes() {
        MockHttpServletRequest adminRequest = new MockHttpServletRequest();
        adminRequest.setServletPath("/admin-api/system/auth/profile");
        MockHttpServletRequest appRequest = new MockHttpServletRequest();
        appRequest.setServletPath("/app-api/member/user/get");

        assertThat(WebFrameworkUtils.getLoginUserType(adminRequest)).isEqualTo(UserTypeEnum.ADMIN.getValue());
        assertThat(WebFrameworkUtils.getLoginUserType(appRequest)).isEqualTo(UserTypeEnum.MEMBER.getValue());
    }

}
