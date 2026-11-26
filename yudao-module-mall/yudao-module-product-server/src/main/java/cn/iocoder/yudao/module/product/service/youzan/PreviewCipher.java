package cn.iocoder.yudao.module.product.service.youzan;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

final class PreviewCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    PreviewCipher(String encodedKey) {
        byte[] bytes = Base64.getDecoder().decode(encodedKey);
        if (bytes.length != 32) throw new IllegalStateException("商品导入密钥未配置");
        key = new SecretKeySpec(bytes, "AES");
    }

    byte[] seal(String binding, byte[] content) throws GeneralSecurityException {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        byte[] sealed = cipher(Cipher.ENCRYPT_MODE, binding, iv).doFinal(content);
        return ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
    }

    byte[] open(String binding, byte[] envelope) throws GeneralSecurityException {
        if (envelope.length < 28) throw new GeneralSecurityException("预览无效");
        byte[] iv = new byte[12];
        ByteBuffer.wrap(envelope).get(iv);
        return cipher(Cipher.DECRYPT_MODE, binding, iv).doFinal(envelope, 12, envelope.length - 12);
    }

    private Cipher cipher(int mode, String binding, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
