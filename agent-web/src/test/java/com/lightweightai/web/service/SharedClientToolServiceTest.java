package com.lightweightai.web.service;

import com.lightweightai.web.model.SharedClientTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SharedClientToolService - 客户端工具 CRUD")
class SharedClientToolServiceTest {

    private SharedClientToolService service;

    @BeforeEach
    void setUp() {
        service = new SharedClientToolService();
    }

    private SharedClientTool createPayload(String name, String description) {
        SharedClientTool tool = new SharedClientTool();
        tool.setName(name);
        tool.setDescription(description);
        tool.setNamespace("test-ns");
        tool.setOwner("owner-1");
        tool.setVersion("1.0");
        tool.setEnabled(true);
        return tool;
    }

    @Nested
    @DisplayName("创建和更新")
    class Upsert {

        @Test
        @DisplayName("新建工具设置创建者和时间戳")
        void shouldSetCreatorAndTimestamps() {
            SharedClientTool payload = createPayload("tool-a", "desc-a");
            SharedClientTool result = service.upsert("key-a", payload, "actor-1");

            assertEquals("key-a", result.getKey());
            assertEquals("tool-a", result.getName());
            assertEquals("actor-1", result.getCreatedBy());
            assertEquals("actor-1", result.getUpdatedBy());
            assertTrue(result.getCreatedAt() > 0);
            assertTrue(result.getUpdatedAt() > 0);
            assertEquals(result.getCreatedAt(), result.getUpdatedAt());
        }

        @Test
        @DisplayName("更新工具保留创建者信息")
        void shouldPreserveCreatorOnUpdate() throws InterruptedException {
            SharedClientTool payload1 = createPayload("tool-a", "v1");
            service.upsert("key-a", payload1, "creator");

            Thread.sleep(10); // 确保时间戳不同

            SharedClientTool payload2 = createPayload("tool-a-updated", "v2");
            SharedClientTool result = service.upsert("key-a", payload2, "updater");

            assertEquals("creator", result.getCreatedBy());
            assertEquals("updater", result.getUpdatedBy());
            assertEquals("tool-a-updated", result.getName());
            assertTrue(result.getUpdatedAt() >= result.getCreatedAt());
        }

        @Test
        @DisplayName("更新所有字段")
        void shouldUpdateAllFields() {
            SharedClientTool payload = createPayload("original", "orig-desc");
            service.upsert("key-1", payload, "actor");

            SharedClientTool update = createPayload("updated", "new-desc");
            update.setNamespace("new-ns");
            update.setOwner("new-owner");
            update.setVersion("2.0");
            update.setEnabled(false);

            SharedClientTool result = service.upsert("key-1", update, "actor-2");

            assertEquals("updated", result.getName());
            assertEquals("new-desc", result.getDescription());
            assertEquals("new-ns", result.getNamespace());
            assertEquals("new-owner", result.getOwner());
            assertEquals("2.0", result.getVersion());
            assertFalse(result.isEnabled());
        }
    }

    @Nested
    @DisplayName("列表")
    class ListTools {

        @Test
        @DisplayName("空列表")
        void shouldReturnEmptyList() {
            List<SharedClientTool> tools = service.list();
            assertTrue(tools.isEmpty());
        }

        @Test
        @DisplayName("按更新时间倒序排列")
        void shouldSortByUpdatedAtDescending() throws InterruptedException {
            service.upsert("key-1", createPayload("tool-1", "d1"), "actor");
            Thread.sleep(10);
            service.upsert("key-2", createPayload("tool-2", "d2"), "actor");
            Thread.sleep(10);
            service.upsert("key-3", createPayload("tool-3", "d3"), "actor");

            List<SharedClientTool> tools = service.list();
            assertEquals(3, tools.size());
            assertEquals("key-3", tools.get(0).getKey());
            assertEquals("key-2", tools.get(1).getKey());
            assertEquals("key-1", tools.get(2).getKey());
        }
    }

    @Nested
    @DisplayName("删除")
    class Remove {

        @Test
        @DisplayName("删除存在的工具返回true")
        void shouldReturnTrueWhenRemovingExistingTool() {
            service.upsert("key-1", createPayload("tool-1", "d1"), "actor");
            assertTrue(service.remove("key-1"));
        }

        @Test
        @DisplayName("删除不存在的工具返回false")
        void shouldReturnFalseWhenRemovingNonExistentTool() {
            assertFalse(service.remove("nonexistent"));
        }

        @Test
        @DisplayName("删除后列表中不再包含该工具")
        void shouldNotAppearInListAfterRemoval() {
            service.upsert("key-1", createPayload("tool-1", "d1"), "actor");
            service.remove("key-1");

            assertTrue(service.list().isEmpty());
        }
    }
}
