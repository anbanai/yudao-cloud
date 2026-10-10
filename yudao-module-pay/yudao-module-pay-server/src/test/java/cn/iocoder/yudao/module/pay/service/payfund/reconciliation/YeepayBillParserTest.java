package cn.iocoder.yudao.module.pay.service.payfund.reconciliation;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class YeepayBillParserTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 10);
    private static final String TRADE_HEADER = "请求时间,完成时间,收单商户编号,商户请求号,易宝订单号,业务类型,交易类型,金额,手续费,手续费承担方商编,商品名称,对账备注,原收款订单商户请求号,原收款订单易宝订单号,手续费收取类型,支付产品";
    private static final String DIVIDE_HEADER = "请求时间,完成时间,收单商户编号,商户收款请求号,易宝收款订单号,商户分账请求号,易宝分账明细单号,分账接收方编号,分账金额,分账说明,分账类型,原商户分账请求号";
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static String trade(String rows) {
        return "账单日期,2026-10-10,对账单所属商户编号,PARENT\n交易总金额,13.90,退款总金额,1.00\n交易总笔数,1,退款总笔数,1\n交易手续费金额,0.00,退款退回手续费金额,0.00\n" + TRADE_HEADER + "\n" + rows;
    }
    private static String divide(String rows) {
        return "账单日期,2026-10-10,对账单所属商户编号,PARENT\n分账总金额,13.90,分账退回总金额,1.00\n分账总笔数,1,分账退回总笔数,1\n" + DIVIDE_HEADER + "\n" + rows;
    }

    @Test void parseTrade_mapsPaymentAndPartialRefundWithOriginalOrder() {
        var entries = YeepayBillParser.parse(bytes(trade(
                "2026-10-10 01:00:00,2026-10-10 01:00:01,COLLECT,PAY1,YOP1,收款,标准收款,13.90,0,PARENT,\"商品,一\",备注,,,实收,小程序\n"
                + "2026-10-10 02:00:00,2026-10-10 02:00:01,COLLECT,REF1,YOR1,退款,标准收款,-1.00,0,PARENT,商品,备注,PAY1,YOP1,实收,小程序\n")), false, DATE, "PARENT");
        assertEquals(2, entries.size());
        assertEquals(1390, entries.get(0).amount());
        assertEquals(YeepayBillParser.Type.REFUND, entries.get(1).type());
        assertEquals("PAY1", entries.get(1).orderId());
        assertEquals("REF1", entries.get(1).requestNo());
        assertEquals(100, entries.get(1).amount());
    }

    @Test void parseDivide_mapsReceiverAndOriginalRequest() {
        var entries = YeepayBillParser.parse(bytes(divide(
                "2026-10-10 01:00:00,2026-10-10 01:00:01,COLLECT,PAY1,YOP1,DIV1,DETAIL1,MER1,-13.90,分账,分账,\n"
                + "2026-10-10 02:00:00,2026-10-10 02:00:01,COLLECT,PAY1,YOP1,BACK1,DETAIL1,MER1,1.00,退回,分账退回,DIV1\n")), true, DATE, "PARENT");
        assertEquals(1390, entries.get(0).amount());
        assertEquals("MER1", entries.get(0).receiverMerchantNo());
        assertEquals(YeepayBillParser.Type.DIVIDE_BACK, entries.get(1).type());
    }

    @Test void parse_rejectsUnknownSchemaWrongDateAndMerchant() {
        assertThrows(IllegalArgumentException.class, () -> YeepayBillParser.parse(bytes("unknown"), false, DATE, "PARENT"));
        assertThrows(IllegalArgumentException.class, () -> YeepayBillParser.parse(bytes(trade("").replace("2026-10-10", "2026-10-09")), false, DATE, "PARENT"));
        assertThrows(IllegalArgumentException.class, () -> YeepayBillParser.parse(bytes(trade("")), false, DATE, "OTHER"));
    }

    @Test void parse_rejectsTruncatedRowsAndFractionalFen() {
        assertThrows(IllegalArgumentException.class, () -> YeepayBillParser.parse(bytes(trade("2026-10-10 01:00:00,short\n")), false, DATE, "PARENT"));
        assertThrows(IllegalArgumentException.class, () -> YeepayBillParser.parse(bytes(trade("2026-10-10 01:00:00,2026-10-10 01:00:01,COLLECT,PAY1,YOP1,收款,标准收款,1.001,0,PARENT,商品,备注,,,实收,小程序\n")), false, DATE, "PARENT"));
    }
    @Test void parse_rejectsTrailingCharactersAfterQuotedCell() {
        String rows = "2026-10-10 01:00:00,2026-10-10 01:00:01,COLLECT,PAY1,YOP1,收款,标准收款,13.90,0,PARENT,\"商品\"x,备注,,,实收,小程序\n"
                + "2026-10-10 02:00:00,2026-10-10 02:00:01,COLLECT,REF1,YOR1,退款,标准收款,-1.00,0,PARENT,商品,备注,,,实收,小程序\n";
        assertThrows(IllegalArgumentException.class, () -> YeepayBillParser.parse(bytes(trade(rows)), false, DATE, "PARENT"));
    }

    @Test void parse_rejectsInvalidCalendarDateRatherThanNormalizingToBillDate() {
        String rows = "2026-10-10 01:00:00,2026-10-10 01:00:01,COLLECT,PAY1,YOP1,收款,标准收款,13.90,0,PARENT,商品,备注,,,实收,小程序\n"
                + "2026-10-10 02:00:00,2026-10-10 02:00:01,COLLECT,REF1,YOR1,退款,标准收款,-1.00,0,PARENT,商品,备注,,,实收,小程序\n";
        String invalid = trade(rows).replace("2026-10-10", "2026-02-28")
                .replace("2026-02-28 01:00:01", "2026-02-30 01:00:01");
        assertThrows(IllegalArgumentException.class, () -> YeepayBillParser.parse(bytes(invalid), false,
                LocalDate.of(2026, 2, 28), "PARENT"));
    }

    @Test void parse_rejectsMinimumLongRefundThatCannotNormalizeToPositiveFen() {
        String rows = "2026-10-10 01:00:00,2026-10-10 01:00:01,COLLECT,PAY1,YOP1,收款,标准收款,13.90,0,PARENT,商品,备注,,,实收,小程序\n"
                + "2026-10-10 02:00:00,2026-10-10 02:00:01,COLLECT,REF1,YOR1,退款,标准收款,-92233720368547758.08,0,PARENT,商品,备注,,,实收,小程序\n";
        String invalid = trade(rows).replace("退款总金额,1.00", "退款总金额,-92233720368547758.08");
        assertThrows(IllegalArgumentException.class, () -> YeepayBillParser.parse(bytes(invalid), false, DATE, "PARENT"));
    }
}
