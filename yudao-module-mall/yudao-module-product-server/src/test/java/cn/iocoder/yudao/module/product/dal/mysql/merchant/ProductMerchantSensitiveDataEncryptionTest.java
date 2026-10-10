package cn.iocoder.yudao.module.product.dal.mysql.merchant;

import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantDO;
import cn.iocoder.yudao.module.product.dal.dataobject.merchant.ProductMerchantQualificationDO;
import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.module.product.framework.merchant.core.MerchantSensitiveDataBackfill;
import cn.iocoder.yudao.module.product.framework.merchant.core.MerchantSensitiveTypeHandler;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestPropertySource(properties = "yudao.product.merchant.encryption.allow-marked-plaintext=true")
class ProductMerchantSensitiveDataEncryptionTest extends BaseDbUnitTest {

    @Resource
    private ProductMerchantMapper merchantMapper;
    @Resource
    private ProductMerchantQualificationMapper qualificationMapper;
    @Resource
    private DataSource dataSource;

    @Test
    void testSensitiveMerchantDataIsEncryptedAtRestAndReadableThroughMapper() {
        ProductMerchantDO merchant = new ProductMerchantDO().setName("测试商家")
                .setCreditCode("91310000MA1FL8X123").setLegalPerson("张三")
                .setContactName("李四").setContactMobile("13800138000").setStatus(10);
        merchantMapper.insert(merchant);

        assertNotEquals("91310000MA1FL8X123", jdbc().queryForObject(
                "SELECT credit_code FROM product_merchant WHERE id = ?", String.class, merchant.getId()));
        assertNotEquals("13800138000", jdbc().queryForObject(
                "SELECT contact_mobile FROM product_merchant WHERE id = ?", String.class, merchant.getId()));
        assertTrue(jdbc().queryForObject("SELECT credit_code FROM product_merchant WHERE id = ?",
                String.class, merchant.getId()).startsWith("AES:v1:"));
        ProductMerchantDO loadedMerchant = merchantMapper.selectById(merchant.getId());
        assertEquals("91310000MA1FL8X123", loadedMerchant.getCreditCode());
        assertEquals("13800138000", loadedMerchant.getContactMobile());

        ProductMerchantQualificationDO qualification = new ProductMerchantQualificationDO()
                .setMerchantId(merchant.getId()).setType("BUSINESS_LICENSE")
                .setCertificateNo("91310000MA1FL8X123")
                .setFileUrl("https://secure.example.test/license?token=secret").setStatus(10);
        qualificationMapper.insert(qualification);

        assertNotEquals("91310000MA1FL8X123", jdbc().queryForObject(
                "SELECT certificate_no FROM product_merchant_qualification WHERE id = ?",
                String.class, qualification.getId()));
        assertNotEquals("https://secure.example.test/license?token=secret", jdbc().queryForObject(
                "SELECT file_url FROM product_merchant_qualification WHERE id = ?",
                String.class, qualification.getId()));
        ProductMerchantQualificationDO loadedQualification = qualificationMapper.selectById(qualification.getId());
        assertEquals("91310000MA1FL8X123", loadedQualification.getCertificateNo());
        assertEquals("https://secure.example.test/license?token=secret", loadedQualification.getFileUrl());
    }

    @Test
    void testExplicitlyMarkedLegacyPlaintextCanBeReadDuringMigration() {
        ProductMerchantDO merchant = new ProductMerchantDO().setName("迁移商家").setStatus(20);
        merchantMapper.insert(merchant);
        jdbc().update("UPDATE product_merchant SET credit_code = ?, legal_person = ? WHERE id = ?",
                "PLAIN:v0:91310000MA1FL8X123", "PLAIN:v0:张三", merchant.getId());

        ProductMerchantDO loaded = merchantMapper.selectById(merchant.getId());

        assertEquals("91310000MA1FL8X123", loaded.getCreditCode());
        assertEquals("张三", loaded.getLegalPerson());
    }

    @Test
    void testDamagedCiphertextIsNeverTreatedAsPlaintext() {
        ProductMerchantDO merchant = new ProductMerchantDO().setName("密文损坏商家").setStatus(20);
        merchantMapper.insert(merchant);
        jdbc().update("UPDATE product_merchant SET credit_code = ? WHERE id = ?",
                "AES:v1:broken-ciphertext", merchant.getId());

        assertThrows(RuntimeException.class, () -> merchantMapper.selectById(merchant.getId()));
        jdbc().update("UPDATE product_merchant SET credit_code = ? WHERE id = ?",
                "91310000MA1FL8X123", merchant.getId());
        assertThrows(RuntimeException.class, () -> merchantMapper.selectById(merchant.getId()));
    }

    @Test
    void testPreviouslyEncryptedUntaggedAesRemainsReadable() {
        ProductMerchantDO merchant = new ProductMerchantDO().setName("已加密部署").setStatus(20);
        merchantMapper.insert(merchant);
        jdbc().update("UPDATE product_merchant SET credit_code = ? WHERE id = ?",
                EncryptTypeHandler.encrypt("91310000MA1FL8X123"), merchant.getId());

        assertEquals("91310000MA1FL8X123", merchantMapper.selectById(merchant.getId()).getCreditCode());
    }

    @Test
    void testMarkedPlaintextBackfillIsIdempotentAndPreservesExistingCiphertext() {
        ProductMerchantDO merchant = new ProductMerchantDO().setName("待回填商家")
                .setLegalPerson("张三").setStatus(20);
        merchantMapper.insert(merchant);
        String existingCiphertext = jdbc().queryForObject("SELECT legal_person FROM product_merchant WHERE id = ?",
                String.class, merchant.getId());
        jdbc().update("UPDATE product_merchant SET credit_code = ? WHERE id = ?",
                "PLAIN:v0:91310000MA1FL8X123", merchant.getId());
        jdbc().update("INSERT INTO product_merchant_encryption_migration(migration_id,state) VALUES (?, 'MARKED')",
                MerchantSensitiveDataBackfill.MIGRATION_ID);
        MerchantSensitiveDataBackfill backfill = new MerchantSensitiveDataBackfill();
        ReflectionTestUtils.setField(backfill, "jdbcTemplate", jdbc());
        ReflectionTestUtils.setField(backfill, "transactionTemplate",
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));

        backfill.backfill();
        String encrypted = jdbc().queryForObject("SELECT credit_code FROM product_merchant WHERE id = ?",
                String.class, merchant.getId());
        backfill.backfill();

        assertTrue(encrypted.startsWith(MerchantSensitiveTypeHandler.CIPHERTEXT_PREFIX));
        assertEquals("91310000MA1FL8X123", merchantMapper.selectById(merchant.getId()).getCreditCode());
        assertEquals(encrypted, jdbc().queryForObject("SELECT credit_code FROM product_merchant WHERE id = ?",
                String.class, merchant.getId()));
        assertEquals(existingCiphertext, jdbc().queryForObject("SELECT legal_person FROM product_merchant WHERE id = ?",
                String.class, merchant.getId()));
        assertEquals("ENCRYPTED", jdbc().queryForObject("SELECT state FROM product_merchant_encryption_migration"
                + " WHERE migration_id = ?", String.class, MerchantSensitiveDataBackfill.MIGRATION_ID));
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }
}
