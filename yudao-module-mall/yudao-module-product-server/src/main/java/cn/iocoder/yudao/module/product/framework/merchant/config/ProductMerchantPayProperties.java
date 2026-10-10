package cn.iocoder.yudao.module.product.framework.merchant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 商品域只持有支付应用标识和重试周期，易宝密钥始终由支付域管理。 */
@Data
@ConfigurationProperties(prefix = "yudao.product.merchant-pay")
public class ProductMerchantPayProperties {

    private String appKey = "mall";
    private int retrySeconds = 60;
    private int pollSeconds = 120;
}
