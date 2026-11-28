package cn.iocoder.yudao.module.member.service.importing;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Resumes encrypted batches confirmed before direct mobile recovery was available. */
@Component
@Slf4j
@RequiredArgsConstructor
public class YouzanConfirmedBatchMigration implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final MemberYouzanImportService imports;

    private record Batch(long id, long tenant) { }

    @Override
    public void run(ApplicationArguments args) {
        // The import schema is optional on installations that do not use Youzan imports.
        boolean installed = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
            try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null,
                    "member_youzan_import_batch", new String[]{"TABLE"})) {
                while (tables.next()) {
                    if ("member_youzan_import_batch".equalsIgnoreCase(tables.getString("TABLE_NAME"))) return true;
                }
                return false;
            }
        }));
        if (!installed) return;

        long cursor = 0;
        while (true) {
            var batches = jdbc.query("""
                    SELECT id, tenant_id FROM member_youzan_import_batch
                    WHERE status IN (3,4) AND merchant_id IS NULL AND deleted=0 AND id>?
                    ORDER BY id LIMIT 100
                    """, (row, index) -> new Batch(row.getLong(1), row.getLong(2)), cursor);
            if (batches.isEmpty()) return;
            for (Batch batch : batches) {
                try {
                    TenantUtils.execute(batch.tenant(), () -> imports.migrateConfirmedBatch(batch.id()));
                } catch (RuntimeException failure) {
                    // Never log the exception message: parsing or JDBC errors can contain customer data.
                    log.error("有赞会员导入批次转换失败，检查应用密钥及数据库后重新确认或重启。tenantId={} batchId={} errorType={}",
                            batch.tenant(), batch.id(), failure.getClass().getSimpleName());
                    jdbc.update("""
                            UPDATE member_youzan_import_batch SET status=4
                            WHERE id=? AND tenant_id=? AND merchant_id IS NULL AND status=3 AND deleted=0
                            """, batch.id(), batch.tenant());
                }
                cursor = batch.id();
            }
        }
    }
}
