package com.lightweightai.web.skillcreator;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolSchemaExcelImporter 单元测试
 *
 * 覆盖关键路径：
 * - 默认列顺序解析 (name/description/category/inputSchema/outputSchema)
 * - 中文表头别名识别
 * - 空行 / 空 name 行自动跳过
 * - 数字单元格自动转字符串
 * - 损坏流抛出 RuntimeException
 * - 默认 status 为 enabled
 */
@DisplayName("ToolSchemaExcelImporter - Excel 工具 Schema 导入")
class ToolSchemaExcelImporterTest {

    private final ToolSchemaExcelImporter importer = new ToolSchemaExcelImporter();

    private static InputStream buildWorkbook(String[] headers, Object[][] rows) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("tools");
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                Object[] values = rows[r];
                if (values == null) continue;
                for (int c = 0; c < values.length; c++) {
                    Object v = values[c];
                    if (v == null) continue;
                    Cell cell = row.createCell(c);
                    if (v instanceof Number) {
                        cell.setCellValue(((Number) v).doubleValue());
                    } else if (v instanceof Boolean) {
                        cell.setCellValue((Boolean) v);
                    } else {
                        cell.setCellValue(v.toString());
                    }
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Test
    @DisplayName("默认英文表头顺序应解析所有字段")
    void shouldParseDefaultHeaderOrder() throws Exception {
        String[] headers = {"name", "description", "category", "inputSchema", "outputSchema"};
        Object[][] rows = {
            {"GeoInformation.GetPosition", "获取位置", "地理信息", "{\"type\":\"object\"}", "{\"result\":\"ok\"}"},
            {"Weather.Query", "查询天气", "天气", "{\"type\":\"object\"}", null}
        };

        List<ToolSchemaEntry> entries = importer.parseExcel(buildWorkbook(headers, rows));

        assertEquals(2, entries.size());

        ToolSchemaEntry first = entries.get(0);
        assertEquals("GeoInformation.GetPosition", first.getName());
        assertEquals("获取位置", first.getDescription());
        assertEquals("地理信息", first.getCategory());
        assertEquals("{\"type\":\"object\"}", first.getInputSchemaJson());
        assertEquals("{\"result\":\"ok\"}", first.getOutputSchemaJson());
        assertEquals("enabled", first.getStatus());

        ToolSchemaEntry second = entries.get(1);
        assertEquals("Weather.Query", second.getName());
        // outputSchema 列缺失 -> 字段保持 null 或空字符串,不做严格断言
        String out = second.getOutputSchemaJson();
        assertTrue(out == null || out.isEmpty(), "expected blank outputSchema, got: " + out);
    }

    @Test
    @DisplayName("中文表头应被识别")
    void shouldRecognizeChineseHeaders() throws Exception {
        String[] headers = {"名称", "描述", "分类", "输入schema"};
        Object[][] rows = {
            {"ToolA", "描述A", "类别1", "{}"}
        };

        List<ToolSchemaEntry> entries = importer.parseExcel(buildWorkbook(headers, rows));

        assertEquals(1, entries.size());
        assertEquals("ToolA", entries.get(0).getName());
        assertEquals("描述A", entries.get(0).getDescription());
        assertEquals("类别1", entries.get(0).getCategory());
        assertEquals("{}", entries.get(0).getInputSchemaJson());
    }

    @Test
    @DisplayName("打乱的列顺序应被表头映射修正")
    void shouldMapShuffledColumns() throws Exception {
        String[] headers = {"description", "name", "category"};
        Object[][] rows = {
            {"some desc", "MyTool", "cat1"}
        };

        List<ToolSchemaEntry> entries = importer.parseExcel(buildWorkbook(headers, rows));

        assertEquals(1, entries.size());
        assertEquals("MyTool", entries.get(0).getName());
        assertEquals("some desc", entries.get(0).getDescription());
        assertEquals("cat1", entries.get(0).getCategory());
    }

    @Test
    @DisplayName("空 name 行应被跳过")
    void shouldSkipRowsWithBlankName() throws Exception {
        String[] headers = {"name", "description"};
        Object[][] rows = {
            {"Valid", "desc1"},
            {"", "desc2"},
            {null, "desc3"},
            {"Another", "desc4"}
        };

        List<ToolSchemaEntry> entries = importer.parseExcel(buildWorkbook(headers, rows));

        assertEquals(2, entries.size());
        assertEquals("Valid", entries.get(0).getName());
        assertEquals("Another", entries.get(1).getName());
    }

    @Test
    @DisplayName("数字单元格应被转换为字符串")
    void shouldConvertNumericCellsToString() throws Exception {
        String[] headers = {"name", "description"};
        Object[][] rows = {
            {"tool42", 12345.0}
        };

        List<ToolSchemaEntry> entries = importer.parseExcel(buildWorkbook(headers, rows));

        assertEquals(1, entries.size());
        assertEquals("tool42", entries.get(0).getName());
        assertEquals("12345", entries.get(0).getDescription());
    }

    @Test
    @DisplayName("损坏的输入流应抛 RuntimeException")
    void shouldThrowOnInvalidStream() {
        InputStream bad = new ByteArrayInputStream("not an excel".getBytes());
        assertThrows(RuntimeException.class, () -> importer.parseExcel(bad));
    }

    @Test
    @DisplayName("空 sheet 应返回空列表")
    void shouldReturnEmptyListForEmptySheet() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            wb.createSheet("empty");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            List<ToolSchemaEntry> entries = importer.parseExcel(new ByteArrayInputStream(out.toByteArray()));
            assertTrue(entries.isEmpty());
        }
    }
}
