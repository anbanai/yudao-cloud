package cn.iocoder.yudao.module.product.framework.stock.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProductStockProperties.class)
public class ProductStockConfig {
}
