package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay;

import com.yeepay.yop.sdk.YopConstants;
import com.yeepay.yop.sdk.auth.credentials.PKICredentialsItem;
import com.yeepay.yop.sdk.auth.credentials.YopPlatformCredentials;
import com.yeepay.yop.sdk.auth.credentials.YopPlatformCredentialsHolder;
import com.yeepay.yop.sdk.auth.credentials.provider.YopPlatformCredentialsProvider;
import com.yeepay.yop.sdk.exception.YopClientException;
import com.yeepay.yop.sdk.security.CertTypeEnum;
import com.yeepay.yop.sdk.utils.X509CertUtils;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime platform-certificate registry used by the official YOP SDK. */
final class YeepayPlatformCredentialsProvider implements YopPlatformCredentialsProvider {

    static final YeepayPlatformCredentialsProvider INSTANCE = new YeepayPlatformCredentialsProvider();

    private final Map<String, YopPlatformCredentials> credentials = new ConcurrentHashMap<>();
    private final Map<String, YopPlatformCredentials> latestCredentials = new ConcurrentHashMap<>();

    private YeepayPlatformCredentialsProvider() {
    }

    void register(String provider, String env, String appKey, String serialNo,
                  PublicKey publicKey, CertTypeEnum certType) {
        if (appKey == null || appKey.isBlank() || publicKey == null || certType == null) {
            throw new IllegalArgumentException("易宝 appKey、平台公钥和证书类型不能为空");
        }
        String actualSerialNo = normalizeSerialNo(serialNo, certType);
        YopPlatformCredentials value = new YopPlatformCredentialsHolder().withAppKey(appKey)
                .withSerialNo(actualSerialNo).withCredentials(new PKICredentialsItem(publicKey, certType));
        credentials.put(key(provider, env, appKey, actualSerialNo), value);
        latestCredentials.put(latestKey(provider, env, appKey, certType), value);
    }

    @Override
    public YopPlatformCredentials getCredentials(String appKey, String serialNo) {
        return getCredentials(appKey, serialNo, null);
    }

    @Override
    public YopPlatformCredentials getCredentials(String appKey, String serialNo, String serverRoot) {
        return lookup(null, null, appKey, serialNo);
    }

    @Override
    public YopPlatformCredentials getCredentials(String provider, String env, String appKey,
                                                  String serialNo, String serverRoot) {
        return lookup(provider, env, appKey, serialNo);
    }

    @Override
    public YopPlatformCredentials getLatestCredentials(String appKey, String credentialType) {
        return getLatestCredentials(appKey, credentialType, null);
    }

    @Override
    public YopPlatformCredentials getLatestCredentials(String appKey, String credentialType, String serverRoot) {
        return findLatest(null, null, appKey, credentialType);
    }

    @Override
    public YopPlatformCredentials getLatestCredentials(String provider, String env, String appKey,
                                                       String credentialType, String serverRoot) {
        return findLatest(provider, env, appKey, credentialType);
    }

    @Override
    public YopPlatformCredentials storeCredentials(String appKey, String credentialType,
                                                    X509Certificate certificate) {
        return storeCredentials(YopConstants.YOP_DEFAULT_PROVIDER, YopConstants.YOP_DEFAULT_ENV,
                appKey, credentialType, certificate);
    }

    @Override
    public YopPlatformCredentials storeCredentials(String provider, String env, String appKey,
                                                    String credentialType, X509Certificate certificate) {
        if (certificate == null) {
            throw new IllegalArgumentException("易宝平台证书不能为空");
        }
        CertTypeEnum certType = CertTypeEnum.parse(credentialType);
        if (certType == null) {
            throw new IllegalArgumentException("不支持的易宝平台证书类型: " + credentialType);
        }
        String serialNo = X509CertUtils.parseToHex(certificate.getSerialNumber().toString());
        register(provider, env, appKey, serialNo, certificate.getPublicKey(), certType);
        return getCredentials(provider, env, appKey, serialNo, null);
    }

    private YopPlatformCredentials lookup(String provider, String env, String appKey, String serialNo) {
        YopPlatformCredentials result = credentials.get(key(provider, env, appKey, serialNo));
        if (result == null) {
            throw new YopClientException("易宝平台证书未配置，serialNo=" + serialNo);
        }
        return result;
    }

    private YopPlatformCredentials findLatest(String provider, String env, String appKey, String credentialType) {
        CertTypeEnum certType = CertTypeEnum.parse(credentialType);
        if (certType == null) {
            throw new YopClientException("不支持的易宝平台证书类型: " + credentialType);
        }
        YopPlatformCredentials result = latestCredentials.get(latestKey(provider, env, appKey, certType));
        if (result == null) {
            // RSA 数字信封的旧协议使用 SDK 约定序列号 rsa，不携带证书序列号。
            result = credentials.get(key(provider, env, appKey,
                    YopConstants.YOP_RSA_PLATFORM_CERT_DEFAULT_SERIAL_NO));
        }
        if (result == null) {
            throw new YopClientException("易宝平台证书未配置，credentialType=" + credentialType);
        }
        return result;
    }

    private static String normalizeSerialNo(String serialNo, CertTypeEnum certType) {
        if (serialNo == null || serialNo.isBlank()) {
            return certType == CertTypeEnum.RSA2048
                    ? YopConstants.YOP_RSA_PLATFORM_CERT_DEFAULT_SERIAL_NO : serialNo;
        }
        return certType == CertTypeEnum.SM2 ? X509CertUtils.parseToHex(serialNo) : serialNo;
    }

    private static String key(String provider, String env, String appKey, String serialNo) {
        return scope(provider, env, appKey) + ":" + serialNo;
    }

    private static String latestKey(String provider, String env, String appKey, CertTypeEnum certType) {
        return scope(provider, env, appKey) + ":" + certType.getValue();
    }

    private static String scope(String provider, String env, String appKey) {
        return (provider == null ? YopConstants.YOP_DEFAULT_PROVIDER : provider) + ":"
                + (env == null ? YopConstants.YOP_DEFAULT_ENV : env) + ":" + appKey;
    }
}
