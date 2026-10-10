package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay;

import cn.iocoder.yudao.framework.common.util.validation.ValidationUtils;
import cn.iocoder.yudao.module.pay.framework.pay.core.client.PayClientConfig;
import com.yeepay.yop.sdk.security.CertTypeEnum;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 易宝聚合支付配置。
 *
 * <p>密钥只允许通过配置中心或密钥管理系统注入，禁止写入业务表和日志。</p>
 */
@Data
@EqualsAndHashCode(doNotUseGetters = true)
public class YeepayPayClientConfig implements PayClientConfig {

    public static final String DEFAULT_SERVER_URL = "https://openapi.yeepay.com/yop-center";
    public static final String FUND_PROCESS_TYPE_DELAY_SETTLE = "DELAY_SETTLE";
    public static final String FUND_PROCESS_TYPE_REAL_TIME_DIVIDE = "REAL_TIME_DIVIDE";

    /** 原材料仅用于运行时注入或内存测试；渠道表只保存引用。 */
    @JsonIgnore
    private String appKey;
    private String appKeyRef;
    @JsonIgnore
    private String privateKey;
    /** 密钥管理系统中的私钥引用。私钥正文不写入支付渠道配置表。 */
    private String privateKeyRef;
    @JsonIgnore
    private String publicKey;
    private String publicKeyRef;
    /** 可选的易宝 X.509 证书；与 publicKey 二选一。 */
    @JsonIgnore
    private String publicCertificate;
    private String publicCertificateRef;

    /** 易宝网关地址，测试环境通过配置覆盖。 */
    @NotBlank(message = "易宝网关地址不能为空")
    private String serverUrl = DEFAULT_SERVER_URL;
    /** 易宝 YOS 文件服务地址，不能与 YOP API 网关混用。 */
    @NotBlank(message = "易宝 YOS 地址不能为空")
    private String yosServerUrl = "https://yos.yeepay.com/yop-center";
    /** 平台统一微信小程序 AppID。 */
    @NotBlank(message = "微信小程序 AppID 不能为空")
    private String appId;
    /** 聚合订单的固定收款商编，不能从商品商家中动态选择。 */
    @NotBlank(message = "平台收款商编不能为空")
    private String collectionMerchantNo;
    /** 易宝平台商编，部分接口称 parentMerchantNo。 */
    @NotBlank(message = "易宝平台商编不能为空")
    private String parentMerchantNo;
    /** YOP SDK 路由空间，生产为 prod，测试环境通常为 qa。 */
    private String sdkProvider = "yeepay";
    private String sdkEnv = "prod";
    /** 易宝平台证书序列号；证书轮换时与 publicCertificate 一起更新。 */
    private String platformCertSerialNo;
    /** SDK 请求签名证书类型，默认 RSA；易宝 SM 版应用配置为 SM2。 */
    private String signingCertType = CertTypeEnum.RSA2048.getValue();
    /** SM2/SM4 旧式回调解密密钥，由密钥管理系统或环境注入。 */
    @JsonIgnore
    private String sm4CallbackKey;
    /** 入网角色，易宝联调确认后通过配置覆盖。 */
    private String businessRole = "SETTLED_MERCHANT";
    /** 分账和资金归还权限编码。 */
    private String sharePermission = "SHARE";
    private String divideBackPermission = "EASYSHARE_BACK_MERCHANT";
    private String fundProcessType = FUND_PROCESS_TYPE_DELAY_SETTLE;

    /** 联调和易宝合同确认全额可分账后才允许新支付，默认关闭。 */
    private boolean fullAmountDivideConfirmed;
    /** PLATFORM_FEE_ACCOUNT：平台费用账户承担；FULL_SUBSIDY_PROMOTION：运营预配100%补贴。 */
    private String feeBearerMode;
    /** 易宝运营配置的全额手续费补贴活动号，不能从小程序请求中传入。 */
    private String feeSubsidyPromotionNo;

