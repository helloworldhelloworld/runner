package com.lightweightai.web.service;

import com.lightweightai.web.model.SharedClientTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SharedClientToolService - in-memory shared tool storage")
class SharedClientToolServiceTest {

    private SharedClientToolService service;

    @BeforeEach
    void setUp() {
        service = new SharedClientToolService();
    }

    // ==================== list ====================

    @Test
    @DisplayName("list returns empty when no tools registered")
    void listReturnsEmptyInitially() {
        List<SharedClientTool> result = service.list();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("list returns tools sorted by updatedAt descending")
    void listReturnsSortedByUpdatedAtDesc() throws InterruptedException {
        SharedClientTool tool1 = createTool("tool-1", "Tool One");
        SharedClientTool tool2 = createTool("tool-2", "Tool Two");

        service.upsert("key1", tool1, "user1");
        // Small delay to ensure different updatedAt timestamps
        Thread.sleep(10);
        service.upsert("key2", tool2, "user2");

        List<SharedClientTool> result = service.list();
        assertEquals(2, result.size());
        assertEquals("key2", result.get(0).getKey());
        assertEquals("key1", result.get(1).getKey());
    }

    // ==================== upsert ====================

    @Test
    @DisplayName("upsert creates new tool with createdBy and timestamps")
    void upsertCreatesNewTool() {
        SharedClientTool payload = createTool("myTool", "A tool");
        SharedClientTool saved = service.upsert("key1", payload, "actor1");

        assertNotNull(saved);
        assertEquals("key1", saved.getKey());
        assertEquals("myTool", saved.getName());
        assertEquals("A tool", saved.getDescription());
        assertEquals("actor1", saved.getCreatedBy());
        assertEquals("actor1", saved.getUpdatedBy());
        assertTrue(saved.getCreatedAt() > 0);
        assertTrue(saved.getUpdatedAt() > 0);
    }

    @Test
    @DisplayName("upsert updates existing tool preserving createdBy")
    void upsertUpdatesExistingTool() {
        SharedClientTool payload1 = createTool("toolV1", "Version 1");
        service.upsert("key1", payload1, "creator");

        SharedClientTool payload2 = createTool("toolV2", "Version 2");
        SharedClientTool updated = service.upsert("key1", payload2, "updater");

        assertEquals("key1", updated.getKey());
        assertEquals("toolV2", updated.getName());
        assertEquals("Version 2", updated.getDescription());
        assertEquals("creator", updated.getCreatedBy());
        assertEquals("updater", updated.getUpdatedBy());
    }

    @Test
    @DisplayName("upsert copies all fields from payload")
    void upsertCopiesAllFields() {
        SharedClientTool payload = new SharedClientTool();
        payload.setName("test");
        payload.setNamespace("ns");
        payload.setDescription("desc");
        payload.setOwner("owner1");
        payload.setVersion("1.0");
        payload.setEnabled(true);

        SharedClientTool saved = service.upsert("k1", payload, "actor");

        assertEquals("ns", saved.getNamespace());
        assertEquals("owner1", saved.getOwner());
        assertEquals("1.0", saved.getVersion());
        assertTrue(saved.isEnabled());
    }

    // ==================== remove ====================

    @Test
    @DisplayName("remove returns true when tool exists")
    void removeReturnsTrueForExistingTool() {
        service.upsert("key1", createTool("tool", "desc"), "actor");
        assertTrue(service.remove("key1"));
        assertTrue(service.list().isEmpty());
    }

    @Test
    @DisplayName("remove returns false when tool does not exist")
    void removeReturnsFalseForNonExistentTool() {
        assertFalse(service.remove("nonexistent"));
    }

    // ==================== helpers ====================

    private SharedClientTool createTool(String name, String description) {
        SharedClientTool tool = new SharedClientTool();
        tool.setName(name);
        tool.setDescription(description);
        return tool;
    }
}
