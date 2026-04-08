package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DirectiveRegistry 单元测试
 *
 * 覆盖关键路径：
 * - register / has / get / getAll / size / clear / unregister
 * - 以 toToolName() 作为 key 的注册与查找
 * - 同名覆盖注册
 */
@DisplayName("DirectiveRegistry - ClientTool 指令注册表")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private static DirectiveDescriptor descriptor(String tool, String namespace) {
        return new DirectiveDescriptor(tool, "down", "up", namespace, 1000, "1.0");
    }

    @Test
    @DisplayName("新注册表应为空")
    void shouldBeEmptyWhenCreated() {
        assertEquals(0, registry.size());
        assertFalse(registry.has("anything"));
        assertTrue(registry.getAll().isEmpty());
    }

    @Test
    @DisplayName("register 应以 toToolName 为 key 并可被 has/get 查到")
    void shouldRegisterAndLookup() {
        DirectiveDescriptor d = descriptor("GetPosition", "GeoInformation");

        registry.register(d);

        assertEquals(1, registry.size());
        assertTrue(registry.has("GetPosition"));
        Optional<DirectiveDescriptor> looked = registry.get("GetPosition");
        assertTrue(looked.isPresent());
        assertSame(d, looked.get());
    }

    @Test
    @DisplayName("get 未知工具应返回空 Optional")
    void shouldReturnEmptyForUnknown() {
        assertTrue(registry.get("no-such-tool").isEmpty());
        assertFalse(registry.has("no-such-tool"));
    }

    @Test
    @DisplayName("register 同名应覆盖旧 descriptor")
    void shouldOverrideOnDuplicateRegister() {
        DirectiveDescriptor first = descriptor("GetPosition", "GeoInformation");
        DirectiveDescriptor second = descriptor("GetPosition", "GeoInformation");

        registry.register(first);
        registry.register(second);

        assertEquals(1, registry.size());
        assertSame(second, registry.get("GetPosition").orElseThrow());
    }

    @Test
    @DisplayName("unregister 应从表中删除该工具")
    void shouldUnregister() {
        registry.register(descriptor("a", "X"));
        registry.register(descriptor("b", "X"));

        registry.unregister("a");

        assertEquals(1, registry.size());
        assertFalse(registry.has("a"));
        assertTrue(registry.has("b"));
    }

    @Test
    @DisplayName("unregister 未注册的工具应被静默忽略")
    void shouldSilentlyIgnoreUnregisterOfUnknown() {
        assertDoesNotThrow(() -> registry.unregister("no-such"));
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("getAll 应返回所有 descriptor 的新快照")
    void shouldReturnSnapshotFromGetAll() {
        registry.register(descriptor("a", "X"));
        registry.register(descriptor("b", "X"));

        List<DirectiveDescriptor> all = registry.getAll();

        assertEquals(2, all.size());
        // 修改快照不应影响原注册表
        all.clear();
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("clear 应清空注册表")
    void shouldClearAll() {
        registry.register(descriptor("a", "X"));
        registry.register(descriptor("b", "X"));

        registry.clear();

        assertEquals(0, registry.size());
        assertTrue(registry.getAll().isEmpty());
    }
}
