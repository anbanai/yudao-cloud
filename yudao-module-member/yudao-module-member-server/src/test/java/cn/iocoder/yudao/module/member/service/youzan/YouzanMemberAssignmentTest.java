package cn.iocoder.yudao.module.member.service.youzan;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseDbUnitTest;
import cn.iocoder.yudao.module.member.service.importing.MemberYouzanImportService;
import cn.iocoder.yudao.module.member.service.importing.YouzanCustomerImportRules;
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
import java.util.stream.IntStream;

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
    void confirmedBatchStaysEncryptedAndUnclaimableUntilOwnerAssigns() throws Exception {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            long id = imports.preview(workbook(12, 18, 25), null).batchId();
            assertThrows(IllegalStateException.class, () -> imports.assign(id, 9L));
            var pending = imports.confirm(id);
            assertEquals(3, pending.status());
            assertNull(pending.merchantId());
            assertNull(pending.expiresAt());
            assertEquals(pending, imports.confirm(id));
            assertEquals(List.of(String.valueOf(id)), imports.listPending(null));
            var ciphertext = jdbc.queryForObject("SELECT encrypted_payload FROM member_youzan_import_batch WHERE id=?", String.class, id);
            assertNotNull(ciphertext);
            assertFalse(ciphertext.contains("13800000000"));
            assertFalse(pending.rows().toString().contains("13800000000"));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM member_youzan_point_snapshot", Integer.class));

            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(3L);
            assertTrue(imports.listPending(null).isEmpty());
            assertThrows(AccessDeniedException.class, () -> imports.getResult(id));
            assertThrows(AccessDeniedException.class, () -> imports.assign(id, 9L));
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            TenantContextHolder.setTenantId(2L);
            assertTrue(imports.listPending(null).isEmpty());
            assertThrows(AccessDeniedException.class, () -> imports.assign(id, 9L));
            TenantContextHolder.setTenantId(1L);
            assertThrows(AccessDeniedException.class, () -> imports.assign(id, 11L));
            assertEquals(pending, imports.getResult(id));

            imports.confirm(imports.preview(workbook(12, 99), 9L).batchId());
            var assigned = imports.assign(id, 9L);
            assertEquals(1, assigned.status());
            assertEquals("9", assigned.merchantId());
            assertEquals(1, assigned.importedRows());
            assertEquals(2, assigned.skippedRows());
            assertEquals(1, assigned.changedRows());
            assertEquals(List.of("重复快照，跳过", "来源快照已变化，仅报告", "已导入快照"),
                    assigned.rows().stream().map(MemberYouzanImportService.PreviewRow::outcome).toList());
            assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM member_youzan_point_snapshot WHERE merchant_id=9", Integer.class));
            assertNull(jdbc.queryForObject("SELECT encrypted_payload FROM member_youzan_import_batch WHERE id=?", String.class, id));
            assertEquals(assigned, imports.assign(id, 9L));
            assertEquals(assigned, imports.confirm(id));
            assertThrows(IllegalStateException.class, () -> imports.assign(id, 10L));
            assertTrue(imports.listPending(null).isEmpty());
        }
    }

    @Test
    void failedAssignmentRollsBackSnapshotsAndRetainsStagedDataForRetry() throws Exception {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            long id = imports.preview(workbook(12, 18), null).batchId();
            var pending = imports.confirm(id);
            jdbc.execute("ALTER TABLE member_youzan_point_snapshot ADD CONSTRAINT test_assign_stop CHECK (legacy_points <> 18)");
            try {
                var failure = assertThrows(org.springframework.dao.DataAccessException.class, () -> imports.assign(id, 9L));
                assertTrue(failure.getMostSpecificCause().getMessage().toLowerCase(java.util.Locale.ROOT).contains("test_assign_stop"));
            } finally {
                jdbc.execute("ALTER TABLE member_youzan_point_snapshot DROP CONSTRAINT test_assign_stop");
            }
            assertEquals(pending, imports.getResult(id));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM member_youzan_point_snapshot", Integer.class));
            assertEquals(2, imports.assign(id, 10L).importedRows());
        }
    }

    @Test
    void pendingListUsesAnExclusiveDescendingCursor() {
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            long first = 9_007_199_254_740_995L;
            for (int i = 0; i < 21; i++) {
                jdbc.update("INSERT INTO member_youzan_import_batch (id, tenant_id, operator_id, status, masked_rows) VALUES (?,1,2,3,'[]')", first + i);
            }
            List<String> expected = IntStream.rangeClosed(1, 20).mapToObj(i -> Long.toString(first + 21 - i)).toList();
            assertEquals(expected, imports.listPending(null));
            assertEquals(List.of(Long.toString(first)), imports.listPending(first + 1));
            assertTrue(imports.listPending(first).isEmpty());
        }
    }
}
