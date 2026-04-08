package com.lightweightai.web.service;

import com.lightweightai.web.model.SharedClientTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SharedClientToolService 单元测试
 *
 * 覆盖关键路径：
 * - upsert 创建新工具（设置 createdBy/createdAt/updatedBy/updatedAt）
 * - upsert 更新已有工具（保留 createdBy/createdAt，更新 updatedBy/updatedAt）
 * - list 按 updatedAt 倒序返回
 * - remove 命中/未命中
 */
@DisplayName("SharedClientToolService - 共享端工具元数据管理")
class SharedClientToolServiceTest {

    private SharedClientToolService service;

    @BeforeEach
    void setUp() {
        service = new SharedClientToolService();
    }

    private static SharedClientTool newPayload(String name, boolean enabled) {
        SharedClientTool t = new SharedClientTool();
        t.setNamespace("Camera");
        t.setName(name);
        t.setDescription("desc-" + name);
        t.setOwner("owner");
        t.setVersion("1.0.0");
        t.setInputSchema(Map.of("type", "object"));
        t.setEnabled(enabled);
        t.setMockResponse(Map.of("ok", true));
        return t;
    }

    @Test
    @DisplayName("upsert 新建时应设置 createdBy/createdAt 与 updatedBy/updatedAt")
    void shouldCreateWithAllTimestamps() {
        long before = System.currentTimeMillis();
        SharedClientTool payload = newPayload("take_photo", true);

        SharedClientTool saved = service.upsert("Camera.take_photo", payload, "alice");

        assertEquals("Camera.take_photo", saved.getKey());
        assertEquals("take_photo", saved.getName());
        assertEquals("alice", saved.getCreatedBy());
        assertEquals("alice", saved.getUpdatedBy());
        assertTrue(saved.getCreatedAt() >= before);
        assertTrue(saved.getUpdatedAt() >= before);
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
        assertTrue(saved.isEnabled());
    }

    @Test
    @DisplayName("upsert 更新已有应保留 createdBy/createdAt,覆盖 updatedBy/updatedAt 及字段")
    void shouldPreserveCreatedMetaOnUpdate() throws InterruptedException {
        SharedClientTool initial = service.upsert("Camera.take_photo", newPayload("take_photo", false), "alice");
        long originalCreatedAt = initial.getCreatedAt();

        // 保证 updatedAt 至少推进 1ms
        Thread.sleep(2);

        SharedClientTool updatePayload = newPayload("take_photo", true);
        updatePayload.setDescription("updated-desc");
        SharedClientTool updated = service.upsert("Camera.take_photo", updatePayload, "bob");

        assertEquals("alice", updated.getCreatedBy(), "createdBy should be preserved");
        assertEquals(originalCreatedAt, updated.getCreatedAt(), "createdAt should be preserved");
        assertEquals("bob", updated.getUpdatedBy());
        assertTrue(updated.getUpdatedAt() > originalCreatedAt, "updatedAt should advance");
        assertEquals("updated-desc", updated.getDescription());
        assertTrue(updated.isEnabled(), "enabled flag should be updated");
    }

    @Test
    @DisplayName("list 应按 updatedAt 倒序返回")
    void shouldListSortedByUpdatedAtDesc() throws InterruptedException {
        service.upsert("k1", newPayload("t1", true), "u");
        Thread.sleep(2);
        service.upsert("k2", newPayload("t2", true), "u");
        Thread.sleep(2);
        service.upsert("k3", newPayload("t3", true), "u");

        List<SharedClientTool> list = service.list();

        assertEquals(3, list.size());
        assertEquals("k3", list.get(0).getKey());
        assertEquals("k2", list.get(1).getKey());
        assertEquals("k1", list.get(2).getKey());
    }

    @Test
    @DisplayName("list 空仓库应返回空列表")
    void shouldListEmpty() {
        assertTrue(service.list().isEmpty());
    }

    @Test
    @DisplayName("remove 命中应返回 true 并从 list 中消失")
    void shouldRemoveExisting() {
        service.upsert("k1", newPayload("t1", true), "u");

        assertTrue(service.remove("k1"));
        assertTrue(service.list().isEmpty());
    }

    @Test
    @DisplayName("remove 未命中应返回 false")
    void shouldReturnFalseWhenRemovingMissing() {
        assertFalse(service.remove("no-such-key"));
    }

    @Test
    @DisplayName("upsert 后再 upsert 相同 key 不应改变 list size")
    void shouldNotDuplicateOnUpsertSameKey() {
        service.upsert("k1", newPayload("t1", true), "u");
        service.upsert("k1", newPayload("t1", true), "u");
        assertEquals(1, service.list().size());
    }
}
