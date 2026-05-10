package com.lightweightai.web.controller;

import com.lightweightai.web.skillcreator.ToolSchemaEntry;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolSchemaController — REST API for tool schema CRUD")
class ToolSchemaControllerTest {

    @Mock
    private ToolSchemaRepository repository;

    private ToolSchemaController controller;

    @BeforeEach
    void setUp() {
        controller = new ToolSchemaController(repository);
    }

    @Nested
    @DisplayName("GET /api/tool-schemas — listAll")
    class ListAllTests {

        @Test
        @DisplayName("returns all tool schemas as maps")
        void returnsAllSchemas() {
            ToolSchemaEntry entry = new ToolSchemaEntry();
            entry.setId("id-1");
            entry.setName("weather");
            entry.setDescription("Get weather");
            entry.setCategory("api");

            when(repository.findAll()).thenReturn(List.of(entry));

            ResponseEntity<List<Map<String, Object>>> response = controller.listAll();

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());
            assertEquals("weather", response.getBody().get(0).get("name"));
        }

        @Test
        @DisplayName("returns empty list when no schemas exist")
        void returnsEmptyList() {
            when(repository.findAll()).thenReturn(List.of());

            ResponseEntity<List<Map<String, Object>>> response = controller.listAll();

            assertEquals(200, response.getStatusCode().value());
            assertTrue(response.getBody().isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/tool-schemas/enabled — listEnabled")
    class ListEnabledTests {

        @Test
        @DisplayName("returns only enabled schemas")
        void returnsEnabledOnly() {
            ToolSchemaEntry entry = new ToolSchemaEntry();
            entry.setId("id-2");
            entry.setName("calc");
            entry.setDescription("Calculator");
            entry.setStatus("enabled");

            when(repository.findEnabled()).thenReturn(List.of(entry));

            ResponseEntity<List<Map<String, Object>>> response = controller.listEnabled();

            assertEquals(200, response.getStatusCode().value());
            assertEquals(1, response.getBody().size());
        }
    }

    @Nested
    @DisplayName("DELETE /api/tool-schemas/{id}")
    class DeleteTests {

        @Test
        @DisplayName("delete existing schema returns success=true")
        void deleteExisting() {
            when(repository.delete("id-1")).thenReturn(true);

            ResponseEntity<Map<String, Object>> response = controller.delete("id-1");

            assertEquals(200, response.getStatusCode().value());
            assertEquals(true, response.getBody().get("success"));
            verify(repository).delete("id-1");
        }

        @Test
        @DisplayName("delete non-existing schema returns success=false")
        void deleteNonExisting() {
            when(repository.delete("nope")).thenReturn(false);

            ResponseEntity<Map<String, Object>> response = controller.delete("nope");

            assertEquals(200, response.getStatusCode().value());
            assertEquals(false, response.getBody().get("success"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/tool-schemas — deleteAll")
    class DeleteAllTests {

        @Test
        @DisplayName("deleteAll returns count of deleted entries")
        void deleteAllReturnsCount() {
            when(repository.deleteAll()).thenReturn(5);

            ResponseEntity<Map<String, Object>> response = controller.deleteAll();

            assertEquals(200, response.getStatusCode().value());
            assertEquals(5, response.getBody().get("deleted"));
        }
    }
}
