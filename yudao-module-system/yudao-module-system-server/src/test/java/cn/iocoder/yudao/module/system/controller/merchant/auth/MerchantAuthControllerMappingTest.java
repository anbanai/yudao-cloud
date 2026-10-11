package cn.iocoder.yudao.module.system.controller.merchant.auth;

import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.config.YudaoWebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantAuthControllerMappingTest {

    @Test
    void authEndpointsUseSingleMerchantApiPrefix() {
        RequestMappingHandlerMapping handlerMapping = new YudaoWebAutoConfiguration()
                .webMvcRegistrations(new WebProperties()).getRequestMappingHandlerMapping();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(MerchantAuthController.class, MerchantAuthController::new);
            context.refresh();
            handlerMapping.setApplicationContext(context);
            handlerMapping.afterPropertiesSet();

            Set<String> paths = handlerMapping.getHandlerMethods().keySet().stream()
                    .flatMap(mapping -> mapping.getPatternValues().stream())
                    .collect(Collectors.toSet());
            assertThat(paths).contains(
                    "/merchant-api/system/auth/register",
                    "/merchant-api/system/auth/login",
                    "/merchant-api/system/auth/refresh-token",
                    "/merchant-api/system/auth/logout",
                    "/merchant-api/system/auth/profile");
            assertThat(paths).doesNotContain("/merchant-api/merchant-api/system/auth/login");
        }
    }

}
