package cn.iocoder.yudao.module.trade.service.logistics;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Component
public class PrintBridgeConfigFileGenerator {

    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generate(String endpointUrl, String allowedOrigin, String token,
                           String deviceId, String deviceName) {
        ObjectNode payload = JsonUtils.getObjectMapper().createObjectNode();
        payload.put("format", "printbridge-config").put("version", 1);
        ObjectNode config = payload.putObject("config");
        config.putObject("service").put("port", 17_890);
        config.putObject("security").putPOJO("allowed_origins", List.of(allowedOrigin));
        config.putObject("remote").put("enabled", true).put("endpoint_url", endpointUrl)
                .put("bearer_token", token).put("device_id", deviceId).put("device_name", deviceName)
                .put("poll_interval_seconds", 10)
                .put("max_report_retries", 10);
        byte[] salt = randomBytes(16);
        byte[] nonce = randomBytes(12);
        return encrypt(JsonUtils.toJsonString(payload), "", salt, nonce);
    }

    String decryptForTest(String encryptedJson, String password) {
        JsonNode envelope = JsonUtils.parseTree(encryptedJson);
        JsonNode crypto = envelope.path("crypto");
        byte[] salt = Base64.getDecoder().decode(crypto.path("salt").asText());
        byte[] nonce = Base64.getDecoder().decode(crypto.path("nonce").asText());
        byte[] ciphertext = Base64.getDecoder().decode(envelope.path("payload").asText());
        byte[] key = deriveKey(password, salt);
        GCMBlockCipher cipher = new GCMBlockCipher(AESEngine.newInstance());
        cipher.init(false, new AEADParameters(new KeyParameter(key), 128, nonce));
        byte[] plaintext = new byte[cipher.getOutputSize(ciphertext.length)];
        int length = cipher.processBytes(ciphertext, 0, ciphertext.length, plaintext, 0);
        try {
            length += cipher.doFinal(plaintext, length);
        } catch (InvalidCipherTextException exception) {
            throw new IllegalArgumentException("PrintBridge 配置文件无法解密", exception);
        }
        return new String(plaintext, 0, length, StandardCharsets.UTF_8);
    }

    private String encrypt(String plaintext, String password, byte[] salt, byte[] nonce) {
        byte[] key = deriveKey(password, salt);
        GCMBlockCipher cipher = new GCMBlockCipher(AESEngine.newInstance());
        cipher.init(true, new AEADParameters(new KeyParameter(key), 128, nonce));
        byte[] input = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = new byte[cipher.getOutputSize(input.length)];
        int length = cipher.processBytes(input, 0, input.length, ciphertext, 0);
        try {
            length += cipher.doFinal(ciphertext, length);
        } catch (InvalidCipherTextException exception) {
            throw new IllegalStateException("PrintBridge 配置文件加密失败", exception);
        }
        ObjectNode envelope = JsonUtils.getObjectMapper().createObjectNode();
        envelope.put("format", "printbridge-config-encrypted").put("version", 1);
        envelope.putObject("crypto").put("kdf", "argon2id13").put("memory_kib", MEMORY_KIB)
                .put("iterations", ITERATIONS).put("parallelism", PARALLELISM)
                .put("cipher", "aes-256-gcm").put("tag_bytes", 16)
                .put("salt", Base64.getEncoder().encodeToString(salt))
                .put("nonce", Base64.getEncoder().encodeToString(nonce));
        envelope.put("payload", Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(ciphertext, length)));
        java.util.Arrays.fill(key, (byte) 0);
        return JsonUtils.toJsonString(envelope);
    }

    private byte[] deriveKey(String password, byte[] salt) {
        Argon2Parameters parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(MEMORY_KIB)
                .withIterations(ITERATIONS).withParallelism(PARALLELISM).withSalt(salt).build();
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(parameters);
        byte[] key = new byte[32];
        generator.generateBytes(password.getBytes(StandardCharsets.UTF_8), key);
        return key;
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }
}
