package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DynamicPluginProvider 动态插件加载测试")
class DynamicPluginProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    @DisplayName("sourceType 返回 dynamic-plugin")
    void sourceType(@TempDir Path pluginDir) {
        DynamicPluginProvider provider = new DynamicPluginProvider(pluginDir);
        assertEquals("dynamic-plugin", provider.sourceType());
    }

    @Test
    @DisplayName("start 扫描目录中已有的 jar")
    void startScansExistingJars(@TempDir Path pluginDir) throws IOException {
        // 空目录 — 不会报错
        DynamicPluginProvider provider = new DynamicPluginProvider(pluginDir);
        provider.start(registry);
        // 空目录无工具
        assertEquals(0, registry.size());
        provider.stop(registry);
    }

    @Test
    @DisplayName("不存在的目录自动创建")
    void autoCreateDirectory(@TempDir Path baseDir) {
        Path pluginDir = baseDir.resolve("plugins");
        assertFalse(Files.exists(pluginDir));

        DynamicPluginProvider provider = new DynamicPluginProvider(pluginDir);
        provider.start(registry);

        assertTrue(Files.exists(pluginDir));
        provider.stop(registry);
    }

    @Test
    @DisplayName("stop 后不再监听")
    void stopCleansUp(@TempDir Path pluginDir) {
        DynamicPluginProvider provider = new DynamicPluginProvider(pluginDir);
        provider.start(registry);
        assertDoesNotThrow(() -> provider.stop(registry));
        // 多次 stop 不报错
        assertDoesNotThrow(() -> provider.stop(registry));
    }

    @Test
    @DisplayName("registerPlugin / unregisterPlugin 手动管理")
    void manualPluginManagement(@TempDir Path pluginDir) {
        DynamicPluginProvider provider = new DynamicPluginProvider(pluginDir);
        provider.start(registry);

        // 手动注册一个 plugin（模拟 jar 加载后的工具注册）
        Tool dummyTool = createDummyTool("dynamic_tool_1");
        provider.registerPlugin("test-plugin", java.util.List.of(dummyTool), registry);

        assertTrue(registry.has("dynamic_tool_1"));
        assertEquals(1, registry.size());

        // 卸载 plugin
        provider.unregisterPlugin("test-plugin", registry);
        assertFalse(registry.has("dynamic_tool_1"));
        assertEquals(0, registry.size());

        provider.stop(registry);
    }

    @Test
    @DisplayName("多个 plugin 独立管理")
    void multiplePlugins(@TempDir Path pluginDir) {
        DynamicPluginProvider provider = new DynamicPluginProvider(pluginDir);
        provider.start(registry);

        provider.registerPlugin("plugin-a",
            java.util.List.of(createDummyTool("tool_a1"), createDummyTool("tool_a2")), registry);
        provider.registerPlugin("plugin-b",
            java.util.List.of(createDummyTool("tool_b1")), registry);

        assertEquals(3, registry.size());

        // 卸载 plugin-a，plugin-b 不受影响
        provider.unregisterPlugin("plugin-a", registry);
        assertFalse(registry.has("tool_a1"));
        assertFalse(registry.has("tool_a2"));
        assertTrue(registry.has("tool_b1"));
        assertEquals(1, registry.size());

        provider.stop(registry);
    }

    @Test
    @DisplayName("stop 注销所有动态加载的工具")
    void stopUnregistersAllPlugins(@TempDir Path pluginDir) {
        DynamicPluginProvider provider = new DynamicPluginProvider(pluginDir);
        provider.start(registry);

        provider.registerPlugin("p1", java.util.List.of(createDummyTool("t1")), registry);
        provider.registerPlugin("p2", java.util.List.of(createDummyTool("t2")), registry);
        assertEquals(2, registry.size());

        provider.stop(registry);
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("getLoadedPlugins 返回已加载的 plugin 名称")
    void getLoadedPlugins(@TempDir Path pluginDir) {
        DynamicPluginProvider provider = new DynamicPluginProvider(pluginDir);
        provider.start(registry);

        assertTrue(provider.getLoadedPlugins().isEmpty());

        provider.registerPlugin("my-plugin", java.util.List.of(createDummyTool("t")), registry);
        assertEquals(1, provider.getLoadedPlugins().size());
        assertTrue(provider.getLoadedPlugins().contains("my-plugin"));

        provider.stop(registry);
    }

    @Test
    @DisplayName("工具名冲突时跳过，不覆盖已有工具")
    void conflictDetection_skipsExisting(@TempDir Path pluginDir) {
        // 先注册一个工具到 registry
        registry.register(createDummyTool("existing_tool"));
        assertEquals(1, registry.size());

        DynamicPluginProvider provider = new DynamicPluginProvider(pluginDir);
        provider.start(registry);

        // 插件尝试注册同名工具 + 新工具
        provider.registerPlugin("conflict-plugin",
            java.util.List.of(
                createDummyTool("existing_tool"),  // 冲突，应跳过
                createDummyTool("new_tool")        // 不冲突，应注册
            ), registry);

        assertEquals(2, registry.size()); // existing + new
        assertTrue(registry.has("existing_tool"));
        assertTrue(registry.has("new_tool"));

        // 卸载插件只删除实际注册的工具
        provider.unregisterPlugin("conflict-plugin", registry);
        assertTrue(registry.has("existing_tool"), "Should not remove pre-existing tool");
        assertFalse(registry.has("new_tool"));
        assertEquals(1, registry.size());

        provider.stop(registry);
    }

    @Test
    @DisplayName("热更新：卸载旧版 + 加载新版")
    void hotReload(@TempDir Path pluginDir) {
        DynamicPluginProvider provider = new DynamicPluginProvider(pluginDir);
        provider.start(registry);

        // 模拟第一次加载
        provider.registerPlugin("my-plugin.jar",
            java.util.List.of(createDummyTool("v1_tool")), registry);
        assertTrue(registry.has("v1_tool"));
        assertEquals(1, registry.size());

        // 模拟热更新：先卸载再注册新版
        provider.unregisterPlugin("my-plugin.jar", registry);
        assertFalse(registry.has("v1_tool"));

        provider.registerPlugin("my-plugin.jar",
            java.util.List.of(createDummyTool("v2_tool")), registry);
        assertFalse(registry.has("v1_tool"));
        assertTrue(registry.has("v2_tool"));
        assertEquals(1, registry.size());

        provider.stop(registry);
    }

    // ==================== Helper ====================

    private static Tool createDummyTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Dynamic tool: " + name; }
            @Override public ToolSchema getSchema() {
                return new ToolSchema(Map.of("type", "object", "properties", Map.of()));
            }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(name, "ok");
            }
        };
    }
}
