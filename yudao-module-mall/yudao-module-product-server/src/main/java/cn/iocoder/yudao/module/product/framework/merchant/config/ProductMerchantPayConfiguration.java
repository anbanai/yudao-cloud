package cn.iocoder.yudao.module.product.framework.merchant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(ProductMerchantPayProperties.class)
public class ProductMerchantPayConfiguration {
}
