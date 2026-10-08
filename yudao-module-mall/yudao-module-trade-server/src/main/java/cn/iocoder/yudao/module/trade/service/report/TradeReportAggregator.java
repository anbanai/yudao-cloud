package cn.iocoder.yudao.module.trade.service.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates order item lines into the product rows used by picking and reconciliation reports.
 * Amounts are represented in cents, matching the trade module's integer price fields.
 */
public class TradeReportAggregator {

    public List<Row> aggregate(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        Map<Key, MutableRow> grouped = new LinkedHashMap<>();
        for (Line line : lines) {
            if (line == null) {
                continue;
            }
            Key key = new Key(line.spuId(), line.skuId(), normalize(line.properties()));
            MutableRow row = grouped.computeIfAbsent(key, ignored -> new MutableRow(line));
            row.quantity += line.quantity();
            row.amount += line.amount();
            row.orderIds.add(line.orderId());
        }
        List<Row> result = new ArrayList<>(grouped.size());
        for (MutableRow row : grouped.values()) {
            result.add(new Row(row.spuId, row.skuId, row.name, row.picUrl, row.barCode,
                    row.properties, row.quantity, row.amount, row.orderIds.size()));
        }
        return result;
    }

    private static String normalize(String properties) {
        return properties == null ? "" : properties;
    }

    public record Line(Long orderId, Long spuId, Long skuId, String name, String picUrl, String barCode,
                       String properties, int quantity, long amount) {
        public Line(Long orderId, Long skuId, String name, String picUrl, String barCode,
                    String properties, int quantity, long amount) {
            this(orderId, null, skuId, name, picUrl, barCode, properties, quantity, amount);
        }
    }

    public record Row(Long spuId, Long skuId, String name, String picUrl, String barCode,
                      String properties, int quantity, long amount, int orderCount) {
    }

    private record Key(Long spuId, Long skuId, String properties) {
    }

    private static final class MutableRow {
        private final Long spuId;
        private final Long skuId;
        private final String name;
        private final String picUrl;
        private final String barCode;
        private final String properties;
        private int quantity;
        private long amount;
        private final Set<Long> orderIds = new LinkedHashSet<>();

        private MutableRow(Line line) {
            this.spuId = line.spuId();
            this.skuId = line.skuId();
            this.name = line.name();
            this.picUrl = line.picUrl();
            this.barCode = line.barCode();
            this.properties = normalize(line.properties());
        }
    }
}
