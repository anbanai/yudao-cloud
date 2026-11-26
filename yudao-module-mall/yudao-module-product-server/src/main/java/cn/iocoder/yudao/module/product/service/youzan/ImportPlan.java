package cn.iocoder.yudao.module.product.service.youzan;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

public final class ImportPlan {
    private ImportPlan() {}

    public record Key(String product, String sku) {}
    public record Item(Key key, String name, String specification, Integer cents, Integer stock, Map<String,String> hints) {}
    public record Row(int number, Item item, String error) {}
    public record Existing(Item source, Long mallSku, Integer price, Integer stock) {}
    public record Line(Row row, String action, Existing before) {}

    public static int cents(String yuan) {
        if (!yuan.matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException();
        return new BigDecimal(yuan).movePointRight(2).intValueExact();
    }

    public static int stock(String units) {
        if (!units.matches("[0-9]{1,10}")) throw new IllegalArgumentException();
        return Integer.parseInt(units);
    }

    public static List<Line> evaluate(List<Row> rows, Function<Key,Existing> lookup) {
        Map<Key, Item> first = new HashMap<>();
        Set<Key> conflicts = new HashSet<>();
        for (Row row : rows) {
            if (row.item == null) continue;
            Item prev = first.putIfAbsent(row.item.key, row.item);
            if (prev != null && !prev.equals(row.item)) conflicts.add(row.item.key);
        }
        Set<Key> seen = new HashSet<>();
        List<Line> lines = new ArrayList<>();
        for (Row row : rows) {
            Item item = row.item;
            Existing before = null;
            String action;
            if (item == null) action = "ERROR";
            else if (conflicts.contains(item.key)) action = "CONFLICT";
            else if (!seen.add(item.key)) action = "DUPLICATE";
            else {
                before = lookup.apply(item.key);
                if (before == null) action = "PENDING";
                else if (!Objects.equals(before.price, item.cents) || !Objects.equals(before.stock, item.stock)) action = "DIFFERENCE";
                else action = before.source.equals(item) ? "DUPLICATE" : "CONFLICT";
            }
            lines.add(new Line(row, action, before));
        }
        return List.copyOf(lines);
    }
}
