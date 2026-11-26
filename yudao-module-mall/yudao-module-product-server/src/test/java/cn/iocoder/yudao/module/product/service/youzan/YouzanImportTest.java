package cn.iocoder.yudao.module.product.service.youzan;

import org.junit.jupiter.api.Test;
import javax.crypto.AEADBadTagException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static cn.iocoder.yudao.module.product.service.youzan.ImportPlan.*;
import static org.junit.jupiter.api.Assertions.*;

class YouzanImportTest {
    private Item item(int index) {
        return new Item(new Key("00" + index / 2, "0" + index), "合成茶", "测试规格" + index,
                cents("12.34"), index, Map.of("状态", "仓库中", "预售提示", "待确认"));
    }

    @Test void fiveProductsNineSkusKeepTextIdsAndZeroStock() {
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < 9; index++) rows.add(new Row(index + 2, item(index), null));
        var lines = evaluate(rows, key -> null);
        assertEquals(5L, lines.stream().map(line -> line.row().item().key().product()).distinct().count());
        assertEquals(9, lines.stream().filter(line -> line.action().equals("PENDING")).count());
        assertEquals("000", rows.get(0).item().key().product());
        assertEquals(0, rows.get(0).item().stock());
        assertEquals(1234, rows.get(0).item().cents());
    }

    @Test void conflictsSkipBothRowsAndInvalidRowsKeepOriginalNumber() {
        Item first = item(0);
        var different = new Item(first.key(), first.name(), first.specification(), 3, 4, first.hints());
        var lines = evaluate(List.of(new Row(2, first, null), new Row(6, different, null), new Row(10, null, "格式无效")), key -> fail("不应查库"));
        assertEquals(List.of("CONFLICT", "CONFLICT", "ERROR"), lines.stream().map(Line::action).toList());
        assertEquals(10, lines.get(2).row().number());
    }

    @Test void reimportComparesLiveMallInsteadOfLastSourceAndNeverSchedulesWrites() {
        Item original = item(2);
        var row = new Row(2, original, null);
        Existing live = new Existing(original, 123L, 80, 75);
        var difference = evaluate(List.of(row), key -> live).get(0);
        assertEquals("DIFFERENCE", difference.action());
        assertEquals(80, difference.before().price());
        assertEquals(75, difference.before().stock());
        assertEquals("DUPLICATE", evaluate(List.of(row), key -> new Existing(original, null, original.cents(), original.stock())).get(0).action());
        assertEquals(List.of("PENDING", "DUPLICATE"), evaluate(List.of(row, row), key -> null).stream().map(Line::action).toList());
    }

    @Test void invalidPricesAndStocksAreNotRoundedOrWrapped() {
        for (String bad : List.of("1.001", "-1", "1E4", "", "30000000")) assertThrows(RuntimeException.class, () -> cents(bad));
        for (String bad : List.of("1.5", "-1", "", "2147483648")) assertThrows(RuntimeException.class, () -> stock(bad));
        assertEquals(1, cents("0.01"));
    }

    @Test void ciphertextIsBoundToTenantMerchantOperatorAndBatchAndRejectsTamper() throws Exception {
        var cipher = new PreviewCipher(Base64.getEncoder().encodeToString(new byte[32]));
        byte[] plain = "合成草稿".getBytes(StandardCharsets.UTF_8);
        String aad = "1:2:3:a";
        byte[] sealed = cipher.seal(aad, plain);
        assertFalse(Arrays.equals(sealed, cipher.seal(aad, plain)));
        assertArrayEquals(plain, cipher.open(aad, sealed));
        for (String other : List.of("9:2:3:a", "1:9:3:a", "1:2:9:a", "1:2:3:b")) assertThrows(AEADBadTagException.class, () -> cipher.open(other, sealed));
        sealed[sealed.length - 1] ^= 1;
        assertThrows(AEADBadTagException.class, () -> cipher.open(aad, sealed));
    }
}
