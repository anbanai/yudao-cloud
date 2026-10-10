package cn.iocoder.yudao.module.pay.framework.pay.core.client.impl.yeepay;

import com.yeepay.yop.sdk.auth.credentials.YopCredentials;
import com.yeepay.yop.sdk.auth.credentials.YopPKICredentials;
import com.yeepay.yop.sdk.auth.credentials.CredentialsItem;
import com.yeepay.yop.sdk.auth.credentials.provider.YopCredentialsProvider;
import com.yeepay.yop.sdk.config.enums.CertStoreType;
import com.yeepay.yop.sdk.config.provider.file.YopCertConfig;
import com.yeepay.yop.sdk.security.CertTypeEnum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime credentials adapter for the official YOP SDK. Secrets remain in memory only. */
final class YeepayCredentialsProvider implements YopCredentialsProvider {

    static final YeepayCredentialsProvider INSTANCE = new YeepayCredentialsProvider();

    private final Map<String, YopCredentials<?>> credentials = new ConcurrentHashMap<>();
    private final Map<String, List<YopCertConfig>> sm4Keys = new ConcurrentHashMap<>();
    private final Map<String, String> defaultAppKeys = new ConcurrentHashMap<>();

    private YeepayCredentialsProvider() {
    }

    void register(String provider, String env, String appKey, YopCredentials<?> value, String sm4Key) {
        if (provider == null || env == null || appKey == null || value == null) {
            throw new IllegalArgumentException("易宝 SDK 凭证参数不能为空");
        }
        String scope = scope(provider, env, appKey);
        credentials.put(scope + ":" + ((CredentialsItem) value.getCredential()).getCertType().getValue(), value);
        defaultAppKeys.put(provider + ":" + env, appKey);
        if (sm4Key != null && !sm4Key.isBlank()) {
            YopCertConfig key = new YopCertConfig();
            key.setCertType(CertTypeEnum.SM4);
            key.setStoreType(CertStoreType.STRING);
            key.setValue(sm4Key);
            sm4Keys.put(scope, List.of(key));
        } else {
            sm4Keys.remove(scope);
        }
    }

    @Override
    public YopCredentials<?> getCredentials(String appKey, String credentialType) {
        return findAnyEnvironment(appKey, credentialType);
    }

    @Override
    public YopCredentials<?> getCredentials(String provider, String env, String appKey, String credentialType) {
        YopCredentials<?> value = credentials.get(scope(provider, env, appKey) + ":" + credentialType);
        if (value == null) {
            throw new IllegalStateException("易宝 SDK 客户端凭证未配置，credentialType=" + credentialType);
        }
        return value;
    }

    @Override
    public List<CertTypeEnum> getSupportCertTypes(String appKey) {
        List<CertTypeEnum> result = new ArrayList<>();
        credentials.forEach((key, value) -> {
            if (key.contains(":" + appKey + ":")) {
                result.add(((CredentialsItem) value.getCredential()).getCertType());
            }
        });
        return result.isEmpty() ? Collections.emptyList() : List.copyOf(result);
    }

    @Override
    public List<CertTypeEnum> getSupportCertTypes(String provider, String env, String appKey) {
        List<CertTypeEnum> result = new ArrayList<>();
        String prefix = scope(provider, env, appKey) + ":";
        credentials.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                result.add(((CredentialsItem) value.getCredential()).getCertType());
            }
        });
        return result.isEmpty() ? Collections.emptyList() : List.copyOf(result);
    }

    @Override
    public String getDefaultAppKey() {
        return defaultAppKeys.values().stream().findFirst().orElse("default");
    }

    @Override
    public String getDefaultAppKey(String provider, String env) {
        return defaultAppKeys.getOrDefault(provider + ":" + env, "default");
    }

    @Override
    @Deprecated
    public List<YopCertConfig> getIsvEncryptKey(String appKey) {
        return sm4Keys.entrySet().stream()
                .filter(entry -> entry.getKey().contains(":" + appKey))
                .map(Map.Entry::getValue).findFirst().orElse(Collections.emptyList());
    }

    @Override
    @Deprecated
    public List<YopCertConfig> getIsvEncryptKey(String provider, String env, String appKey) {
        return sm4Keys.getOrDefault(scope(provider, env, appKey), Collections.emptyList());
    }

    private YopCredentials<?> findAnyEnvironment(String appKey, String credentialType) {
        String suffix = ":" + appKey + ":" + credentialType;
        return credentials.entrySet().stream().filter(entry -> entry.getKey().endsWith(suffix))
                .map(Map.Entry::getValue).findFirst()
                .orElseThrow(() -> new IllegalStateException("易宝 SDK 客户端凭证未配置，credentialType=" + credentialType));
    }

    private static String scope(String provider, String env, String appKey) {
        return provider + ":" + env + ":" + appKey;
    }
}
