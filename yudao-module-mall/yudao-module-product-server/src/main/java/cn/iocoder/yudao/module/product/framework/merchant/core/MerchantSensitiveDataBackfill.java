package cn.iocoder.yudao.module.product.framework.merchant.core;

import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/** 将升级脚本明确标记的旧明文分批加密；可中断后重启，不推断任何未标记字段。 */
@Component
@ConditionalOnProperty(name = "yudao.product.merchant.encryption.backfill-enabled", havingValue = "true")
public class MerchantSensitiveDataBackfill implements ApplicationRunner {

    public static final String MIGRATION_ID = "merchant-plaintext-v0-to-aes-v1";
    private static final Map<String, List<String>> FIELDS = Map.of(
            "product_merchant", List.of("credit_code", "legal_person", "contact_name", "contact_mobile"),
            "product_merchant_qualification", List.of("certificate_no", "file_url"));

    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        backfill();
    }

    public void backfill() {
        List<String> states = jdbcTemplate.queryForList(
                "SELECT state FROM product_merchant_encryption_migration WHERE migration_id = ?",
                String.class, MIGRATION_ID);
        if (states.isEmpty()) {
            throw new IllegalStateException("请先执行商家旧明文专用迁移 SQL");
        }
        for (Map.Entry<String, List<String>> entry : FIELDS.entrySet()) {
            for (String field : entry.getValue()) {
                while (Boolean.TRUE.equals(transactionTemplate.execute(status -> backfillBatch(entry.getKey(), field)))) {
                    // 每批独立提交，重启时仅继续处理剩余显式明文标记。
                }
            }
        }
        jdbcTemplate.update("UPDATE product_merchant_encryption_migration SET state = 'ENCRYPTED'"
                + " WHERE migration_id = ?", MIGRATION_ID);
    }

    private boolean backfillBatch(String table, String column) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT id, " + column + " FROM " + table
                + " WHERE " + column + " LIKE 'PLAIN:v0:%' LIMIT 200 FOR UPDATE");
        for (Map<String, Object> row : rows) {
            String markedValue = (String) row.get(column);
            String encryptedValue = MerchantSensitiveTypeHandler.encrypt(
                    markedValue.substring(MerchantSensitiveTypeHandler.LEGACY_PLAINTEXT_PREFIX.length()));
            jdbcTemplate.update("UPDATE " + table + " SET " + column + " = ? WHERE id = ? AND " + column + " = ?",
                    encryptedValue, row.get("id"), markedValue);
        }
        return !rows.isEmpty();
    }
}
