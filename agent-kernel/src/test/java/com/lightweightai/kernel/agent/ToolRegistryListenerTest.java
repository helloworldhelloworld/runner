package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolRegistryListener — lifecycle event notifications")
class ToolRegistryListenerTest {

    private ToolRegistry registry;
    private RecordingListener listener;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        listener = new RecordingListener();
        registry.addListener(listener);
    }

    @Test
    @DisplayName("onToolRegistered fires when tool is added")
    void registeredEventFires() {
        Tool tool = simpleTool("search", "Web search");
        registry.register(tool);

        assertEquals(1, listener.registered.size());
        assertEquals("search", listener.registered.get(0).getName());
    }

    @Test
    @DisplayName("onToolUnregistered fires when tool is removed")
    void unregisteredEventFires() {
        registry.register(simpleTool("search", "Search"));
        registry.unregister("search");

        assertEquals(1, listener.unregistered.size());
        assertEquals("search", listener.unregistered.get(0));
    }

    @Test
    @DisplayName("onToolUnregistered does not fire for nonexistent tool")
    void unregisterNonexistentDoesNotFire() {
        registry.unregister("nonexistent");

        assertTrue(listener.unregistered.isEmpty());
    }

    @Test
    @DisplayName("onToolDisabled fires when tool is disabled")
    void disabledEventFires() {
        registry.register(simpleTool("search", "Search"));
        registry.disable("search");

        assertEquals(1, listener.disabled.size());
        assertEquals("search", listener.disabled.get(0));
        assertFalse(registry.isEnabled("search"));
    }

    @Test
    @DisplayName("onToolEnabled fires when tool is re-enabled")
    void enabledEventFires() {
        registry.register(simpleTool("search", "Search"));
        registry.disable("search");
        registry.enable("search");

        assertEquals(1, listener.enabled.size());
        assertEquals("search", listener.enabled.get(0));
        assertTrue(registry.isEnabled("search"));
    }

    @Test
    @DisplayName("listener can be removed and stops receiving events")
    void removedListenerStopsReceiving() {
        registry.register(simpleTool("a", "A"));
        assertEquals(1, listener.registered.size());

        registry.removeListener(listener);
        registry.register(simpleTool("b", "B"));

        assertEquals(1, listener.registered.size());
    }

    @Test
    @DisplayName("multiple listeners all receive events")
    void multipleListenersReceiveEvents() {
        RecordingListener listener2 = new RecordingListener();
        registry.addListener(listener2);

        registry.register(simpleTool("tool", "Tool"));

        assertEquals(1, listener.registered.size());
        assertEquals(1, listener2.registered.size());
    }

    @Test
    @DisplayName("disable on nonexistent tool does not fire")
    void disableNonexistentDoesNotFire() {
        registry.disable("nonexistent");
        assertTrue(listener.disabled.isEmpty());
    }

    @Test
    @DisplayName("enable on non-disabled tool does not fire")
    void enableNonDisabledDoesNotFire() {
        registry.register(simpleTool("tool", "Tool"));
        registry.enable("tool");
        assertTrue(listener.enabled.isEmpty());
    }

    private static Tool simpleTool(String name, String description) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    private static class RecordingListener implements ToolRegistryListener {
        final List<Tool> registered = new ArrayList<>();
        final List<String> unregistered = new ArrayList<>();
        final List<String> enabled = new ArrayList<>();
        final List<String> disabled = new ArrayList<>();

        @Override public void onToolRegistered(Tool tool) { registered.add(tool); }
        @Override public void onToolUnregistered(String name) { unregistered.add(name); }
        @Override public void onToolEnabled(String name) { enabled.add(name); }
        @Override public void onToolDisabled(String name) { disabled.add(name); }
    }
}
