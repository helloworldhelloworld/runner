package com.lightweightai.web.skillcreator;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 Excel 文件导入工具 Schema
 *
 * Excel 格式要求（列顺序）：
 * | name | description | category | inputSchema (JSON) | outputSchema (JSON, 可选) |
 *
 * 第一行为表头，从第二行开始读取数据。
 */
public class ToolSchemaExcelImporter {

    private static final Logger logger = LoggerFactory.getLogger(ToolSchemaExcelImporter.class);

    /**
     * 从 Excel InputStream 解析工具 Schema 列表
     *
     * @param inputStream Excel 文件输入流（.xlsx）
     * @return 解析出的工具 Schema 列表
     */
    public List<ToolSchemaEntry> parseExcel(InputStream inputStream) {
        List<ToolSchemaEntry> entries = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                logger.warn("Excel file has no sheets");
                return entries;
            }

            // 解析表头，确定列映射
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                logger.warn("Excel file has no header row");
                return entries;
            }

            int[] columnMap = resolveColumnMap(headerRow);

            // 从第二行开始读取数据
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    ToolSchemaEntry entry = parseRow(row, columnMap);
                    if (entry != null && entry.getName() != null && !entry.getName().isBlank()) {
                        entries.add(entry);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to parse row {}: {}", i + 1, e.getMessage());
                }
            }

            logger.info("Parsed {} tool schemas from Excel", entries.size());
        } catch (Exception e) {
            logger.error("Failed to parse Excel file: {}", e.getMessage(), e);
            throw new RuntimeException("Excel 文件解析失败: " + e.getMessage(), e);
        }

        return entries;
    }

    /**
     * 解析表头，返回列索引映射
     * 索引: [name, description, category, inputSchema, outputSchema]
     */
    private int[] resolveColumnMap(Row headerRow) {
        int[] map = {0, 1, 2, 3, 4}; // 默认列顺序

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String header = getCellString(headerRow.getCell(i)).toLowerCase().trim();
            switch (header) {
                case "name", "名称", "工具名", "工具名称", "tool_name", "toolname" -> map[0] = i;
                case "description", "描述", "说明", "desc" -> map[1] = i;
                case "category", "分类", "类别", "类型" -> map[2] = i;
                case "inputschema", "input_schema", "输入schema", "schema", "参数", "input" -> map[3] = i;
                case "outputschema", "output_schema", "输出schema", "output", "返回" -> map[4] = i;
            }
        }

        return map;
    }

    private ToolSchemaEntry parseRow(Row row, int[] columnMap) {
        String name = getCellString(row.getCell(columnMap[0]));
        if (name.isBlank()) return null;

        ToolSchemaEntry entry = new ToolSchemaEntry();
        entry.setName(name);
        entry.setDescription(getCellString(row.getCell(columnMap[1])));
        entry.setCategory(getCellString(row.getCell(columnMap[2])));
        entry.setInputSchemaJson(getCellString(row.getCell(columnMap[3])));

        if (columnMap[4] < row.getLastCellNum()) {
            entry.setOutputSchemaJson(getCellString(row.getCell(columnMap[4])));
        }

        entry.setStatus("enabled");
        return entry;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }
}
