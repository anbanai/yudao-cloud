package cn.iocoder.yudao.module.member.service.importing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class YouzanCustomerImportRulesTest {

    @Test
    void testTemplateContractHasExactSourceColumns() {
        assertEquals(39, YouzanCustomerImportRules.HEADERS.size());
        assertEquals("客户测试名单", YouzanCustomerImportRules.SHEET_NAME);
        assertTrue(YouzanCustomerImportRules.hasExpectedHeaders(YouzanCustomerImportRules.HEADERS));
        var reordered = new ArrayList<>(YouzanCustomerImportRules.HEADERS);
        reordered.set(0, reordered.get(1));
        assertFalse(YouzanCustomerImportRules.hasExpectedHeaders(reordered));
    }

    @Test
    void testValidateIdentityAndPoints() {
        assertNull(YouzanCustomerImportRules.validateIdentityAndPoints("+86 138-0000-0000", "120"));
        assertEquals("手机号缺失或格式无效",
                YouzanCustomerImportRules.validateIdentityAndPoints("invalid", "120"));
        assertEquals("客户积分必须为非负整数",
                YouzanCustomerImportRules.validateIdentityAndPoints("13800000000", "-1"));
        assertEquals("客户积分必须为非负整数",
                YouzanCustomerImportRules.validateIdentityAndPoints("13800000000", "1.5"));
    }

    @Test
    void testPointOverflowIsRejectedAndMobileIsMasked() {
        assertNull(YouzanCustomerImportRules.parseLegacyPoints("999999999999999999999"));
        assertEquals("138****0000", YouzanCustomerImportRules.maskMobile("13800000000"));
        assertEquals("***", YouzanCustomerImportRules.maskMobile("not-a-mobile"));
    }

    @Test
    void testRepeatedSnapshotsAreSkippedAndConflictsAreReported() {
        var snapshots = new HashMap<String, Integer>();
        assertEquals(YouzanCustomerImportRules.SnapshotDisposition.NEW,
                YouzanCustomerImportRules.classifySnapshot("13800000000", "10", snapshots));
        assertEquals(YouzanCustomerImportRules.SnapshotDisposition.DUPLICATE,
                YouzanCustomerImportRules.classifySnapshot("+86 138-0000-0000", "10", snapshots));
        assertEquals(YouzanCustomerImportRules.SnapshotDisposition.CONFLICT,
                YouzanCustomerImportRules.classifySnapshot("13800000000", "11", snapshots));
        assertEquals(YouzanCustomerImportRules.SnapshotDisposition.INVALID,
                YouzanCustomerImportRules.classifySnapshot("invalid", "11", snapshots));
    }

    @Test
    void testKeyedHashIsStableAndDoesNotExposeRawIdentifier() {
        String first = YouzanCustomerImportRules.keyedHash("synthetic-mobile", "synthetic-test-secret");
        assertEquals(first, YouzanCustomerImportRules.keyedHash("synthetic-mobile", "synthetic-test-secret"));
        assertNotEquals(first, YouzanCustomerImportRules.keyedHash("synthetic-mobile", "different-secret"));
        assertNotEquals("synthetic-mobile", first);
    }
}
