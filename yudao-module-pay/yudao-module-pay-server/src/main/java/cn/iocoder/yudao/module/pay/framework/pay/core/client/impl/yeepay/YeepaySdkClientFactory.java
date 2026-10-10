package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay;

import com.yeepay.yop.sdk.auth.credentials.PKICredentialsItem;
import com.yeepay.yop.sdk.auth.credentials.YopPKICredentials;
import com.yeepay.yop.sdk.base.security.cert.parser.YopCertParserFactory;
import com.yeepay.yop.sdk.config.enums.CertStoreType;
import com.yeepay.yop.sdk.config.provider.file.YopCertConfig;
import com.yeepay.yop.sdk.auth.credentials.provider.YopPlatformCredentialsProviderRegistry;
import com.yeepay.yop.sdk.auth.credentials.provider.YopCredentialsProviderRegistry;
import com.yeepay.yop.sdk.security.CertTypeEnum;
import com.yeepay.yop.sdk.security.cert.YopCertCategory;
import com.yeepay.yop.sdk.security.cert.YopPublicKey;
import com.yeepay.yop.sdk.utils.X509CertUtils;
import com.yeepay.yop.sdk.client.ClientConfiguration;
import com.yeepay.yop.sdk.http.DefaultRetryPolicy;
import com.yeepay.yop.sdk.service.common.YopClient;
import com.yeepay.yop.sdk.service.common.YopClientBuilder;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 官方易宝 YOP SDK 的客户端和密钥适配层。 */
final class YeepaySdkClientFactory {

    private static final Map<String, YopClient> CLIENTS = new ConcurrentHashMap<>();

    private YeepaySdkClientFactory() {
    }

    static YopClient getClient(YeepayPayClientConfig config) {
        YeepaySdkJacksonCompatibility.install();
        String key = config.getSdkProvider() + "@" + config.getSdkEnv() + "@"
                + config.getAppKey() + "@" + config.getServerUrl() + "@" + config.getYosServerUrl();
        registerCredentials(config);
        URI endpoint = URI.create(config.getServerUrl());
        URI yosEndpoint = URI.create(config.getYosServerUrl());
        return CLIENTS.computeIfAbsent(key, ignored -> YopClientBuilder.builder()
                .withProvider(config.getSdkProvider())
                .withEnv(config.getSdkEnv())
                .withEndpoint(config.getServerUrl())
                // The SDK gives preferredServerRoots precedence over endpoint. Supplying the
                // configured endpoint as the preferred route keeps test gateways and explicitly
                // configured production gateways on the same official routing path.
                .withPreferredEndPoint(List.of(endpoint))
                .withYosEndpoint(config.getYosServerUrl())
                .withPreferredYosEndPoint(List.of(yosEndpoint))
                .withSandboxEndPoint(config.getServerUrl())
                .withClientId("yudao-yeepay-" + Integer.toHexString(key.hashCode()))
                // State-changing calls must never be repeated by the transport SDK after an
                // ambiguous timeout. The payment/fund state machines own retries and use stable
                // request numbers; only read-only query calls are retried in YeepayPayClient.
                // YOP 4.4.15 falls back to its default policy when maxRetryCount <= 0, so set
                // the official policy explicitly instead of relying on that legacy knob.
                .withClientConfiguration(new ClientConfiguration()
                        .withMaxRetryCount(0)
                        .withRetryPolicy(new DefaultRetryPolicy(0, 0)))
                .withPlatformCredentialsProvider(YeepayPlatformCredentialsProvider.INSTANCE)
                .build());
    }

    static YopPKICredentials credentials(YeepayPayClientConfig config) {
        CertTypeEnum certType = config.getSigningCertTypeEnum();
        return new YopPKICredentials(config.getAppKey(), new PKICredentialsItem(
                readPrivateKey(config.getRuntimePrivateKey(), certType), certType));
    }

