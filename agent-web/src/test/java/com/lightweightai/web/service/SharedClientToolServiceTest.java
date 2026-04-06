package com.lightweightai.web.service;

import com.lightweightai.web.model.SharedClientTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

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
    @DisplayName("upsert 创建新工具")
    void shouldCreateNewTool() {
        SharedClientTool payload = createPayload("Camera", "TakePhoto", "拍照");
        SharedClientTool result = service.upsert("camera.take_photo", payload, "user1");

        assertEquals("camera.take_photo", result.getKey());
        assertEquals("Camera", result.getNamespace());
        assertEquals("TakePhoto", result.getName());
        assertEquals("user1", result.getCreatedBy());
        assertEquals("user1", result.getUpdatedBy());
        assertTrue(result.getCreatedAt() > 0);
    }

    @Test
    @DisplayName("upsert 更新已有工具")
    void shouldUpdateExistingTool() {
        SharedClientTool payload1 = createPayload("Camera", "TakePhoto", "拍照 v1");
        service.upsert("camera.take_photo", payload1, "user1");

        SharedClientTool payload2 = createPayload("Camera", "TakePhoto", "拍照 v2");
        SharedClientTool result = service.upsert("camera.take_photo", payload2, "user2");

        assertEquals("拍照 v2", result.getDescription());
        assertEquals("user1", result.getCreatedBy()); // 创建者不变
        assertEquals("user2", result.getUpdatedBy()); // 更新者改变
    }

    @Test
    @DisplayName("list 返回所有工具，按更新时间倒序")
    void shouldListByUpdatedAtDesc() throws InterruptedException {
        service.upsert("tool_a", createPayload("A", "ToolA", "第一个"), "u1");
        Thread.sleep(5); // 确保时间差
        service.upsert("tool_b", createPayload("B", "ToolB", "第二个"), "u1");

        List<SharedClientTool> list = service.list();
        assertEquals(2, list.size());
        assertEquals("tool_b", list.get(0).getKey()); // 最近更新的在前
    }

    @Test
    @DisplayName("remove 删除已有工具返回 true")
    void shouldRemoveExisting() {
        service.upsert("tool_a", createPayload("A", "ToolA", "desc"), "u1");
        assertTrue(service.remove("tool_a"));
        assertTrue(service.list().isEmpty());
    }

    @Test
    @DisplayName("remove 不存在的工具返回 false")
    void shouldReturnFalseForMissing() {
        assertFalse(service.remove("nonexistent"));
    }

    @Test
    @DisplayName("upsert 保留 enabled 和 inputSchema")
    void shouldPreserveAllFields() {
        SharedClientTool payload = createPayload("GPS", "GetLoc", "获取位置");
        payload.setEnabled(true);
        payload.setInputSchema(Map.of("type", "object"));
        payload.setVersion("2.0");

        SharedClientTool result = service.upsert("gps.get_loc", payload, "u1");

        assertTrue(result.isEnabled());
        assertNotNull(result.getInputSchema());
        assertEquals("2.0", result.getVersion());
    }

    private SharedClientTool createPayload(String namespace, String name, String desc) {
        SharedClientTool tool = new SharedClientTool();
        tool.setNamespace(namespace);
        tool.setName(name);
        tool.setDescription(desc);
        return tool;
    }
}
