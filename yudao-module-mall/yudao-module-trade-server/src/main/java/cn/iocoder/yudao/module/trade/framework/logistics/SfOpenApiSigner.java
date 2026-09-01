package cn.iocoder.yudao.module.trade.framework.logistics;

import lombok.SneakyThrows;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/** 顺丰开放平台 msgDigest 签名。 */
public final class SfOpenApiSigner {

    private SfOpenApiSigner() {
    }

    @SneakyThrows
    public static String sign(String msgData, long timestamp, String checkWord) {
        String source = URLEncoder.encode(msgData + timestamp + checkWord, StandardCharsets.UTF_8);
        byte[] digest = MessageDigest.getInstance("MD5").digest(source.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

}
