package com.lightweightai.web.controller;

import com.lightweightai.web.skillcreator.ToolSchemaEntry;
import com.lightweightai.web.skillcreator.ToolSchemaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolSchemaController - tool schema management REST API")
class ToolSchemaControllerTest {

    @Mock
    private ToolSchemaRepository toolSchemaRepository;

    private ToolSchemaController controller;

    @BeforeEach
    void setUp() {
        controller = new ToolSchemaController(toolSchemaRepository);
    }

    // ==================== listAll ====================

    @Test
    @DisplayName("listAll returns all tool schemas")
    void listAllReturnsAllSchemas() {
        ToolSchemaEntry entry = new ToolSchemaEntry();
        entry.setId("1");
        entry.setName("tool1");
        when(toolSchemaRepository.findAll()).thenReturn(List.of(entry));

        ResponseEntity<List<Map<String, Object>>> result = controller.listAll();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
    }

    @Test
    @DisplayName("listAll returns empty list when no schemas")
    void listAllReturnsEmptyWhenNone() {
        when(toolSchemaRepository.findAll()).thenReturn(List.of());

        ResponseEntity<List<Map<String, Object>>> result = controller.listAll();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertTrue(result.getBody().isEmpty());
    }

    // ==================== listEnabled ====================

    @Test
    @DisplayName("listEnabled returns only enabled schemas")
    void listEnabledReturnsEnabledOnly() {
        ToolSchemaEntry entry = new ToolSchemaEntry();
        entry.setId("1");
        entry.setName("enabled-tool");
        entry.setStatus("enabled");
        when(toolSchemaRepository.findEnabled()).thenReturn(List.of(entry));

        ResponseEntity<List<Map<String, Object>>> result = controller.listEnabled();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(1, result.getBody().size());
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete returns success when schema exists")
    void deleteReturnsSuccessWhenExists() {
        when(toolSchemaRepository.delete("1")).thenReturn(true);

        ResponseEntity<Map<String, Object>> result = controller.delete("1");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(true, result.getBody().get("success"));
    }

    @Test
    @DisplayName("delete returns false when schema not found")
    void deleteReturnsFalseWhenNotFound() {
        when(toolSchemaRepository.delete("999")).thenReturn(false);

        ResponseEntity<Map<String, Object>> result = controller.delete("999");

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(false, result.getBody().get("success"));
    }

    // ==================== deleteAll ====================

    @Test
    @DisplayName("deleteAll returns count of deleted schemas")
    void deleteAllReturnsCount() {
        when(toolSchemaRepository.deleteAll()).thenReturn(5);

        ResponseEntity<Map<String, Object>> result = controller.deleteAll();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(5, result.getBody().get("deleted"));
    }
}
