package cn.iocoder.yudao.module.trade.service.logistics;

import cn.hutool.core.codec.Base64;
import cn.hutool.crypto.SecureUtil;

import java.security.SecureRandom;

public final class LogisticsTokenUtils {

    private LogisticsTokenUtils() {
    }

    public static String generate() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeUrlSafe(bytes);
    }

    public static String hash(String token) {
        return SecureUtil.sha256(token);
    }
}
