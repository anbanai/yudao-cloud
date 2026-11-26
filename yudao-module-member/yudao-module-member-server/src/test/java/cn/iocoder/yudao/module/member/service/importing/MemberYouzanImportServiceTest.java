package cn.iocoder.yudao.module.member.service.importing;

import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.member.dal.dataobject.importing.MemberYouzanImportBatchDO;
import cn.iocoder.yudao.module.member.dal.dataobject.importing.MemberYouzanPointSnapshotDO;
import cn.iocoder.yudao.module.member.dal.dataobject.level.MemberLevelDO;
import cn.iocoder.yudao.module.member.dal.mysql.importing.MemberYouzanImportBatchMapper;
import cn.iocoder.yudao.module.member.dal.mysql.importing.MemberYouzanPointSnapshotMapper;
import cn.iocoder.yudao.module.member.dal.mysql.level.MemberLevelMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberYouzanImportServiceTest {
    private MockMultipartFile workbook(boolean wrongHeader) throws Exception {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet(YouzanCustomerImportRules.SHEET_NAME);
            var header = sheet.createRow(0);
            var columns = YouzanCustomerImportRules.HEADERS;
            for (int index = 0; index < columns.size(); index++) header.createCell(index).setCellValue(columns.get(index));
            if (wrongHeader) header.getCell(2).setCellValue("无效表头");
            String[] suffixes = {"01", "01", "02", "02", "03", ""};
            String[] points = {"7", "7", "8", "8", "-1", "0"};
            for (int index = 0; index < points.length; index++) {
                var row = sheet.createRow(index + 1);
                row.createCell(2).setCellValue(suffixes[index].isEmpty() ? "" : "138000000" + suffixes[index]);
                row.createCell(7).setCellValue(index == 3 ? "2" : "1");
                row.createCell(8).setCellValue("合成等级1");
                row.createCell(11).setCellValue(points[index]);
            }
            book.write(output);
            return new MockMultipartFile("file", "synthetic.xlsx", "application/octet-stream", output.toByteArray());
        }
    }

    @Test
    void sixRowsRejectConflictsAndBindTheConfirmation() throws Exception {
        var batches = mock(MemberYouzanImportBatchMapper.class);
        var snapshots = mock(MemberYouzanPointSnapshotMapper.class);
        var levels = mock(MemberLevelMapper.class);
        var jdbc = mock(JdbcTemplate.class);
        var service = new MemberYouzanImportService();
        ReflectionTestUtils.setField(service, "batchMapper", batches);
        ReflectionTestUtils.setField(service, "snapshotMapper", snapshots);
        ReflectionTestUtils.setField(service, "levelMapper", levels);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(service, "hashSecret", "synthetic-only");
        var saved = new AtomicReference<MemberYouzanImportBatchDO>();
        when(batches.insert(any(MemberYouzanImportBatchDO.class))).thenAnswer(call -> {
            MemberYouzanImportBatchDO batch = call.getArgument(0);
            batch.setId(12L); saved.set(batch); return 1;
        });
        when(batches.selectByIdForUpdate(12L)).thenAnswer(call -> saved.get());
        when(jdbc.queryForList(anyString(), eq(Long.class), eq(1L), eq(9L), eq(2L))).thenReturn(List.of(2L));
        when(levels.selectList(any(LambdaQueryWrapper.class))).thenReturn(IntStream.rangeClosed(1, 5)
                .mapToObj(index -> MemberLevelDO.builder().id((long) index).name("合成等级" + index).build()).toList());
        TenantContextHolder.setTenantId(1L);
        try (var security = mockStatic(SecurityFrameworkUtils.class)) {
            security.when(SecurityFrameworkUtils::getLoginUserId).thenReturn(2L);
            assertThrows(IllegalArgumentException.class, () -> service.preview(workbook(true), 9L));
            verifyNoInteractions(batches, snapshots);
            var result = service.preview(workbook(false), 9L);
            assertEquals(1, result.validCount());
            assertEquals(5, result.invalidCount());
            assertTrue(result.rows().get(2).outcome().contains("整组跳过"));
            assertEquals(result.rows().get(2).outcome(), result.rows().get(3).outcome());
            TenantContextHolder.setTenantId(3L);
            assertThrows(AccessDeniedException.class, () -> service.confirm(12L));
            TenantContextHolder.setTenantId(1L);
            var existing = new MemberYouzanPointSnapshotDO();
            existing.setId(100L); existing.setRestoreStatus(1); existing.setSnapshotHash("changed");
            when(snapshots.findSource(eq(9L), anyString(), eq(true))).thenReturn(existing);
            assertThrows(IllegalStateException.class, () -> service.confirm(12L));
            verify(snapshots, never()).insert(any(MemberYouzanPointSnapshotDO.class));
            when(snapshots.findSource(eq(9L), anyString(), eq(true))).thenReturn(null);
            assertEquals(1, service.confirm(12L).importedRows());
            assertEquals(1, service.confirm(12L).importedRows());
            verify(snapshots).insert(any(MemberYouzanPointSnapshotDO.class));
            service.preview(workbook(false), 9L);
            saved.get().setExpiresAt(LocalDateTime.now().minusSeconds(1));
            assertThrows(IllegalStateException.class, () -> service.confirm(12L));
        } finally { TenantContextHolder.clear(); }
    }
}
