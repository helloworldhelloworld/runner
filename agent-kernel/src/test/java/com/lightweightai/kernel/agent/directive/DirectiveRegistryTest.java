package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DirectiveRegistry} — client tool directive registry.
 *
 * Covers: CRUD operations, concurrency-safe ConcurrentHashMap, getAll snapshot.
 */
@DisplayName("DirectiveRegistry - ClientTool 注册表")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor descriptor(String tool) {
        return new DirectiveDescriptor(tool, "Down" + tool, "Up" + tool, "TestNS", 5000, "1.0");
    }

    // ==================== register / has / get ====================

    @Nested
    @DisplayName("register, has, get")
    class RegisterGetTests {

        @Test
        @DisplayName("注册后 has 返回 true，get 返回 descriptor")
        void register_thenHasAndGet() {
            DirectiveDescriptor desc = descriptor("GetPosition");
            registry.register(desc);

            assertTrue(registry.has("GetPosition"));
            Optional<DirectiveDescriptor> found = registry.get("GetPosition");
            assertTrue(found.isPresent());
            assertEquals("TestNS", found.get().getNamespace());
            assertEquals("DownGetPosition", found.get().getDownAction());
        }

        @Test
        @DisplayName("未注册 — has 返回 false，get 返回 empty")
        void notRegistered_hasAndGetReturnFalse() {
            assertFalse(registry.has("nonexistent"));
            assertTrue(registry.get("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("重复注册同名 — 覆盖")
        void register_duplicate_overwrites() {
            registry.register(descriptor("ToolA"));
            DirectiveDescriptor updated = new DirectiveDescriptor("ToolA", "NewDown", "NewUp", "NS2", 10000, "2.0");
            registry.register(updated);

            assertEquals(1, registry.size());
            assertEquals("NewDown", registry.get("ToolA").get().getDownAction());
        }
    }

    // ==================== unregister ====================

    @Nested
    @DisplayName("unregister")
    class UnregisterTests {

        @Test
        @DisplayName("注销后 has 返回 false")
        void unregister_removesEntry() {
            registry.register(descriptor("ToolA"));
            registry.unregister("ToolA");

            assertFalse(registry.has("ToolA"));
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("注销不存在的 key — 无异常")
        void unregister_nonExistent_noException() {
            assertDoesNotThrow(() -> registry.unregister("ghost"));
        }
    }

    // ==================== getAll / size / clear ====================

    @Nested
    @DisplayName("getAll, size, clear")
    class CollectionTests {

        @Test
        @DisplayName("getAll 返回快照副本")
        void getAll_returnsSnapshot() {
            registry.register(descriptor("A"));
            registry.register(descriptor("B"));

            List<DirectiveDescriptor> all = registry.getAll();
            assertEquals(2, all.size());

            // Modifying snapshot doesn't affect registry
            all.clear();
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("size 反映当前注册数量")
        void size_reflectsState() {
            assertEquals(0, registry.size());
            registry.register(descriptor("A"));
            assertEquals(1, registry.size());
            registry.register(descriptor("B"));
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("clear 清空所有注册")
        void clear_removesAll() {
            registry.register(descriptor("A"));
            registry.register(descriptor("B"));

            registry.clear();

            assertEquals(0, registry.size());
            assertFalse(registry.has("A"));
        }
    }
}
