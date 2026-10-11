package cn.iocoder.yudao.module.product.framework.merchant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.util.StringUtils;

import java.net.URI;

@ConfigurationProperties(prefix = "yudao.product.merchant.application")
@RefreshScope
@Data
public class ProductMerchantApplicationProperties {

    private String agreementVersion;
    private String agreementUrl;
    private String privacyPolicyVersion;
    private String privacyPolicyUrl;

    public boolean isConfigured() {
        return StringUtils.hasText(agreementVersion) && isReadableUrl(agreementUrl)
                && StringUtils.hasText(privacyPolicyVersion) && isReadableUrl(privacyPolicyUrl);
    }

    public boolean matches(String submittedAgreementVersion, String submittedPrivacyPolicyVersion) {
        return isConfigured() && agreementVersion.equals(submittedAgreementVersion)
                && privacyPolicyVersion.equals(submittedPrivacyPolicyVersion);
    }

    private boolean isReadableUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String url = value.trim();
        if (url.startsWith("/") && !url.startsWith("//") && !url.contains("\\")) {
            return true;
        }
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
