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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolSchemaController - Tool Schema management API")
class ToolSchemaControllerTest {

    @Mock
    private ToolSchemaRepository toolSchemaRepository;

    private ToolSchemaController controller;

    @BeforeEach
    void setUp() {
        controller = new ToolSchemaController(toolSchemaRepository);
    }

    private ToolSchemaEntry createEntry(String id, String name, String category, String status) {
        ToolSchemaEntry entry = new ToolSchemaEntry();
        entry.setId(id);
        entry.setName(name);
        entry.setCategory(category);
        entry.setDescription("Description for " + name);
        entry.setInputSchemaJson("{\"type\":\"object\"}");
        entry.setStatus(status);
        return entry;
    }

    @Nested
    @DisplayName("GET /api/tool-schemas - list all schemas")
    class ListAll {

        @Test
        @DisplayName("returns all tool schemas as maps with 200 OK")
        void returnsAllSchemas() {
            List<ToolSchemaEntry> entries = List.of(
                    createEntry("id1", "GeoInfo.GetPosition", "Geo", "enabled"),
                    createEntry("id2", "Weather.GetForecast", "Weather", "disabled"));
            when(toolSchemaRepository.findAll()).thenReturn(entries);

            ResponseEntity<List<Map<String, Object>>> response = controller.listAll();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().size());
            assertEquals("GeoInfo.GetPosition", response.getBody().get(0).get("name"));
            assertEquals("Weather.GetForecast", response.getBody().get(1).get("name"));
        }

        @Test
        @DisplayName("returns empty list when no schemas exist")
        void emptyList() {
            when(toolSchemaRepository.findAll()).thenReturn(List.of());

            ResponseEntity<List<Map<String, Object>>> response = controller.listAll();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().isEmpty());
        }
    }

    @Nested
    @DisplayName("GET /api/tool-schemas/enabled - list enabled schemas")
    class ListEnabled {

        @Test
        @DisplayName("returns only enabled schemas")
        void returnsEnabledSchemas() {
            List<ToolSchemaEntry> entries = List.of(
                    createEntry("id1", "GeoInfo.GetPosition", "Geo", "enabled"));
            when(toolSchemaRepository.findEnabled()).thenReturn(entries);

            ResponseEntity<List<Map<String, Object>>> response = controller.listEnabled();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());
            assertEquals("enabled", response.getBody().get(0).get("status"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/tool-schemas/{id} - delete a schema")
    class DeleteSchema {

        @Test
        @DisplayName("successful deletion returns success=true")
        void successfulDeletion() {
            when(toolSchemaRepository.delete("id1")).thenReturn(true);

            ResponseEntity<Map<String, Object>> response = controller.delete("id1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(true, response.getBody().get("success"));
        }

        @Test
        @DisplayName("deletion of non-existent schema returns success=false")
        void nonExistentDeletion() {
            when(toolSchemaRepository.delete("no-such-id")).thenReturn(false);

            ResponseEntity<Map<String, Object>> response = controller.delete("no-such-id");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(false, response.getBody().get("success"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/tool-schemas - delete all schemas")
    class DeleteAll {

        @Test
        @DisplayName("returns count of deleted schemas")
        void deletesAllSchemas() {
            when(toolSchemaRepository.deleteAll()).thenReturn(5);

            ResponseEntity<Map<String, Object>> response = controller.deleteAll();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(5, response.getBody().get("deleted"));
        }

        @Test
        @DisplayName("returns 0 when no schemas to delete")
        void nothingToDelete() {
            when(toolSchemaRepository.deleteAll()).thenReturn(0);

            ResponseEntity<Map<String, Object>> response = controller.deleteAll();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(0, response.getBody().get("deleted"));
        }
    }
}
