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
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolSchemaController - tool schema CRUD endpoints")
class ToolSchemaControllerTest {

    @Mock private ToolSchemaRepository toolSchemaRepository;

    private ToolSchemaController controller;

    @BeforeEach
    void setUp() {
        controller = new ToolSchemaController(toolSchemaRepository);
    }

    @Nested
    @DisplayName("POST /api/tool-schemas/import")
    class ImportExcel {

        @Test
        @DisplayName("rejects empty file")
        void rejectsEmptyFile() {
            MockMultipartFile empty = new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

            ResponseEntity<Map<String, Object>> response = controller.importFromExcel(empty);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getBody().get("error").toString().contains("空"));
        }

        @Test
        @DisplayName("rejects non-xlsx file")
        void rejectsNonXlsx() {
            MockMultipartFile csv = new MockMultipartFile("file", "test.csv",
                    "text/csv", "data".getBytes());

            ResponseEntity<Map<String, Object>> response = controller.importFromExcel(csv);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getBody().get("error").toString().contains(".xlsx"));
        }
    }

    @Nested
    @DisplayName("GET /api/tool-schemas")
    class ListAll {

        @Test
        @DisplayName("returns all tool schemas as maps")
        void returnsAll() {
            ToolSchemaEntry entry = new ToolSchemaEntry();
            entry.setId("1");
            entry.setName("GetPosition");
            entry.setDescription("Get device position");
            entry.setCategory("Geo");
            when(toolSchemaRepository.findAll()).thenReturn(List.of(entry));

            ResponseEntity<List<Map<String, Object>>> response = controller.listAll();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());
            assertEquals("GetPosition", response.getBody().get(0).get("name"));
        }
    }

    @Nested
    @DisplayName("GET /api/tool-schemas/enabled")
    class ListEnabled {

        @Test
        @DisplayName("returns only enabled schemas")
        void returnsEnabled() {
            when(toolSchemaRepository.findEnabled()).thenReturn(List.of());

            ResponseEntity<List<Map<String, Object>>> response = controller.listEnabled();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getBody().isEmpty());
        }
    }

    @Nested
    @DisplayName("DELETE /api/tool-schemas/{id}")
    class DeleteOne {

        @Test
        @DisplayName("returns success=true when deleted")
        void deletesSuccessfully() {
            when(toolSchemaRepository.delete("id-1")).thenReturn(true);

            ResponseEntity<Map<String, Object>> response = controller.delete("id-1");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(true, response.getBody().get("success"));
        }

        @Test
        @DisplayName("returns success=false when not found")
        void notFound() {
            when(toolSchemaRepository.delete("ghost")).thenReturn(false);

            ResponseEntity<Map<String, Object>> response = controller.delete("ghost");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(false, response.getBody().get("success"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/tool-schemas")
    class DeleteAll {

        @Test
        @DisplayName("returns count of deleted entries")
        void returnsDeletedCount() {
            when(toolSchemaRepository.deleteAll()).thenReturn(5);

            ResponseEntity<Map<String, Object>> response = controller.deleteAll();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(5, response.getBody().get("deleted"));
        }
    }
}
