package com.lightweightai.web.service;

import com.lightweightai.web.model.SharedClientTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SharedClientToolService - 共享客户端工具管理")
class SharedClientToolServiceTest {

    private SharedClientToolService service;

    @BeforeEach
    void setUp() {
        service = new SharedClientToolService();
    }

    private SharedClientTool createPayload(String name, String namespace) {
        SharedClientTool tool = new SharedClientTool();
        tool.setName(name);
        tool.setNamespace(namespace);
        tool.setDescription("Test tool: " + name);
        tool.setVersion("1.0.0");
        tool.setEnabled(true);
        return tool;
    }

    @Nested
    @DisplayName("Upsert 操作")
    class UpsertTests {

        @Test
        @DisplayName("新建工具设置 createdBy 和 createdAt")
        void shouldSetCreatedFieldsOnInsert() {
            SharedClientTool payload = createPayload("weather", "tools");
            SharedClientTool result = service.upsert("weather-key", payload, "admin");

            assertEquals("weather-key", result.getKey());
            assertEquals("weather", result.getName());
            assertEquals("tools", result.getNamespace());
            assertEquals("admin", result.getCreatedBy());
            assertEquals("admin", result.getUpdatedBy());
            assertTrue(result.getCreatedAt() > 0);
            assertTrue(result.getUpdatedAt() > 0);
        }

        @Test
        @DisplayName("更新工具保留 createdBy/createdAt，更新 updatedBy/updatedAt")
        void shouldPreserveCreatedFieldsOnUpdate() throws InterruptedException {
            SharedClientTool payload = createPayload("weather", "tools");
            SharedClientTool created = service.upsert("key1", payload, "creator");
            long originalCreatedAt = created.getCreatedAt();

            Thread.sleep(10);

            SharedClientTool updatePayload = createPayload("weather-v2", "tools");
            SharedClientTool updated = service.upsert("key1", updatePayload, "updater");

            assertEquals("creator", updated.getCreatedBy());
            assertEquals(originalCreatedAt, updated.getCreatedAt());
            assertEquals("updater", updated.getUpdatedBy());
            assertTrue(updated.getUpdatedAt() > originalCreatedAt);
            assertEquals("weather-v2", updated.getName());
        }

        @Test
        @DisplayName("Upsert 返回更新后的工具对象")
        void shouldReturnUpdatedTool() {
            SharedClientTool payload = createPayload("calc", "math");
            payload.setEnabled(false);

            SharedClientTool result = service.upsert("calc-key", payload, "user1");
            assertEquals("calc", result.getName());
            assertFalse(result.isEnabled());
        }
    }

    @Nested
    @DisplayName("List 操作")
    class ListTests {

        @Test
        @DisplayName("空列表")
        void shouldReturnEmptyListWhenNoTools() {
            assertTrue(service.list().isEmpty());
        }

        @Test
        @DisplayName("按 updatedAt 降序排列")
        void shouldSortByUpdatedAtDescending() throws InterruptedException {
            service.upsert("a", createPayload("A", "ns"), "user");
            Thread.sleep(10);
            service.upsert("b", createPayload("B", "ns"), "user");
            Thread.sleep(10);
            service.upsert("c", createPayload("C", "ns"), "user");

            List<SharedClientTool> list = service.list();
            assertEquals(3, list.size());
            assertEquals("C", list.get(0).getName());
            assertEquals("B", list.get(1).getName());
            assertEquals("A", list.get(2).getName());
        }

        @Test
        @DisplayName("更新后重新排序")
        void shouldReorderAfterUpdate() throws InterruptedException {
            service.upsert("a", createPayload("A", "ns"), "user");
            Thread.sleep(10);
            service.upsert("b", createPayload("B", "ns"), "user");
            Thread.sleep(10);

            service.upsert("a", createPayload("A-updated", "ns"), "user");

            List<SharedClientTool> list = service.list();
            assertEquals("A-updated", list.get(0).getName());
        }
    }

    @Nested
    @DisplayName("Remove 操作")
    class RemoveTests {

        @Test
        @DisplayName("删除已存在的工具返回 true")
        void shouldReturnTrueWhenRemoved() {
            service.upsert("key1", createPayload("tool1", "ns"), "user");
            assertTrue(service.remove("key1"));
        }

        @Test
        @DisplayName("删除不存在的工具返回 false")
        void shouldReturnFalseWhenNotFound() {
            assertFalse(service.remove("nonexistent"));
        }

        @Test
        @DisplayName("删除后从列表中消失")
        void shouldRemoveFromList() {
            service.upsert("key1", createPayload("tool1", "ns"), "user");
            assertEquals(1, service.list().size());

            service.remove("key1");
            assertTrue(service.list().isEmpty());
        }
    }
}
