package cn.iocoder.yudao.module.pay.service.payfund.reconciliation;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** 双向账本核对；外部账单与本地成功流水均须有对应项。 */
public final class YeepayBillComparator {
    private YeepayBillComparator() {}
    public record Local(YeepayBillParser.Entry entry, boolean success) {}
    public record Difference(String key, YeepayBillParser.Entry channel, Local local, String reason) {}
    public record Result(int matchedCount, long matchedAmount, List<Difference> differences) {}
    public static Result compare(List<YeepayBillParser.Entry> channel, List<Local> local, LocalDate date) {
        var remote = channel.stream().collect(Collectors.groupingBy(YeepayBillParser.Entry::key));
        var expected = local.stream().collect(Collectors.groupingBy(v -> v.entry().key()));
        List<Difference> differences = new ArrayList<>();
        int matchedCount = 0; long matchedAmount = 0;
        Set<String> keys = new LinkedHashSet<>(remote.keySet()); keys.addAll(expected.keySet());
        for (String key : keys) {
            var rs = remote.getOrDefault(key, List.of());
            var ls = expected.getOrDefault(key, List.of());
            var r = rs.isEmpty() ? null : rs.get(0);
            var l = ls.isEmpty() ? null : ls.get(0);
            List<String> reasons = new ArrayList<>();
            if (rs.size() > 1 || ls.size() > 1) reasons.add("流水标识重复");
            if (r == null) {
                if (l != null && l.success()) reasons.add("本地成功流水在账单缺失");
            } else if (l == null) {
                reasons.add("外部孤儿流水");
            } else {
                var e = l.entry();
                if (!l.success()) reasons.add("本地状态非成功");
                if (r.amount() != e.amount()) reasons.add("金额不一致");
                if (!Objects.equals(r.merchantNo(), e.merchantNo())) reasons.add("收款商编不一致");
                if (!Objects.equals(r.receiverMerchantNo(), e.receiverMerchantNo())) reasons.add("接收商编不一致");
                // Official refund samples leave original order columns blank.
                if (!(r.type() == YeepayBillParser.Type.REFUND && blank(r.orderId()))
                        && !Objects.equals(r.orderId(), e.orderId())) reasons.add("原订单号不一致");
                if (!blank(e.externalNo()) && !Objects.equals(r.externalNo(), e.externalNo())) reasons.add("渠道流水号不一致");
                if (r.type() == YeepayBillParser.Type.DIVIDE && !Objects.equals(r.detailNo(), e.detailNo())) reasons.add("分账明细号不一致");
                if (r.completedTime() == null || e.completedTime() == null
                        || !date.equals(r.completedTime().toLocalDate())
                        || !date.equals(e.completedTime().toLocalDate())) reasons.add("完成日期不一致");
            }
            if (!reasons.isEmpty()) differences.add(new Difference(key, r, l, String.join("；", reasons)));
            else if (r != null && l != null) { matchedCount++; matchedAmount = Math.addExact(matchedAmount, Math.abs(r.amount())); }
        }
        return new Result(matchedCount, matchedAmount, List.copyOf(differences));
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
