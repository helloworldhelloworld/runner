package com.lightweightai.web.service;

import com.lightweightai.web.model.SharedClientTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SharedClientToolService - 共享客户端工具管理")
class SharedClientToolServiceTest {

    private SharedClientToolService service;

    @BeforeEach
    void setUp() {
        service = new SharedClientToolService();
    }

    @Test
    @DisplayName("初始列表为空")
    void shouldStartEmpty() {
        assertTrue(service.list().isEmpty());
    }

    @Test
    @DisplayName("upsert 新工具并读取")
    void shouldUpsertNewTool() {
        SharedClientTool payload = createTool("test-tool", "Test Tool", "A test tool");

        SharedClientTool result = service.upsert("test-tool", payload, "user-1");

        assertNotNull(result);
        assertEquals("test-tool", result.getKey());
        assertEquals("Test Tool", result.getName());
        assertEquals("A test tool", result.getDescription());
        assertEquals("user-1", result.getCreatedBy());
        assertEquals("user-1", result.getUpdatedBy());
        assertTrue(result.getCreatedAt() > 0);
        assertTrue(result.getUpdatedAt() > 0);
    }

    @Test
    @DisplayName("upsert 更新已有工具保留 createdBy/At")
    void shouldPreserveCreatedFieldsOnUpdate() throws InterruptedException {
        SharedClientTool payload1 = createTool("tool-1", "V1", "First version");
        SharedClientTool first = service.upsert("tool-1", payload1, "creator");

        long originalCreatedAt = first.getCreatedAt();
        String originalCreatedBy = first.getCreatedBy();

        Thread.sleep(10);

        SharedClientTool payload2 = createTool("tool-1", "V2", "Second version");
        SharedClientTool updated = service.upsert("tool-1", payload2, "updater");

        assertEquals(originalCreatedAt, updated.getCreatedAt());
        assertEquals(originalCreatedBy, updated.getCreatedBy());
        assertEquals("updater", updated.getUpdatedBy());
        assertEquals("V2", updated.getName());
        assertTrue(updated.getUpdatedAt() >= originalCreatedAt);
    }

    @Test
    @DisplayName("list 按 updatedAt 降序排列")
    void shouldListSortedByUpdateTimeDesc() throws InterruptedException {
        service.upsert("old", createTool("old", "Old", ""), "u1");
        Thread.sleep(10);
        service.upsert("new", createTool("new", "New", ""), "u1");

        List<SharedClientTool> list = service.list();
        assertEquals(2, list.size());
        assertEquals("new", list.get(0).getKey());
        assertEquals("old", list.get(1).getKey());
    }

    @Test
    @DisplayName("remove 已有工具返回 true")
    void shouldRemoveExistingTool() {
        service.upsert("tool-1", createTool("tool-1", "T", ""), "u1");
        assertTrue(service.remove("tool-1"));
        assertTrue(service.list().isEmpty());
    }

    @Test
    @DisplayName("remove 不存在的工具返回 false")
    void shouldReturnFalseForNonExistentRemove() {
        assertFalse(service.remove("nonexistent"));
    }

    @Test
    @DisplayName("upsert 设置所有字段")
    void shouldSetAllFields() {
        SharedClientTool payload = new SharedClientTool();
        payload.setNamespace("ns");
        payload.setName("Tool");
        payload.setDescription("Desc");
        payload.setOwner("owner");
        payload.setVersion("1.0");
        payload.setInputSchema(Map.of("type", "object"));
        payload.setEnabled(true);
        payload.setMockResponse(Map.of("result", "mock"));

        SharedClientTool result = service.upsert("k1", payload, "actor");

        assertEquals("ns", result.getNamespace());
        assertEquals("Tool", result.getName());
        assertEquals("Desc", result.getDescription());
        assertEquals("owner", result.getOwner());
        assertEquals("1.0", result.getVersion());
        assertEquals(Map.of("type", "object"), result.getInputSchema());
        assertTrue(result.isEnabled());
        assertEquals(Map.of("result", "mock"), result.getMockResponse());
    }

    private SharedClientTool createTool(String key, String name, String description) {
        SharedClientTool tool = new SharedClientTool();
        tool.setKey(key);
        tool.setName(name);
        tool.setDescription(description);
        tool.setEnabled(true);
        return tool;
    }
}