    /** 注册官方 SDK 的商户私钥凭证，供请求签名和回调数字信封解密共同使用。 */
    static void registerCredentials(YeepayPayClientConfig config) {
        YeepaySdkJacksonCompatibility.install();
        registerPlatformKey(config);
        YeepayCredentialsProvider.INSTANCE.register(config.getSdkProvider(), config.getSdkEnv(),
                config.getAppKey(), credentials(config), config.getRuntimeSm4CallbackKey());
        YopCredentialsProviderRegistry.registerProvider(YeepayCredentialsProvider.INSTANCE);
    }

    static PrivateKey readPrivateKey(String text) {
        return readPrivateKey(text, CertTypeEnum.RSA2048);
    }

    static PrivateKey readPrivateKey(String text, CertTypeEnum certType) {
        try {
            YopCertConfig config = new YopCertConfig().withCertType(certType)
                    .withStoreType(CertStoreType.STRING).withValue(stripPem(text, "PRIVATE KEY"));
            return (PrivateKey) YopCertParserFactory.getCertParser(YopCertCategory.PRIVATE, certType).parse(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("易宝平台私钥解析失败", e);
        }
    }

    static PublicKey readPublicKey(String text) {
        return readPublicKey(text, CertTypeEnum.RSA2048);
    }

    static PublicKey readPublicKey(String text, CertTypeEnum certType) {
        try {
            if (text.contains("BEGIN CERTIFICATE")) {
                return CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(decodeCertificate(text))).getPublicKey();
            }
            YopCertConfig config = new YopCertConfig().withCertType(certType)
                    .withStoreType(CertStoreType.STRING).withValue(stripPem(text, "PUBLIC KEY"));
            // The official parser returns a YopPublicKey wrapper so that certificate-backed
            // and raw-key credentials share one API. Always unwrap it before registering the
            // JCA key with the platform credential provider.
            YopPublicKey parsed = (YopPublicKey) YopCertParserFactory
                    .getCertParser(YopCertCategory.PUBLIC, certType).parse(config);
            return parsed.getPublicKey();
        } catch (Exception e) {
            throw new IllegalArgumentException("易宝平台公钥或证书解析失败", e);
        }
    }

    private static void registerPlatformKey(YeepayPayClientConfig config) {
        String verificationKey = config.getVerificationKey();
        if (verificationKey == null || verificationKey.isBlank()) {
            throw new IllegalStateException("易宝平台公钥或证书未配置");
        }
        CertTypeEnum certType = config.getSigningCertTypeEnum();
        String serialNo = config.getEffectivePlatformCertSerialNo();
        if ((serialNo == null || serialNo.isBlank()) && verificationKey.contains("BEGIN CERTIFICATE")) {
            serialNo = readCertificateSerialNo(verificationKey);
        }
        YeepayPlatformCredentialsProvider.INSTANCE.register(config.getSdkProvider(), config.getSdkEnv(),
                config.getAppKey(), serialNo, readPublicKey(verificationKey, certType), certType);
        // DigitalEnvelopeUtils 使用全局 provider，注册同一个多 appKey provider 以支持回调解密。
        YopPlatformCredentialsProviderRegistry.registerProvider(YeepayPlatformCredentialsProvider.INSTANCE);
    }

    private static String readCertificateSerialNo(String certificate) {
        try {
            X509Certificate x509 = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(decodeCertificate(certificate)));
            String decimalSerial = x509.getSerialNumber().toString();
            return X509CertUtils.parseToHex(decimalSerial);
        } catch (Exception e) {
            throw new IllegalArgumentException("易宝平台证书序列号解析失败", e);
        }
    }

    private static String stripPem(String text, String label) {
        return text.replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "").replaceAll("\\s", "");
    }

    private static byte[] decodeCertificate(String text) {
        return java.util.Base64.getDecoder().decode(text.replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "").replaceAll("\\s", ""));
    }
}
