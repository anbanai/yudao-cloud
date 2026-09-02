package cn.iocoder.yudao.module.trade.service.logistics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrintBridgeConfigFileGeneratorTest {

    @Test
    void decryptsOfficialCrossLanguageTestVector() {
        String encrypted = """
                {"format":"printbridge-config-encrypted","version":1,"crypto":{"kdf":"argon2id13","memory_kib":19456,"iterations":2,"parallelism":1,"cipher":"aes-256-gcm","tag_bytes":16,"salt":"AAECAwQFBgcICQoLDA0ODw==","nonce":"EBESExQVFhcYGRob"},"payload":"OTX3vkYug76bv335qmWdp85pbgu85QfwarlnqhxGoV0U+4sRez0dlwWy+5eIe597KLRqdHg7XJVbjLds/mXROcLHLhTJrJJ+DWpB2Xc6BX2sKii+bziOsb8akhUwxqo="}
                """;

        String plaintext = new PrintBridgeConfigFileGenerator().decryptForTest(encrypted, "test-password");

        assertThat(plaintext).contains("\"format\":\"printbridge-config\"")
                .contains("\"port\":17890");
    }

    @Test
    void generatedFileContainsEncryptedRemoteConfiguration() {
        PrintBridgeConfigFileGenerator generator = new PrintBridgeConfigFileGenerator();

        String encrypted = generator.generate("https://api.example.com/internal-api/trade/logistics/printbridge/tasks",
                "https://admin.example.com", "device-secret", "device-001", "仓库工作站");
        String plaintext = generator.decryptForTest(encrypted, "");

        assertThat(plaintext).contains("\"endpoint_url\":\"https://api.example.com/internal-api/trade/logistics/printbridge/tasks\"")
                .contains("\"bearer_token\":\"device-secret\"")
                .contains("\"device_id\":\"device-001\"")
                .contains("\"device_name\":\"仓库工作站\"")
                .contains("\"allowed_origins\":[\"https://admin.example.com\"]");
        assertThat(encrypted).doesNotContain("device-secret");
    }
}
