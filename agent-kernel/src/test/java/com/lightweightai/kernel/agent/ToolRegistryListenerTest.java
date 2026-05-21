package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolRegistryListener — tool lifecycle event notifications")
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
    @DisplayName("register fires onToolRegistered")
    void registerFiresEvent() {
        registry.register(simpleTool("echo"));

        assertEquals(1, listener.registered.size());
        assertEquals("echo", listener.registered.get(0).getName());
    }

    @Test
    @DisplayName("unregister fires onToolUnregistered")
    void unregisterFiresEvent() {
        registry.register(simpleTool("echo"));
        registry.unregister("echo");

        assertEquals(1, listener.unregistered.size());
        assertEquals("echo", listener.unregistered.get(0));
    }

    @Test
    @DisplayName("unregister nonexistent tool does not fire event")
    void unregisterNonexistentNoEvent() {
        registry.unregister("ghost");

        assertTrue(listener.unregistered.isEmpty());
    }

    @Test
    @DisplayName("disable fires onToolDisabled")
    void disableFiresEvent() {
        registry.register(simpleTool("echo"));
        registry.disable("echo");

        assertEquals(1, listener.disabled.size());
        assertEquals("echo", listener.disabled.get(0));
    }

    @Test
    @DisplayName("enable fires onToolEnabled")
    void enableFiresEvent() {
        registry.register(simpleTool("echo"));
        registry.disable("echo");
        registry.enable("echo");

        assertEquals(1, listener.enabled.size());
        assertEquals("echo", listener.enabled.get(0));
    }

    @Test
    @DisplayName("enable already-enabled tool does not fire event")
    void enableAlreadyEnabledNoEvent() {
        registry.register(simpleTool("echo"));
        registry.enable("echo");

        assertTrue(listener.enabled.isEmpty());
    }

    @Test
    @DisplayName("removing listener stops event delivery")
    void removeListenerStopsDelivery() {
        registry.removeListener(listener);
        registry.register(simpleTool("echo"));

        assertTrue(listener.registered.isEmpty());
    }

    @Test
    @DisplayName("multiple listeners all receive events")
    void multipleListeners() {
        RecordingListener second = new RecordingListener();
        registry.addListener(second);

        registry.register(simpleTool("echo"));

        assertEquals(1, listener.registered.size());
        assertEquals(1, second.registered.size());
    }

    @Test
    @DisplayName("registerAll fires events for each tool")
    void registerAllFiresForEach() {
        registry.registerAll(List.of(simpleTool("a"), simpleTool("b"), simpleTool("c")));

        assertEquals(3, listener.registered.size());
    }

    // ==================== Test helpers ====================

    private static Tool simpleTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " desc"; }
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
