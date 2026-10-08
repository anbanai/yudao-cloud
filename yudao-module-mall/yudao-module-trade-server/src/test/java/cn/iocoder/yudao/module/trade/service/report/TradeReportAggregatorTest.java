package cn.iocoder.yudao.module.trade.service.report;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TradeReportAggregatorTest {

    private final TradeReportAggregator aggregator = new TradeReportAggregator();

    @Test
    void aggregate_emptyInput_returnsEmptyRows() {
        assertThat(aggregator.aggregate(List.of())).isEmpty();
    }

    @Test
    void aggregate_mergesSameSkuAndPropertiesAndKeepsDifferentPropertiesSeparate() {
        List<TradeReportAggregator.Line> lines = List.of(
                new TradeReportAggregator.Line(10L, 100L, "Tea", "https://img/tea.png", "SKU-10", "Green", 2, 1200),
                new TradeReportAggregator.Line(11L, 100L, "Tea", "https://img/tea.png", "SKU-10", "Green", 3, 1800),
                new TradeReportAggregator.Line(12L, 100L, "Tea", "https://img/tea.png", "SKU-10", "Black", 1, 700)
        );

        List<TradeReportAggregator.Row> rows = aggregator.aggregate(lines);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).quantity()).isEqualTo(5);
        assertThat(rows.get(0).amount()).isEqualTo(3000);
        assertThat(rows.get(0).orderCount()).isEqualTo(2);
        assertThat(rows.get(1).properties()).isEqualTo("Black");
    }

    @Test
    void aggregate_keepsSameSkuFromDifferentSpuSeparate() {
        List<TradeReportAggregator.Line> lines = List.of(
                new TradeReportAggregator.Line(10L, 1L, 100L, "Tea A", null, "SKU-10", "Green", 1, 500),
                new TradeReportAggregator.Line(11L, 2L, 100L, "Tea B", null, "SKU-10", "Green", 2, 1000)
        );

        List<TradeReportAggregator.Row> rows = aggregator.aggregate(lines);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(TradeReportAggregator.Row::spuId)
                .containsExactly(1L, 2L);
    }

    @Test
    void aggregate_ignoresNullLines_andCountsDistinctOrders() {
        List<TradeReportAggregator.Line> lines = Arrays.asList(
                null,
                new TradeReportAggregator.Line(10L, 100L, "Tea", null, "SKU-10", "Green", 1, 500),
                new TradeReportAggregator.Line(10L, 100L, "Tea", null, "SKU-10", "Green", 2, 1000)
        );

        List<TradeReportAggregator.Row> rows = aggregator.aggregate(lines);

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.quantity()).isEqualTo(3);
            assertThat(row.amount()).isEqualTo(1500);
            assertThat(row.orderCount()).isEqualTo(1);
        });
    }

}