    /**
     * 新支付的硬闸门。确认必须包含费用账户/补贴资金保障，不代表虚构的余额查询。
     * 既有订单查询、回调、退款和归还不调用此方法。
     */
    public void validateFullAmountDivideGuarantee() {
        validateFirstPhaseFundProcess();
        if (!fullAmountDivideConfirmed) {
            throw new IllegalStateException("易宝全额分账及手续费保障尚未确认，禁止发起新支付");
        }
        if (!"PLATFORM_FEE_ACCOUNT".equals(feeBearerMode)
                && !"FULL_SUBSIDY_PROMOTION".equals(feeBearerMode)) {
            throw new IllegalStateException("易宝手续费必须由平台费用账户承担或配置全额补贴活动");
        }
        if ("FULL_SUBSIDY_PROMOTION".equals(feeBearerMode)
                && !org.springframework.util.StringUtils.hasText(feeSubsidyPromotionNo)) {
            throw new IllegalStateException("全额补贴模式必须配置易宝运营确认的100%手续费补贴活动编号");
        }
        if (getFeeSubsidyInfo() != null && getFeeSubsidyInfo().length() > 256) {
            throw new IllegalStateException("易宝手续费补贴信息不能超过256字符");
        }
    }

    /** 官方新版契约：JSON ARRAY仅一条，预配置活动只提交活动编号。 */
    @JsonIgnore
    public String getFeeSubsidyInfo() {
        if (!"FULL_SUBSIDY_PROMOTION".equals(feeBearerMode)) return null;
        return cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(
                java.util.List.of(java.util.Map.of("feeSubsidyPromotionNo",
                        feeSubsidyPromotionNo == null ? "" : feeSubsidyPromotionNo)));
    }


    private String paymentNotifyUrl;
    private String clearingNotifyUrl;
    private String divideNotifyUrl;
    private String refundNotifyUrl;
    private String divideBackNotifyUrl;
    /** 易宝商户入网结果回调地址，平台统一接收后按 requestNo 更新入网状态。 */
    private String merchantNotifyUrl;

    @Min(value = 1, message = "易宝请求超时秒数必须大于 0")
    @Max(value = 60, message = "易宝请求超时秒数不能超过 60")
    private Integer timeoutSeconds = 10;
    @Min(value = 1, message = "易宝连接超时秒数必须大于 0")
    @Max(value = 60, message = "易宝连接超时秒数不能超过 60")
    private Integer connectTimeoutSeconds = 5;
    @Min(value = 0, message = "易宝自动重试次数不能小于 0")
    @Max(value = 10, message = "易宝自动重试次数不能超过 10")
    private Integer maxRetries = 3;
    @Min(value = 1, message = "易宝重试退避秒数必须大于 0")
    @Max(value = 300, message = "易宝重试退避秒数不能超过 300")
    private Integer retryBackoffSeconds = 3;
    @Min(value = 1, message = "易宝限流每秒请求数必须大于 0")
    private Integer rateLimitPerSecond = 20;
    @Min(value = 1, message = "单笔订单分账接收方数量必须大于 0")
    private Integer maxDivideRecipients = 5;

    /**
     * 仅供本地协议测试使用。生产环境必须保持 false，让官方 SDK 校验易宝响应签名。
     */
    @JsonIgnore
    private boolean skipResponseVerifySign;

    @Override
    public void validate(Validator validator) {
        ValidationUtils.validate(validator, this);
        CertTypeEnum certType = getSigningCertTypeEnum();
        getAppKey();
        if (org.springframework.util.StringUtils.hasText(getPublicKey())
                == org.springframework.util.StringUtils.hasText(getPublicCertificate())) {
            throw new IllegalArgumentException("易宝公钥和证书必须配置且只能配置一个");
        }
        if (!org.springframework.util.StringUtils.hasText(privateKey)
                && !org.springframework.util.StringUtils.hasText(resolvePrivateKeyFromEnvironment())) {
            throw new IllegalArgumentException("易宝私钥未配置或 privateKeyRef 无法解析，请配置 env://、sys:// 引用或注入运行时密钥");
        }
        if (certType == CertTypeEnum.SM2
                && !org.springframework.util.StringUtils.hasText(platformCertSerialNo)
                && !org.springframework.util.StringUtils.hasText(getPublicCertificate())) {
            throw new IllegalArgumentException("SM2 必须配置平台证书或 platformCertSerialNo");
        }
        validateFirstPhaseFundProcess();
    }

