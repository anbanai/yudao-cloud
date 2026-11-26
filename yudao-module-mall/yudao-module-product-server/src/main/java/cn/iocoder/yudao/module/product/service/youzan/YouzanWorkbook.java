package cn.iocoder.yudao.module.product.service.youzan;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import static cn.iocoder.yudao.module.product.service.youzan.ImportPlan.*;

public final class YouzanWorkbook {
    private YouzanWorkbook() {}
    private record Column(String role, String header) {}
    private static List<Column> columns() {
        InputStream input = YouzanWorkbook.class.getResourceAsStream("/youzan/product-columns.tsv");
        if (input == null) throw new IllegalStateException("商品模板契约未安装");
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            var columns = reader.lines().map(line -> line.split("\t", 2)).map(pair -> new Column(pair[0], pair[1])).toList();
            if (columns.size() != 52) throw new IllegalStateException("商品模板契约不完整");
            return columns;
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }

    public static List<String> unused() {
        return columns().stream().filter(col -> col.role.equals("ignore")).map(Column::header).toList();
    }

    public static void template(OutputStream output) throws IOException {
        try (var book = new XSSFWorkbook()) {
            var sheet = book.createSheet("Sheet1");
            var row = sheet.createRow(0);
            var textStyle = book.createCellStyle();
            textStyle.setDataFormat(book.createDataFormat().getFormat("@"));
            var columns = columns();
            for (int index = 0; index < columns.size(); index++) {
                Column column = columns.get(index);
                row.createCell(index).setCellValue(column.header);
                if (Set.of("product", "sku", "code").contains(column.role)) sheet.setDefaultColumnStyle(index, textStyle);
            }
            book.write(output);
        }
    }

    public static List<ImportPlan.Row> read(MultipartFile file) {
        var columns = columns();
        if (!Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || file.getSize() > 4_000_000) throw new IllegalArgumentException("请上传不超过 4 MB 的 .xlsx 文件");
        try (var input = file.getInputStream(); var zip = new ZipInputStream(input)) {
            long total = 0;
            int entries = 0, count;
            byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entries > 200) throw new IOException();
                while ((count = zip.read(buffer)) >= 0) if ((total += count) > 8_000_000) throw new IOException();
            }
        } catch (Exception ex) { throw new IllegalArgumentException("工作簿格式或大小不符合要求"); }
        try (var input = file.getInputStream(); var book = new XSSFWorkbook(input)) {
            if (book.getNumberOfSheets() != 1 || !"Sheet1".equals(book.getSheetName(0))) throw new IOException();
            var sheet = book.getSheetAt(0);
            if (sheet.getLastRowNum() > 1000) throw new IOException();
            var header = sheet.getRow(0);
            if (header == null || header.getLastCellNum() != 52) throw new IOException();
            for (int index = 0; index < columns.size(); index++) {
                var cell = header.getCell(index);
                if (cell == null || cell.getCellType() != CellType.STRING || !columns.get(index).header.equals(cell.getStringCellValue())) throw new IOException();
            }
            List<ImportPlan.Row> rows = new ArrayList<>();
            for (var row : sheet) {
                if (row.getRowNum() == 0) continue;
                if (row.getLastCellNum() > 52) throw new IOException();
                Map<String,String> fields = new HashMap<>(), hints = new LinkedHashMap<>();
                boolean empty = true;
                try {
                    for (int index = 0; index < columns.size(); index++) {
                        var column = columns.get(index);
                        var cell = row.getCell(index);
                        if (cell == null || cell.getCellType() == CellType.BLANK) continue;
                        empty = false;
                        if (column.role.equals("ignore")) continue;
                        String value = switch (cell.getCellType()) {
                            case STRING -> cell.getStringCellValue();
                            case NUMERIC -> {
                                if (Set.of("product", "sku", "code").contains(column.role)) {
                                    double id = cell.getNumericCellValue();
                                    if (id < 0 || id >= 1e15 || id != Math.rint(id)) throw new IllegalArgumentException();
                                    String format = cell.getCellStyle().getDataFormatString();
                                    if ("General".equalsIgnoreCase(format) || "@".equals(format)) yield Long.toString((long) id);
                                    if (format == null || !format.matches("0{1,80}")) throw new IllegalArgumentException();
                                    yield new DataFormatter(Locale.ROOT).formatCellValue(cell);
                                }
                                yield org.apache.poi.ss.util.NumberToTextConverter.toText(cell.getNumericCellValue());
                            }
                            default -> throw new IllegalArgumentException();
                        };
                        if (value.length() > 512) throw new IllegalArgumentException();
                        if (column.role.equals("hint") || column.role.equals("code")) hints.put(column.header, value);
                        else fields.put(column.role, value);
                    }
                    if (empty) continue;
                    Key key = new Key(fields.getOrDefault("product", ""), fields.getOrDefault("sku", ""));
                    if (!validId(key.product()) || !validId(key.sku())) throw new IllegalArgumentException();
                    String name = fields.getOrDefault("name", "");
                    if (name.length() > 255) throw new IllegalArgumentException();
                    String price = fields.getOrDefault("price", ""), units = fields.getOrDefault("stock", "");
                    var item = new Item(key, name.isBlank() ? "" : name, fields.getOrDefault("spec", ""),
                            price.isBlank() ? null : cents(price), units.isBlank() ? null : stock(units), hints);
                    rows.add(new ImportPlan.Row(row.getRowNum() + 1, item, null));
                } catch (IllegalArgumentException | ArithmeticException ex) {
                    rows.add(new ImportPlan.Row(row.getRowNum() + 1, null, "标识、名称、价格、库存或单元格格式无效"));
                }
            }
            if (rows.isEmpty()) throw new IOException();
            return rows;
        } catch (Exception ex) { throw new IllegalArgumentException("请使用原始 Sheet1 商品模板，最多 1000 行"); }
    }

    private static boolean validId(String value) { return value.matches("[A-Za-z0-9_-]{1,80}"); }
}
