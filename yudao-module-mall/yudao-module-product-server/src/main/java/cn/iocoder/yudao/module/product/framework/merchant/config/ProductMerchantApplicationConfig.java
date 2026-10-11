package cn.iocoder.yudao.module.product.framework.merchant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProductMerchantApplicationProperties.class)
public class ProductMerchantApplicationConfig {
}
