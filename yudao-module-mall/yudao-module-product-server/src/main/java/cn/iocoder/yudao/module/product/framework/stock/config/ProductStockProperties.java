package cn.iocoder.yudao.module.product.framework.stock.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;

/**
 * 商品库存配置项。
 */
@ConfigurationProperties(prefix = "yudao.product.stock")
@RefreshScope
@Data
public class ProductStockProperties {

    /**
     * 是否使用 PolarDB 热点行优化库存更新路径。
     *
     * 默认关闭，确保未配置 Nacos 时保持原有行为。
     */
    private boolean hotspotEnabled;

}
