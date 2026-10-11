package cn.iocoder.yudao.module.system.controller.merchant.auth;

import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.config.YudaoWebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantApiPathPrefixTest {

    @Test
    void merchantControllerGetsMerchantApiPrefix() {
        WebProperties properties = new WebProperties();
        RequestMappingHandlerMapping handlerMapping = new YudaoWebAutoConfiguration()
                .webMvcRegistrations(properties).getRequestMappingHandlerMapping();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(TestMerchantController.class);
            context.refresh();
            handlerMapping.setApplicationContext(context);
            handlerMapping.afterPropertiesSet();

            assertThat(handlerMapping.getHandlerMethods().keySet())
                    .anySatisfy(mapping -> assertThat(mapping.getPatternValues())
                            .contains("/merchant-api/system/auth/ping"));
        }
    }

    @RestController
    @RequestMapping("/system/auth")
    static class TestMerchantController {

        @GetMapping("/ping")
        String ping() {
            return "ok";
        }

    }

}