    /** 首期仅支持清算成功后的异步分账，不能先实时分账后再次 apply。 */
    private void validateFirstPhaseFundProcess() {
        if (!FUND_PROCESS_TYPE_DELAY_SETTLE.equals(fundProcessType)) {
            throw new IllegalArgumentException("易宝首期资金处理方式必须是 DELAY_SETTLE");
        }
    }

    @JsonIgnore
    public String getAppKey() {
        String value = resolveRuntimeMaterial(appKey, appKeyRef, "AppKey");
        if (!org.springframework.util.StringUtils.hasText(value)) {
            throw new IllegalStateException("易宝 AppKey 未配置，请通过 appKeyRef 注入运行时配置");
        }
        return value;
    }

    @JsonIgnore
    public String getPublicKey() {
        return resolveRuntimeMaterial(publicKey, publicKeyRef, "平台公钥");
    }

    @JsonIgnore
    public String getPublicCertificate() {
        return resolveRuntimeMaterial(publicCertificate, publicCertificateRef, "平台证书");
    }

    private static String resolveRuntimeMaterial(String inMemory, String reference, String label) {
        if (org.springframework.util.StringUtils.hasText(inMemory)) return inMemory;
        if (!org.springframework.util.StringUtils.hasText(reference)) return null;
        String value = resolvePrivateKeyReference(reference.trim());
        if (!org.springframework.util.StringUtils.hasText(value)) {
            throw new IllegalStateException("易宝" + label + "引用无法解析，请检查运行时密钥配置");
        }
        return value;
    }

    /** 客户端验签使用的密钥材料，避免业务层直接判断公钥还是证书。 */
    @JsonIgnore
    public String getVerificationKey() {
        String key = getPublicKey();
        return org.springframework.util.StringUtils.hasText(key) ? key : getPublicCertificate();
    }

    /**
     * 读取运行时私钥。privateKey 仅用于兼容内存配置和测试，生产应使用环境变量或密钥管理系统。
     */
    @JsonIgnore
    public String getRuntimePrivateKey() {
        if (org.springframework.util.StringUtils.hasText(privateKey)) {
            return privateKey;
        }
        String resolved = resolvePrivateKeyFromEnvironment();
        if (org.springframework.util.StringUtils.hasText(resolved)) {
            return resolved;
        }
        throw new IllegalStateException("易宝私钥未配置，请通过密钥管理系统注入 privateKeyRef=" + privateKeyRef);
    }

    private String resolvePrivateKeyFromEnvironment() {
        String reference = privateKeyRef == null ? null : privateKeyRef.trim();
        if (org.springframework.util.StringUtils.hasText(reference)) {
            String referencedValue = resolvePrivateKeyReference(reference);
            if (org.springframework.util.StringUtils.hasText(referencedValue)) {
                return referencedValue;
            }
            // A prefixed reference is explicit. Do not silently fall through to a derived
            // variable and make a typo appear to be a valid configuration.
            if (reference.startsWith("env://") || reference.startsWith("sys://")) {
                return null;
            }
        }
        String runtimeAppKey = getAppKey();
        String envKey = "YEEPAY_PRIVATE_KEY_" + runtimeAppKey
                .replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
        String fromEnv = System.getenv(envKey);
        if (org.springframework.util.StringUtils.hasText(fromEnv)) {
            return fromEnv;
        }
        String fromProperty = System.getProperty("yeepay.private-key." + runtimeAppKey);
        if (org.springframework.util.StringUtils.hasText(fromProperty)) {
            return fromProperty;
        }
        return null;
    }

    /**
     * Resolve a private-key reference without ever treating the reference itself as key material.
     * The unprefixed form is kept for deployments whose secret manager exports a conventional
     * environment variable or JVM system property name.
     */
    private static String resolvePrivateKeyReference(String reference) {
        if (reference.startsWith("env://")) {
            return readEnvironmentVariable(reference.substring("env://".length()));
        }
        if (reference.startsWith("sys://")) {
            return readSystemProperty(reference.substring("sys://".length()));
        }
        String fromEnv = readEnvironmentVariable(reference);
        return org.springframework.util.StringUtils.hasText(fromEnv) ? fromEnv : readSystemProperty(reference);
    }

    private static String readEnvironmentVariable(String name) {
        return org.springframework.util.StringUtils.hasText(name) ? System.getenv(name) : null;
    }

