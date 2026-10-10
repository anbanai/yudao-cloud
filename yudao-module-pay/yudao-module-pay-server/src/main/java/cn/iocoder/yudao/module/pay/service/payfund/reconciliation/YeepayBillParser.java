package cn.iocoder.yudao.module.pay.service.payfund.reconciliation;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;

/**
 * 官方商户对账说明中的交易日、分账日 CSV。按固定列位置读取并允许追加列；
 * 不接受未知前缀、截断数据、金额精度损失或汇总不一致，避免误报对账完成。
 */
public final class YeepayBillParser {

    private static final String[] TRADE_COLUMNS = {"请求时间", "完成时间", "收单商户编号", "商户请求号",
            "易宝订单号", "业务类型", "交易类型", "金额", "手续费", "手续费承担方商编", "商品名称", "对账备注",
            "原收款订单商户请求号", "原收款订单易宝订单号", "手续费收取类型", "支付产品"};
    private static final String[] DIVIDE_COLUMNS = {"请求时间", "完成时间", "收单商户编号", "商户收款请求号",
            "易宝收款订单号", "商户分账请求号", "易宝分账明细单号", "分账接收方编号", "分账金额",
            "分账说明", "分账类型", "原商户分账请求号"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    private YeepayBillParser() {}

    public enum Type { PAYMENT, REFUND, DIVIDE, DIVIDE_BACK }

    public record Entry(Type type, String merchantNo, String orderId, String externalNo,
                        String requestNo, String detailNo, String receiverMerchantNo,
                        long amount, LocalDateTime completedTime) {
        public String key() {
            return type + "|" + requestNo + "|" + (receiverMerchantNo == null ? "" : receiverMerchantNo);
        }
    }

    public static List<Entry> parse(byte[] data, boolean divide, LocalDate date, String merchantNo) {
        String csv;
        try {
            csv = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("易宝账单不是合法 UTF-8，需人工确认格式");
        }
        List<List<String>> rows = csv(csv.replaceFirst("^\\uFEFF", ""));
        int headerIndex = divide ? 3 : 4;
        require(rows.size() > headerIndex, "易宝账单缺少汇总或表头");
        List<String> owner = rows.get(0);
        require(owner.size() >= 4 && "账单日期".equals(owner.get(0))
                && date.toString().equals(owner.get(1)) && "对账单所属商户编号".equals(owner.get(2))
                && merchantNo.equals(owner.get(3)), "易宝账单日期或所属商编不匹配");
        String[] columns = divide ? DIVIDE_COLUMNS : TRADE_COLUMNS;
        List<String> header = rows.get(headerIndex);
        require(header.size() >= columns.length, "未知易宝账单表头");
        for (int i = 0; i < columns.length; i++) require(columns[i].equals(header.get(i)), "未知易宝账单列顺序");
        List<Entry> result = new ArrayList<>();
        long[] totals = new long[2];
        int[] counts = new int[2];
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            require(row.size() >= columns.length, "易宝账单明细截断");
            String business = row.get(divide ? 10 : 5);
            Type type = switch (business) {
                case "收款" -> Type.PAYMENT;
                case "退款" -> Type.REFUND;
                case "分账" -> Type.DIVIDE;
                case "分账退回" -> Type.DIVIDE_BACK;
                default -> throw new IllegalArgumentException("未知易宝账单业务类型");
            };
            require(divide == (type == Type.DIVIDE || type == Type.DIVIDE_BACK), "易宝账单业务类型不匹配");
            long signedAmount = fen(row.get(divide ? 8 : 7));
            boolean negative = type == Type.REFUND || type == Type.DIVIDE;
            require(negative ? signedAmount < 0 : signedAmount > 0, "易宝账单金额符号不匹配");
            long amount = Math.abs(signedAmount);
            require(amount > 0, "易宝账单金额不能归一为正整数分");
            LocalDateTime completed;
            try {
                completed = LocalDateTime.parse(row.get(1), TIME);
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException("易宝账单完成时间不合法");
            }
            require(date.equals(completed.toLocalDate()), "易宝账单完成日期不匹配");
            String request = row.get(divide ? 5 : 3);
            require(!request.isBlank() && !row.get(2).isBlank() && !row.get(4).isBlank(), "易宝账单缺少业务编号");
            if (divide) require(!row.get(6).isBlank() && !row.get(7).isBlank(), "易宝分账账单缺少明细或接收方");
            String order = type == Type.REFUND ? row.get(12) : row.get(3);
            result.add(new Entry(type, row.get(2), order, row.get(4), request,
                    divide ? row.get(6) : null, divide ? row.get(7) : null, amount, completed));
            int bucket = type == Type.REFUND || type == Type.DIVIDE_BACK ? 1 : 0;
            totals[bucket] = Math.addExact(totals[bucket], amount);
            counts[bucket]++;
        }
        List<String> amountSummary = rows.get(1), countSummary = rows.get(2);
        require(amountSummary.size() >= 4 && countSummary.size() >= 4, "易宝账单汇总截断");
        require((divide ? "分账总金额" : "交易总金额").equals(amountSummary.get(0))
                && (divide ? "分账退回总金额" : "退款总金额").equals(amountSummary.get(2))
                && (divide ? "分账总笔数" : "交易总笔数").equals(countSummary.get(0))
                && (divide ? "分账退回总笔数" : "退款总笔数").equals(countSummary.get(2)), "未知易宝账单汇总格式");
        require(fen(amountSummary.get(1)) == totals[0] && fen(amountSummary.get(3)) == totals[1]
                && Integer.parseInt(countSummary.get(1)) == counts[0]
                && Integer.parseInt(countSummary.get(3)) == counts[1], "易宝账单明细与汇总不一致");
        return List.copyOf(result);
    }

    private static long fen(String amount) {
        try {
            return new BigDecimal(amount).movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("易宝账单金额不合法或存在分以下精度");
        }
    }

    /** 支持 CSV 引号、转义引号及引号内换行；不将商品、备注等报文保存在数据库或日志。 */
    private static List<List<String>> csv(String input) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false, closedQuoted = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < input.length() && input.charAt(i + 1) == '"') {
                    cell.append('"'); i++;
                } else {
                    require(quoted || cell.length() == 0 && !closedQuoted, "非法 CSV 引号");
                    if (quoted) closedQuoted = true;
                    quoted = !quoted;
                }
            } else if (!quoted && (c == ',' || c == '\n' || c == '\r')) {
                row.add(cell.toString().trim()); cell.setLength(0); closedQuoted = false;
                if (c != ',') {
                    if (row.size() > 1 || !row.get(0).isEmpty()) rows.add(List.copyOf(row));
                    row.clear();
                    if (c == '\r' && i + 1 < input.length() && input.charAt(i + 1) == '\n') i++;
                    require(rows.size() <= 100000, "易宝账单明细超过处理上限");
                }
            } else {
                require(quoted || !closedQuoted || Character.isWhitespace(c), "CSV 引号后存在非法字符");
                cell.append(c);
            }
        }
        require(!quoted, "易宝 CSV 引号未闭合");
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString().trim()); rows.add(List.copyOf(row)); }
        return rows;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
