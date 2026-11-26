package cn.iocoder.yudao.module.member.service.importing;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipInputStream;

final class YouzanMemberWorkbook {
    private YouzanMemberWorkbook() { }

    static List<Map<Integer, String>> read(MultipartFile file) {
        if (!Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT).endsWith(".xlsx") || file.getSize() > 4_000_000) {
            throw new IllegalArgumentException("请上传不超过 4 MB 的 XLSX 模板");
        }
        try (var archive = new ZipInputStream(file.getInputStream())) {
            byte[] buffer = new byte[8192];
            int entries = 0, total = 0, size;
            while (archive.getNextEntry() != null) {
                if (++entries > 200) throw new IOException();
                while ((size = archive.read(buffer)) != -1) if ((total += size) > 12_000_000) throw new IOException();
            }
        } catch (IOException ex) { throw new IllegalArgumentException("工作簿大小或格式无效"); }
        var headers = YouzanCustomerImportRules.HEADERS;
        try (var input = file.getInputStream(); var book = new XSSFWorkbook(input)) {
            var sheet = book.getSheet(YouzanCustomerImportRules.SHEET_NAME);
            if (sheet == null || !YouzanCustomerImportRules.SHEET_NAME.equals(sheet.getSheetName()) || sheet.getLastRowNum() > 10000) throw new IOException();
            var first = sheet.getRow(0);
            if (first == null || first.getLastCellNum() != headers.size()) throw new IOException();
            Map<Integer,String> head = new HashMap<>();
            for (int index = 0; index < headers.size(); index++) {
                var cell = first.getCell(index);
                if (cell == null || cell.getCellType() != CellType.STRING || !headers.get(index).equals(cell.getStringCellValue())) throw new IOException();
                head.put(index, headers.get(index));
            }
            List<Map<Integer,String>> rows = new ArrayList<>();
            rows.add(head);
            for (int number = 1; number <= sheet.getLastRowNum(); number++) {
                var row = sheet.getRow(number);
                Map<Integer,String> fields = new HashMap<>();
                rows.add(fields);
                if (row == null) continue;
                if (row.getLastCellNum() > headers.size()) throw new IOException();
                for (var cell : row) {
                    if (cell.getCellType() != CellType.BLANK) { fields.put(-1, "present"); break; }
                }
                for (int index : List.of(2, 7, 8, 11)) {
                    var cell = row.getCell(index);
                    if (cell == null || cell.getCellType() == CellType.BLANK) continue;
                    String value = switch (cell.getCellType()) {
                        case STRING -> cell.getStringCellValue();
                        case NUMERIC -> NumberToTextConverter.toText(cell.getNumericCellValue());
                        default -> "#INVALID";
                    };
                    fields.put(index, value.length() > 128 ? "#INVALID" : value);
                }
            }
            return rows;
        } catch (IOException | RuntimeException ex) { throw new IllegalArgumentException("客户模板工作表、表头或数据结构无效"); }
    }
}
