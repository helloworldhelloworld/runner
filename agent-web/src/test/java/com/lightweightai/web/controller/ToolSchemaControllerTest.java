package com.lightweightai.web.controller;

import com.lightweightai.web.skillcreator.ToolSchemaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ToolSchemaController - 工具 Schema 管理")
@ExtendWith(MockitoExtension.class)
class ToolSchemaControllerTest {

    @Mock
    private ToolSchemaRepository toolSchemaRepository;

    private ToolSchemaController controller;

    @BeforeEach
    void setUp() {
        controller = new ToolSchemaController(toolSchemaRepository);
    }

    // ==================== GET /api/tool-schemas ====================

    @Test
    @DisplayName("列出所有 Schema")
    void shouldListAllSchemas() {
        when(toolSchemaRepository.findAll()).thenReturn(List.of());

        ResponseEntity<List<Map<String, Object>>> response = controller.listAll();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    // ==================== GET /api/tool-schemas/enabled ====================

    @Test
    @DisplayName("列出启用的 Schema")
    void shouldListEnabledSchemas() {
        when(toolSchemaRepository.findEnabled()).thenReturn(List.of());

        ResponseEntity<List<Map<String, Object>>> response = controller.listEnabled();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
    }

    // ==================== DELETE /api/tool-schemas/{id} ====================

    @Nested
    @DisplayName("删除操作")
    class DeleteOps {

        @Test
        @DisplayName("删除单个 Schema")
        void shouldDeleteById() {
            when(toolSchemaRepository.delete("schema-1")).thenReturn(true);

            ResponseEntity<Map<String, Object>> response = controller.delete("schema-1");

            assertEquals(200, response.getStatusCode().value());
            assertEquals(true, response.getBody().get("success"));
        }

        @Test
        @DisplayName("删除不存在的 Schema 返回 false")
        void shouldReturnFalseForMissing() {
            when(toolSchemaRepository.delete("nonexistent")).thenReturn(false);

            ResponseEntity<Map<String, Object>> response = controller.delete("nonexistent");

            assertEquals(false, response.getBody().get("success"));
        }

        @Test
        @DisplayName("清空所有 Schema")
        void shouldDeleteAll() {
            when(toolSchemaRepository.deleteAll()).thenReturn(5);

            ResponseEntity<Map<String, Object>> response = controller.deleteAll();

            assertEquals(200, response.getStatusCode().value());
            assertEquals(5, response.getBody().get("deleted"));
        }
    }
}
