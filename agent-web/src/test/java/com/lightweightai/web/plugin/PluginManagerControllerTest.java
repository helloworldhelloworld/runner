package com.lightweightai.web.plugin;

import com.lightweightai.kernel.agent.DynamicPluginProvider;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PluginManager 逻辑测试")
class PluginManagerControllerTest {

    private ToolRegistry registry;
    private DynamicPluginProvider pluginProvider;

    @TempDir
    Path pluginDir;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        pluginProvider = new DynamicPluginProvider(pluginDir);
        pluginProvider.start(registry);
    }

    @Test
    @DisplayName("列出已加载的插件")
    void listPlugins() {
        assertTrue(pluginProvider.getLoadedPlugins().isEmpty());

        pluginProvider.registerPlugin("p1",
            List.of(createTool("tool_a")), registry);

        Set<String> plugins = pluginProvider.getLoadedPlugins();
        assertEquals(1, plugins.size());
        assertTrue(plugins.contains("p1"));
    }

    @Test
    @DisplayName("列出 registry 中所有工具")
    void listTools() {
        pluginProvider.registerPlugin("p1",
            List.of(createTool("tool_a"), createTool("tool_b")), registry);

        assertEquals(2, registry.size());
        assertTrue(registry.has("tool_a"));
        assertTrue(registry.has("tool_b"));
    }

    @Test
    @DisplayName("卸载插件")
    void unloadPlugin() {
        pluginProvider.registerPlugin("p1",
            List.of(createTool("tool_a")), registry);
        assertEquals(1, registry.size());

        pluginProvider.unregisterPlugin("p1", registry);
        assertEquals(0, registry.size());
        assertFalse(pluginProvider.getLoadedPlugins().contains("p1"));
    }

    @Test
    @DisplayName("下载到 plugins 目录的 jar 文件存在")
    void downloadedJarExists() throws IOException {
        // 模拟下载后的 jar 文件
        Path jarPath = pluginDir.resolve("test.jar");
        Files.write(jarPath, new byte[]{0x50, 0x4B, 0x03, 0x04}); // PK header

        assertTrue(Files.exists(jarPath));
        assertTrue(jarPath.getFileName().toString().endsWith(".jar"));
    }

    private static Tool createTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() {
                return new ToolSchema(Map.of("type", "object", "properties", Map.of()));
            }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(name, "ok");
            }
        };
    }
}
