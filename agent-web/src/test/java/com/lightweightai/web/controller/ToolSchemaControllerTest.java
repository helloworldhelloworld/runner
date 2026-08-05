package com.lightweightai.web.controller;

import com.lightweightai.web.skillcreator.ToolSchemaEntry;
import com.lightweightai.web.skillcreator.ToolSchemaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ToolSchemaController 单元测试 — HTTP 接口逻辑关键路径
 */
class ToolSchemaControllerTest {

    private ToolSchemaRepository mockRepository;
    private ToolSchemaController controller;

    @BeforeEach
    void setUp() {
        mockRepository = mock(ToolSchemaRepository.class);
        controller = new ToolSchemaController(mockRepository);
    }

    // ==================== GET /api/tool-schemas ====================

    @Test
    @DisplayName("listAll 返回所有 Schema 的 toMap 列表")
    void shouldListAllSchemas() {
        ToolSchemaEntry entry = new ToolSchemaEntry();
        entry.setId("id-1");
        entry.setName("Tool1");
        entry.setDescription("desc");
        entry.setStatus("enabled");

        when(mockRepository.findAll()).thenReturn(List.of(entry));

        ResponseEntity<List<Map<String, Object>>> response = controller.listAll();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("Tool1", response.getBody().get(0).get("name"));
    }

    @Test
    @DisplayName("listAll 空数据返回空列表")
    void shouldReturnEmptyListWhenNoSchemas() {
        when(mockRepository.findAll()).thenReturn(List.of());

        ResponseEntity<List<Map<String, Object>>> response = controller.listAll();

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isEmpty());
    }

    // ==================== GET /api/tool-schemas/enabled ====================

    @Test
    @DisplayName("listEnabled 仅返回启用的 Schema")
    void shouldListOnlyEnabledSchemas() {
        ToolSchemaEntry entry = new ToolSchemaEntry();
        entry.setName("EnabledTool");
        entry.setStatus("enabled");

        when(mockRepository.findEnabled()).thenReturn(List.of(entry));

        ResponseEntity<List<Map<String, Object>>> response = controller.listEnabled();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals("EnabledTool", response.getBody().get(0).get("name"));
    }

    // ==================== DELETE /api/tool-schemas/{id} ====================

    @Test
    @DisplayName("delete 成功返回 success=true")
    void shouldDeleteAndReturnSuccess() {
        when(mockRepository.delete("id-1")).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.delete("id-1");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("success"));
        verify(mockRepository).delete("id-1");
    }

    @Test
    @DisplayName("delete 不存在的 ID 返回 success=false")
    void shouldReturnFalseWhenDeletingNonExistent() {
        when(mockRepository.delete("nonexistent")).thenReturn(false);

        ResponseEntity<Map<String, Object>> response = controller.delete("nonexistent");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(false, response.getBody().get("success"));
    }

    // ==================== DELETE /api/tool-schemas ====================

    @Test
    @DisplayName("deleteAll 返回删除数量")
    void shouldDeleteAllAndReturnCount() {
        when(mockRepository.deleteAll()).thenReturn(5);

        ResponseEntity<Map<String, Object>> response = controller.deleteAll();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(5, response.getBody().get("deleted"));
    }

    // ==================== POST /api/tool-schemas/import ====================

    @Test
    @DisplayName("importFromExcel 空文件返回 400 错误")
    void shouldRejectEmptyFile() {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.importFromExcel(mockFile);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("error"));
    }

    @Test
    @DisplayName("importFromExcel 非 xlsx 文件返回 400 错误")
    void shouldRejectNonXlsxFile() {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn("data.csv");

        ResponseEntity<Map<String, Object>> response = controller.importFromExcel(mockFile);

        assertEquals(400, response.getStatusCode().value());
        assertTrue(response.getBody().get("error").toString().contains(".xlsx"));
    }

    @Test
    @DisplayName("importFromExcel 文件名为 null 返回 400 错误")
    void shouldRejectNullFilename() {
        MultipartFile mockFile = mock(MultipartFile.class);
        when(mockFile.isEmpty()).thenReturn(false);
        when(mockFile.getOriginalFilename()).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = controller.importFromExcel(mockFile);

        assertEquals(400, response.getStatusCode().value());
    }
}