    private static String readSystemProperty(String name) {
        return org.springframework.util.StringUtils.hasText(name) ? System.getProperty(name) : null;
    }

    public String getEffectivePlatformCertSerialNo() {
        if (org.springframework.util.StringUtils.hasText(platformCertSerialNo)) {
            return platformCertSerialNo;
        }
        return getSigningCertTypeEnum() == CertTypeEnum.RSA2048
                ? com.yeepay.yop.sdk.YopConstants.YOP_RSA_PLATFORM_CERT_DEFAULT_SERIAL_NO : null;
    }

    @JsonIgnore
    public CertTypeEnum getSigningCertTypeEnum() {
        CertTypeEnum certType = CertTypeEnum.parse(signingCertType);
        if (certType == null || certType == CertTypeEnum.SM4) {
            throw new IllegalStateException("易宝请求签名证书类型必须是 RSA2048 或 SM2");
        }
        return certType;
    }

    @JsonIgnore
    public String getSecurityReq() {
        return getSigningCertTypeEnum() == CertTypeEnum.SM2 ? "YOP-SM2-SM3" : "YOP-RSA2048-SHA256";
    }

    @JsonIgnore
    public String getRuntimeSm4CallbackKey() {
        if (org.springframework.util.StringUtils.hasText(sm4CallbackKey)) {
            return sm4CallbackKey;
        }
        return System.getenv("YEEPAY_SM4_CALLBACK_KEY_" + getAppKey()
                .replaceAll("[^A-Za-z0-9]", "_").toUpperCase());
    }

    /** 配置文档中的证书命名兼容别名。 */
    @JsonIgnore
    public String getCertificate() {
        return getPublicCertificate();
    }

    public void setCertificate(String certificate) {
        this.publicCertificate = certificate;
    }

    /** 方案文档中的配置名称兼容别名。 */
    public String getPlatformCollectionMerchantNo() {
        return collectionMerchantNo;
    }

    public void setPlatformCollectionMerchantNo(String merchantNo) {
        this.collectionMerchantNo = merchantNo;
    }

    public String getPlatformAppId() {
        return appId;
    }

    public void setPlatformAppId(String appId) {
        this.appId = appId;
    }

    /**
     * Lombok's generated representation is used by a number of configuration and client logs.
     * Keep credentials out of those logs even when a caller logs the whole configuration object.
     */
    @Override
    public String toString() {
        return "YeepayPayClientConfig{" +
                "appKey='" + mask(appKey) + '\'' +
                ", appKeyRef='" + mask(appKeyRef) + '\'' +
                ", privateKeyRef='" + mask(privateKeyRef) + '\'' +
                ", publicKeyConfigured=" + (org.springframework.util.StringUtils.hasText(publicKey)
                        || org.springframework.util.StringUtils.hasText(publicKeyRef)) +
                ", publicCertificateConfigured=" + (org.springframework.util.StringUtils.hasText(publicCertificate)
                        || org.springframework.util.StringUtils.hasText(publicCertificateRef)) +
                ", serverUrl='" + serverUrl + '\'' +
                ", yosServerUrl='" + yosServerUrl + '\'' +
                ", appId='" + mask(appId) + '\'' +
                ", collectionMerchantNo='" + mask(collectionMerchantNo) + '\'' +
                ", parentMerchantNo='" + mask(parentMerchantNo) + '\'' +
                ", sdkProvider='" + sdkProvider + '\'' +
                ", sdkEnv='" + sdkEnv + '\'' +
                ", signingCertType='" + signingCertType + '\'' +
                ", businessRole='" + businessRole + '\'' +
                ", fundProcessType='" + fundProcessType + '\'' +
                ", timeoutSeconds=" + timeoutSeconds +
                ", connectTimeoutSeconds=" + connectTimeoutSeconds +
                ", maxRetries=" + maxRetries +
                ", retryBackoffSeconds=" + retryBackoffSeconds +
                ", rateLimitPerSecond=" + rateLimitPerSecond +
                ", maxDivideRecipients=" + maxDivideRecipients +
                '}';
    }

    private static String mask(String value) {
        if (!org.springframework.util.StringUtils.hasText(value)) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
