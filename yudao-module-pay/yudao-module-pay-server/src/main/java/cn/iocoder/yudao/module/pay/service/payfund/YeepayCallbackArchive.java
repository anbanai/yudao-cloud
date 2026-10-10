package cn.iocoder.yudao.module.pay.service.payfund;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** 使用独立的外部密钥加密已验签的回调原文，密钥编号支持保留旧密钥进行轮换。 */
@Component
public class YeepayCallbackArchive {

    private static final String PREFIX = "yudao.pay.yeepay.callback-archive.";
    private static final int MAX_ORIGINAL_BYTES = 8 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final Environment environment;

    public YeepayCallbackArchive(Environment environment) {
        this.environment = environment;
    }

    public String encrypt(String plaintext, String binding) {
        requireBinding(binding);
        if (plaintext == null) {
            throw new IllegalArgumentException("易宝回调原文不能为空");
        }
        byte[] original = plaintext.getBytes(StandardCharsets.UTF_8);
        if (original.length > MAX_ORIGINAL_BYTES) {
            throw new IllegalArgumentException("易宝回调原文超过存储上限");
        }
        String keyId = environment.getProperty(PREFIX + "active-key-id", "v1");
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, keyId, nonce, binding);
            return "YPA:1:" + keyId + ":" + Base64.getEncoder().encodeToString(nonce) + ":"
                    + Base64.getEncoder().encodeToString(cipher.doFinal(original));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("易宝回调原文加密失败");
        } finally {
            Arrays.fill(original, (byte) 0);
        }
    }

    /** 仅供受控审计工具使用，业务查询接口不得返回解密原文。 */
    public String decrypt(String ciphertext, String binding) {
        requireBinding(binding);
        try {
            if (ciphertext == null || ciphertext.length() > (MAX_ORIGINAL_BYTES + 16) * 4L / 3 + 128) {
                throw new IllegalArgumentException("易宝回调密文格式无效");
            }
            String[] parts = ciphertext.split(":", -1);
            if (parts.length != 5 || !"YPA".equals(parts[0]) || !"1".equals(parts[1])) {
                throw new IllegalArgumentException("易宝回调密文格式无效");
            }
            byte[] nonce = Base64.getDecoder().decode(parts[3]);
            byte[] encrypted = Base64.getDecoder().decode(parts[4]);
            if (nonce.length != 12 || encrypted.length < 16 || encrypted.length > MAX_ORIGINAL_BYTES + 16) {
                throw new IllegalArgumentException("易宝回调密文格式无效");
            }
            byte[] original = cipher(Cipher.DECRYPT_MODE, parts[2], nonce, binding).doFinal(encrypted);
            try {
                return new String(original, StandardCharsets.UTF_8);
            } finally {
                Arrays.fill(original, (byte) 0);
            }
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("易宝回调原文校验或解密失败");
        }
    }

    private Cipher cipher(int mode, String keyId, byte[] nonce, String binding)
            throws GeneralSecurityException {
        if (keyId == null || !keyId.matches("[A-Za-z0-9_-]{1,32}")) {
            throw new IllegalStateException("易宝回调归档密钥编号无效");
        }
        String configuredKey = environment.getProperty(PREFIX + "keys." + keyId);
        byte[] key;
        try {
            key = configuredKey == null ? new byte[0] : Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("易宝回调归档密钥配置无效");
        }
        try {
            if (key.length != 32) {
                throw new IllegalStateException("易宝回调归档需要外部配置的 AES256 密钥");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            return cipher;
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    private static void requireBinding(String binding) {
        if (binding == null || binding.isBlank()) {
            throw new IllegalArgumentException("易宝回调归档缺少租户和事件绑定");
        }
    }
}
