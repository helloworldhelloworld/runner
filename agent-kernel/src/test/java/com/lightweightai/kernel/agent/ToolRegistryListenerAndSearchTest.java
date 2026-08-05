package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolRegistry - 监听器通知、search、enable/disable、registerFrom")
class ToolRegistryListenerAndSearchTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ==================== Listener tests ====================

    @Test
    @DisplayName("register 触发 onToolRegistered 回调并传递正确的 Tool")
    void registerFiresListener() {
        List<String> registered = new CopyOnWriteArrayList<>();
        registry.addListener(new ToolRegistryListener() {
            @Override public void onToolRegistered(Tool tool) { registered.add(tool.getName()); }
            @Override public void onToolUnregistered(String name) {}
            @Override public void onToolEnabled(String name) {}
            @Override public void onToolDisabled(String name) {}
        });

        registry.register(stubTool("alpha"));
        registry.register(stubTool("beta"));

        assertEquals(List.of("alpha", "beta"), registered);
    }

    @Test
    @DisplayName("unregister 触发 onToolUnregistered 回调")
    void unregisterFiresListener() {
        List<String> unregistered = new CopyOnWriteArrayList<>();
        registry.addListener(new ToolRegistryListener() {
            @Override public void onToolRegistered(Tool tool) {}
            @Override public void onToolUnregistered(String name) { unregistered.add(name); }
            @Override public void onToolEnabled(String name) {}
            @Override public void onToolDisabled(String name) {}
        });

        registry.register(stubTool("alpha"));
        registry.unregister("alpha");

        assertEquals(List.of("alpha"), unregistered);
    }

    @Test
    @DisplayName("unregister 不存在的工具不触发回调")
    void unregisterNonExistentDoesNotFire() {
        List<String> unregistered = new CopyOnWriteArrayList<>();
        registry.addListener(new ToolRegistryListener() {
            @Override public void onToolRegistered(Tool tool) {}
            @Override public void onToolUnregistered(String name) { unregistered.add(name); }
            @Override public void onToolEnabled(String name) {}
            @Override public void onToolDisabled(String name) {}
        });

        registry.unregister("ghost");
        assertTrue(unregistered.isEmpty());
    }

    @Test
    @DisplayName("disable/enable 触发对应回调")
    void disableEnableFireListeners() {
        List<String> disabled = new CopyOnWriteArrayList<>();
        List<String> enabled = new CopyOnWriteArrayList<>();
        registry.addListener(new ToolRegistryListener() {
            @Override public void onToolRegistered(Tool tool) {}
            @Override public void onToolUnregistered(String name) {}
            @Override public void onToolEnabled(String name) { enabled.add(name); }
            @Override public void onToolDisabled(String name) { disabled.add(name); }
        });

        registry.register(stubTool("tool1"));
        registry.disable("tool1");
        registry.enable("tool1");

        assertEquals(List.of("tool1"), disabled);
        assertEquals(List.of("tool1"), enabled);
    }

    @Test
    @DisplayName("removeListener 后不再收到通知")
    void removeListenerStopsNotification() {
        List<String> registered = new CopyOnWriteArrayList<>();
        ToolRegistryListener listener = new ToolRegistryListener() {
            @Override public void onToolRegistered(Tool tool) { registered.add(tool.getName()); }
            @Override public void onToolUnregistered(String name) {}
            @Override public void onToolEnabled(String name) {}
            @Override public void onToolDisabled(String name) {}
        };

        registry.addListener(listener);
        registry.register(stubTool("first"));
        registry.removeListener(listener);
        registry.register(stubTool("second"));

        assertEquals(List.of("first"), registered,
                "移除 listener 后不应再收到通知");
    }

    // ==================== Enable/Disable ====================

    @Test
    @DisplayName("disable 后 isEnabled 返回 false, getEnabled 不包含该工具")
    void disableAffectsIsEnabledAndGetEnabled() {
        registry.register(stubTool("a"));
        registry.register(stubTool("b"));

        registry.disable("a");

        assertFalse(registry.isEnabled("a"));
        assertTrue(registry.isEnabled("b"));
        assertEquals(1, registry.getEnabled().size());
        assertEquals("b", registry.getEnabled().get(0).getName());
    }

    @Test
    @DisplayName("disable 不存在的工具不抛异常")
    void disableNonExistentIsSafe() {
        assertDoesNotThrow(() -> registry.disable("nonexistent"));
    }

    @Test
    @DisplayName("enable 已启用的工具不触发回调")
    void enableAlreadyEnabledNoCallback() {
        List<String> enabled = new CopyOnWriteArrayList<>();
        registry.addListener(new ToolRegistryListener() {
            @Override public void onToolRegistered(Tool tool) {}
            @Override public void onToolUnregistered(String name) {}
            @Override public void onToolEnabled(String name) { enabled.add(name); }
            @Override public void onToolDisabled(String name) {}
        });

        registry.register(stubTool("tool"));
        registry.enable("tool");

        assertTrue(enabled.isEmpty(), "已启用工具再次 enable 不应触发回调");
    }

    @Test
    @DisplayName("enabledCount 正确反映 disabled 工具数量")
    void enabledCountAccurate() {
        registry.register(stubTool("a"));
        registry.register(stubTool("b"));
        registry.register(stubTool("c"));

        assertEquals(3, registry.enabledCount());
        registry.disable("a");
        assertEquals(2, registry.enabledCount());
        registry.disable("b");
        assertEquals(1, registry.enabledCount());
    }

    // ==================== Search ====================

    @Test
    @DisplayName("search 按 name 子串匹配")
    void searchByNameSubstring() {
        registry.register(stubToolWithDesc("read_file", "Read a file from disk"));
        registry.register(stubToolWithDesc("write_file", "Write content to file"));
        registry.register(stubToolWithDesc("search_web", "Search the web"));

        List<Tool> results = registry.search("file");
        assertEquals(2, results.size());
        assertTrue(results.stream().anyMatch(t -> "read_file".equals(t.getName())));
        assertTrue(results.stream().anyMatch(t -> "write_file".equals(t.getName())));
    }

    @Test
    @DisplayName("search 按 description 子串匹配")
    void searchByDescriptionSubstring() {
        registry.register(stubToolWithDesc("tool1", "Execute shell commands"));
        registry.register(stubToolWithDesc("tool2", "Read database records"));

        List<Tool> results = registry.search("shell");
        assertEquals(1, results.size());
        assertEquals("tool1", results.get(0).getName());
    }

    @Test
    @DisplayName("search 不区分大小写")
    void searchCaseInsensitive() {
        registry.register(stubToolWithDesc("MyTool", "Does Amazing Things"));

        assertEquals(1, registry.search("mytool").size());
        assertEquals(1, registry.search("AMAZING").size());
    }

    @Test
    @DisplayName("search null/blank 返回所有 enabled 工具")
    void searchNullOrBlankReturnsAll() {
        registry.register(stubTool("a"));
        registry.register(stubTool("b"));

        assertEquals(2, registry.search(null).size());
        assertEquals(2, registry.search("").size());
        assertEquals(2, registry.search("  ").size());
    }

    @Test
    @DisplayName("search 不返回 disabled 工具")
    void searchExcludesDisabled() {
        registry.register(stubTool("enabled_tool"));
        registry.register(stubTool("disabled_tool"));
        registry.disable("disabled_tool");

        List<Tool> results = registry.search("tool");
        assertEquals(1, results.size());
        assertEquals("enabled_tool", results.get(0).getName());
    }

    // ==================== registerFrom(ToolSource) ====================

    @Test
    @DisplayName("registerFrom 从 ToolSource 发现并注册工具")
    void registerFromToolSource() {
        ToolSource source = () -> List.of(stubTool("from_source_1"), stubTool("from_source_2"));

        int count = registry.registerFrom(source);

        assertEquals(2, count);
        assertTrue(registry.has("from_source_1"));
        assertTrue(registry.has("from_source_2"));
    }

    @Test
    @DisplayName("registerFrom: source 返回 null 不抛异常,返回 0")
    void registerFromNullToolsReturnsZero() {
        ToolSource source = () -> null;
        assertEquals(0, registry.registerFrom(source));
    }

    @Test
    @DisplayName("registerFrom: source 为 null 抛 NPE")
    void registerFromNullSourceThrows() {
        assertThrows(NullPointerException.class, () -> registry.registerFrom(null));
    }

    // ==================== clear ====================

    @Test
    @DisplayName("clear 后 size 为 0,所有工具不可见")
    void clearRemovesAll() {
        registry.register(stubTool("a"));
        registry.register(stubTool("b"));
        registry.disable("a");

        registry.clear();

        assertEquals(0, registry.size());
        assertEquals(0, registry.enabledCount());
        assertFalse(registry.has("a"));
    }

    // ==================== Helpers ====================

    private static Tool stubTool(String name) {
        return stubToolWithDesc(name, name + " tool");
    }

    private static Tool stubToolWithDesc(String name, String description) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }
}
