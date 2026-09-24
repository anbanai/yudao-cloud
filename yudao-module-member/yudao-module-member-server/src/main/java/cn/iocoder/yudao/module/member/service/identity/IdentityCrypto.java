package cn.iocoder.yudao.module.member.service.identity;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class IdentityCrypto {
    private final byte[] key;
    public IdentityCrypto(String encodedKey) {
        key = Base64.getDecoder().decode(encodedKey);
        if (key.length != 32) { throw new IllegalArgumentException("Identity encryption key must be 32 bytes"); }
    }
    public static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Digest unavailable"); }
    }
    public static String canonical(String path, String timestamp, String nonce, String body) {
        return "POST\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + sha256(body);
    }
    public static String sign(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("Signature unavailable"); }
    }
    public String encrypt(String value) {
        if (value == null) { return null; }
        try {
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] result = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(result);
        } catch (Exception e) { throw IdentityPolicy.unavailable(); }
    }
    public String decrypt(String value) {
        if (value == null) { return null; }
        try {
            String[] parts = value.split("\\.", -1);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw IdentityPolicy.unavailable(); }
    }
}
