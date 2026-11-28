package cn.iocoder.yudao.module.member.service.youzan;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.service.importing.MemberYouzanImportService;
import cn.iocoder.yudao.module.member.service.importing.YouzanCustomerImportRules;
import cn.iocoder.yudao.module.member.service.importing.YouzanConfirmedBatchMigration;
import jakarta.annotation.Resource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

@Import({MemberYouzanImportService.class, YouzanClaimFlowTest.JdbcConfig.class})
@TestPropertySource(properties = "mybatis-plus.encryptor.password=synthetic-key-01")
@Sql(statements = "SELECT 1", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class YouzanMemberAssignmentTest extends BaseDbUnitTest {

    @Resource MemberYouzanImportService imports;
    @Resource JdbcTemplate jdbc;

    @DynamicPropertySource
    static void disposableDatabase(DynamicPropertyRegistry properties) {
        YouzanClaimFlowTest.disposableDatabase(properties);
    }

    @BeforeEach
    void seed() throws Exception {
        TenantContextHolder.setTenantId(1L);
        String ddl = Files.readString(Path.of("../../sql/mysql/member_youzan_import.sql")).split("INSERT INTO `system_menu`")[0];
        for (String statement : ddl.replace("b'0'", "false").split(";")) {
            if (!statement.isBlank()) jdbc.execute(statement);
        }
        if (YouzanClaimFlowTest.PORT == null) {
            jdbc.execute("ALTER TABLE member_youzan_point_snapshot ALTER COLUMN merchant_id DROP NOT NULL");
            jdbc.execute("ALTER TABLE member_youzan_point_snapshot ADD COLUMN IF NOT EXISTS direct_mobile_hash CHAR(64) "
                    + "GENERATED ALWAYS AS (CASE WHEN merchant_id IS NULL THEN mobile_hash END)");
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_youzan_direct_mobile ON member_youzan_point_snapshot(tenant_id, direct_mobile_hash)");
        }
        jdbc.update("DELETE FROM member_youzan_point_snapshot");
        jdbc.update("DELETE FROM member_youzan_import_batch");
        jdbc.update("DELETE FROM member_youzan_import_grant");
        jdbc.update("INSERT INTO member_youzan_import_grant VALUES (1,9,2), (1,10,2)");
    }

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    private MockMultipartFile workbook(int... points) throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet(YouzanCustomerImportRules.SHEET_NAME);
            var header = sheet.createRow(0);
            for (int i = 0; i < YouzanCustomerImportRules.HEADERS.size(); i++) {
                header.createCell(i).setCellValue(YouzanCustomerImportRules.HEADERS.get(i));
            }
            for (int i = 0; i < points.length; i++) {
                var row = sheet.createRow(i + 1);
                row.createCell(2).setCellValue("1380000000" + i);
                row.createCell(11).setCellValue(Integer.toString(points[i]));
            }
            book.write(bytes);
            return new MockMultipartFile("file", "synthetic.xlsx", "application/octet-stream", bytes.toByteArray());
        }
    }

    @Test
    void confirmedDirectRowsAreTenantScopedAndIdempotent() throws Exception {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            jdbc.update("DELETE FROM member_youzan_import_grant");
            var uploaded = imports.preview(workbook(12, 18), null);
            assertEquals(0, countSnapshots());
            var result = imports.confirm(uploaded.batchId());
            assertEquals(1, result.status());
            assertNull(result.merchantId());
            assertEquals(2, result.importedRows());
            assertEquals(result, imports.confirm(uploaded.batchId()));
            assertFalse(result.rows().toString().contains("13800000000"));
            assertNull(jdbc.queryForObject("SELECT encrypted_payload FROM member_youzan_import_batch WHERE id=?", String.class, result.batchId()));
            assertEquals(2, countSnapshots());
            var repeat = imports.confirm(imports.preview(workbook(12, 99), null).batchId());
            assertEquals(2, repeat.skippedRows());
            assertEquals(1, repeat.changedRows());
            assertEquals(List.of("重复快照，跳过", "来源快照已变化，仅报告"), repeat.rows().stream()
                    .map(MemberYouzanImportService.PreviewRow::outcome).toList());
            assertEquals(2, countSnapshots());
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(3L);
            assertThrows(AccessDeniedException.class, () -> imports.getResult(result.batchId()));
            TenantContextHolder.setTenantId(2L);
            assertThrows(AccessDeniedException.class, () -> imports.confirm(result.batchId()));
            assertEquals(2, imports.confirm(imports.preview(workbook(12, 18), null).batchId()).importedRows());
            assertEquals(4, countSnapshots());
        }
    }

    @Test
    void startupConvertsOnlyPreviouslyConfirmedBatchesAcrossTenants() throws Exception {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            long first = imports.preview(workbook(12), null).batchId();
            long corrupt = imports.preview(workbook(45), null).batchId();
            String encrypted = jdbc.queryForObject("SELECT encrypted_payload FROM member_youzan_import_batch WHERE id=?", String.class, corrupt);
            jdbc.update("UPDATE member_youzan_import_batch SET status=3, expires_at=NULL, encrypted_payload='damaged' WHERE id=?", corrupt);
            long preview = imports.preview(workbook(21), null).batchId();
            TenantContextHolder.setTenantId(7L);
            long second = imports.preview(workbook(13), null).batchId();
            jdbc.update("UPDATE member_youzan_import_batch SET status=3, expires_at=NULL WHERE id IN (?,?)", first, second);
            TenantContextHolder.clear();
            var migration = new YouzanConfirmedBatchMigration(jdbc, imports);
            migration.run(null);
            migration.run(null);
            assertNull(TenantContextHolder.getTenantId());
            assertEquals(2, countSnapshots());
            assertEquals(List.of(1L, 7L), jdbc.queryForList("SELECT tenant_id FROM member_youzan_point_snapshot ORDER BY tenant_id", Long.class));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM member_youzan_import_batch WHERE status=1 AND encrypted_payload IS NULL", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT status FROM member_youzan_import_batch WHERE id=?", Integer.class, preview));
            TenantContextHolder.setTenantId(1L);
            assertEquals(4, imports.getResult(corrupt).status());
            jdbc.update("UPDATE member_youzan_import_batch SET encrypted_payload=? WHERE id=?", encrypted, corrupt);
            assertEquals(1, imports.confirm(corrupt).status());
            assertEquals(2, countSnapshots());
        }
    }

    @Test
    void failedDirectImportKeepsEncryptedPreviewForRetry() throws Exception {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            long id = imports.preview(workbook(12, 18), null).batchId();
            jdbc.execute("ALTER TABLE member_youzan_point_snapshot ADD CONSTRAINT test_direct_stop CHECK (legacy_points <> 18)");
            try {
                var failure = assertThrows(org.springframework.dao.DataAccessException.class, () -> imports.confirm(id));
                assertTrue(failure.getMostSpecificCause().getMessage().toLowerCase(java.util.Locale.ROOT).contains("test_direct_stop"));
            } finally {
                jdbc.execute("ALTER TABLE member_youzan_point_snapshot DROP CONSTRAINT test_direct_stop");
            }
            assertEquals(0, imports.getResult(id).status());
            assertEquals(0, countSnapshots());
            assertEquals(2, imports.confirm(id).importedRows());
        }
    }

    private int countSnapshots() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM member_youzan_point_snapshot", Integer.class);
    }
}
