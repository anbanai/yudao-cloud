package cn.iocoder.yudao.module.product.controller;

import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.framework.web.config.YudaoWebAutoConfiguration;
import cn.iocoder.yudao.module.product.controller.admin.merchant.ProductMerchantApplicationController;
import cn.iocoder.yudao.module.product.controller.admin.merchant.ProductMerchantBusinessTypeController;
import cn.iocoder.yudao.module.product.controller.merchant.ProductMerchantSpuController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMerchantControllerMappingTest {

    @Test
    void productControllersHaveUniqueComponentNames() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            var scanner = new ClassPathBeanDefinitionScanner(context, false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

            scanner.scan("cn.iocoder.yudao.module.product.controller");

            assertThat(context.getBeanDefinitionNames())
                    .contains("adminProductMerchantApplicationController",
                            "merchantProductMerchantApplicationController",
                            "adminProductMerchantBusinessTypeController",
                            "merchantProductMerchantBusinessTypeController");
        }
    }

    @Test
    void productMerchantEndpointsUseTheCorrectApiPrefixes() {
        RequestMappingHandlerMapping handlerMapping = new YudaoWebAutoConfiguration()
                .webMvcRegistrations(new WebProperties()).getRequestMappingHandlerMapping();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("adminProductMerchantApplicationController",
                    ProductMerchantApplicationController.class);
            context.registerBean("merchantProductMerchantApplicationController",
                    cn.iocoder.yudao.module.product.controller.merchant.ProductMerchantApplicationController.class);
            context.registerBean("adminProductMerchantBusinessTypeController",
                    ProductMerchantBusinessTypeController.class);
            context.registerBean("merchantProductMerchantBusinessTypeController",
                    cn.iocoder.yudao.module.product.controller.merchant.ProductMerchantBusinessTypeController.class);
            context.refresh();
            handlerMapping.setApplicationContext(context);
            handlerMapping.afterPropertiesSet();

            Set<String> paths = handlerMapping.getHandlerMethods().keySet().stream()
                    .flatMap(mapping -> mapping.getPatternValues().stream())
                    .collect(Collectors.toSet());
            assertThat(paths).contains(
                    "/admin-api/product/merchant/application/history",
                    "/merchant-api/product/merchant/application/history",
                    "/admin-api/product/merchant/business-type/page",
                    "/merchant-api/product/merchant/business-type/list");
        }
    }

    @Test
    void merchantBusinessControllersAreExplicitlyDisabledWhileApplicationEndpointsRemainAvailable() {
        assertThat(ProductMerchantSpuController.class.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("denyAll()");
        assertThat(cn.iocoder.yudao.module.product.controller.merchant.ProductMerchantBrandController.class
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("denyAll()");
        assertThat(cn.iocoder.yudao.module.product.controller.merchant.ProductMerchantProfileController.class
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("denyAll()");
        assertThat(ProductMerchantApplicationController.class.getAnnotation(PreAuthorize.class)).isNull();
        assertThat(cn.iocoder.yudao.module.product.controller.merchant.ProductMerchantApplicationController.class
                .getAnnotation(PreAuthorize.class)).isNull();
    }

}
