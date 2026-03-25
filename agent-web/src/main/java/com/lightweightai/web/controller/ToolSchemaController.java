package com.lightweightai.web.controller;

import com.lightweightai.web.skillcreator.ToolSchemaEntry;
import com.lightweightai.web.skillcreator.ToolSchemaExcelImporter;
import com.lightweightai.web.skillcreator.ToolSchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具 Schema 管理 REST API
 *
 * 提供 Excel 导入和 CRUD 接口，用于管理 Skill Creator 中的候选工具。
 */
@RestController
@RequestMapping("/api/tool-schemas")
public class ToolSchemaController {

    private static final Logger logger = LoggerFactory.getLogger(ToolSchemaController.class);

    private final ToolSchemaRepository toolSchemaRepository;
    private final ToolSchemaExcelImporter excelImporter = new ToolSchemaExcelImporter();

    public ToolSchemaController(ToolSchemaRepository toolSchemaRepository) {
        this.toolSchemaRepository = toolSchemaRepository;
    }

    /**
     * 从 Excel 导入工具 Schema
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importFromExcel(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "文件不能为空"));
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".xlsx")) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 .xlsx 格式的 Excel 文件"));
        }

        try {
            List<ToolSchemaEntry> entries = excelImporter.parseExcel(file.getInputStream());
            int saved = toolSchemaRepository.saveAll(entries);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("parsed", entries.size());
            result.put("saved", saved);
            result.put("message", String.format("成功导入 %d 个工具 Schema", saved));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Excel import failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", "导入失败: " + e.getMessage()));
        }
    }

    /**
     * 获取所有工具 Schema
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAll() {
        List<ToolSchemaEntry> entries = toolSchemaRepository.findAll();
        return ResponseEntity.ok(entries.stream().map(ToolSchemaEntry::toMap).toList());
    }

    /**
     * 获取启用的工具 Schema
     */
    @GetMapping("/enabled")
    public ResponseEntity<List<Map<String, Object>>> listEnabled() {
        List<ToolSchemaEntry> entries = toolSchemaRepository.findEnabled();
        return ResponseEntity.ok(entries.stream().map(ToolSchemaEntry::toMap).toList());
    }

    /**
     * 删除工具 Schema
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable("id") String id) {
        boolean ok = toolSchemaRepository.delete(id);
        return ResponseEntity.ok(Map.of("success", ok));
    }

    /**
     * 清空所有工具 Schema
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteAll() {
        int count = toolSchemaRepository.deleteAll();
        return ResponseEntity.ok(Map.of("deleted", count));
    }
}
