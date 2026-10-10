package cn.iocoder.yudao.module.pay.service.payfund.reconciliation;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static cn.iocoder.yudao.module.pay.service.payfund.reconciliation.YeepayBillParser.Type.*;
import static org.junit.jupiter.api.Assertions.*;

class YeepayBillComparatorTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 10);
    private static YeepayBillParser.Entry entry(YeepayBillParser.Type type, String request, long amount) {
        return new YeepayBillParser.Entry(type, "COLLECT", "PAY1", "YOP1", request,
                type == DIVIDE ? "DETAIL1" : null, type == DIVIDE ? "MER1" : null,
                amount, DATE.atTime(12, 0));
    }
    @Test void compare_detectsChannelOrphanAndMissingLocalSuccess() {
        var result = YeepayBillComparator.compare(List.of(entry(PAYMENT, "ORPHAN", 100)),
                List.of(new YeepayBillComparator.Local(entry(PAYMENT, "MISSING", 200), true)), DATE);
        assertEquals(2, result.differences().size());
        assertTrue(result.differences().stream().anyMatch(d -> d.reason().contains("外部孤儿")));
        assertTrue(result.differences().stream().anyMatch(d -> d.reason().contains("账单缺失")));
        assertEquals(0, result.matchedCount());
    }
    @Test void compare_checksAmountMerchantDetailAndState() {
        var remote = entry(DIVIDE, "DIV1", 100);
        var changed = new YeepayBillParser.Entry(DIVIDE, "OTHER", "PAY1", "YOP1", "DIV1", "WRONG",
                "MER1", 101, DATE.atTime(12, 0));
        var result = YeepayBillComparator.compare(List.of(remote),
                List.of(new YeepayBillComparator.Local(changed, false)), DATE);
        assertEquals(1, result.differences().size());
        String reason = result.differences().get(0).reason();
        assertTrue(reason.contains("金额")); assertTrue(reason.contains("收款商编"));
        assertTrue(reason.contains("分账明细")); assertTrue(reason.contains("本地状态"));
    }
    @Test void compare_rejectsDuplicateIdentityInsteadOfDoubleCounting() {
        var e = entry(PAYMENT, "PAY1", 100);
        var result = YeepayBillComparator.compare(List.of(e, e),
                List.of(new YeepayBillComparator.Local(e, true)), DATE);
        assertEquals(1, result.differences().size());
        assertTrue(result.differences().get(0).reason().contains("重复"));
    }
    @Test void compare_matchesRefundWhenOfficialOriginalOrderColumnIsBlank() {
        var remote = new YeepayBillParser.Entry(REFUND, "COLLECT", "", "YOR1", "REF1", null, null,
                100, DATE.atTime(12, 0));
        var expected = new YeepayBillParser.Entry(REFUND, "COLLECT", "PAY1", "YOR1", "REF1", null, null,
                100, DATE.atTime(12, 0));
        var result = YeepayBillComparator.compare(List.of(remote),
                List.of(new YeepayBillComparator.Local(expected, true)), DATE);
        assertTrue(result.differences().isEmpty()); assertEquals(100, result.matchedAmount());
    }
}
