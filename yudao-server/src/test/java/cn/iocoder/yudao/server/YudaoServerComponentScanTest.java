package cn.iocoder.yudao.server;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;

import static org.assertj.core.api.Assertions.assertThat;

class YudaoServerComponentScanTest {

    @Test
    void merchantSecurityServicesHaveUniqueComponentNames() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(context);

            scanner.scan("cn.iocoder.yudao.module");

            assertThat(context.getBeanDefinitionNames())
                    .contains("productMerchantSecurityService", "tradeMerchantSecurityService");
        }
    }

}
