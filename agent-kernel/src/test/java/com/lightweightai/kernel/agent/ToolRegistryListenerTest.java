package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolRegistryListener — lifecycle callbacks on ToolRegistry mutations")
class ToolRegistryListenerTest {

    private ToolRegistry registry;
    private RecordingListener listener;

    @BeforeEach
    void setup() {
        registry = new ToolRegistry();
        listener = new RecordingListener();
        registry.addListener(listener);
    }

    @Nested
    @DisplayName("onToolRegistered")
    class Registered {

        @Test
        @DisplayName("fires with the registered Tool instance")
        void firesOnRegister() {
            Tool tool = stubTool("echo");
            registry.register(tool);

            assertEquals(1, listener.registered.size());
            assertSame(tool, listener.registered.get(0));
        }

        @Test
        @DisplayName("fires for each tool in batch registration")
        void firesForBatch() {
            registry.registerAll(List.of(stubTool("a"), stubTool("b"), stubTool("c")));
            assertEquals(3, listener.registered.size());
        }
    }

    @Nested
    @DisplayName("onToolUnregistered")
    class Unregistered {

        @Test
        @DisplayName("fires with the tool name when unregistered")
        void firesOnUnregister() {
            registry.register(stubTool("echo"));
            listener.clear();

            registry.unregister("echo");

            assertEquals(1, listener.unregistered.size());
            assertEquals("echo", listener.unregistered.get(0));
        }

        @Test
        @DisplayName("does not fire when unregistering a non-existent tool")
        void noFireForNonExistent() {
            registry.unregister("nonexistent");
            assertTrue(listener.unregistered.isEmpty());
        }
    }

    @Nested
    @DisplayName("onToolEnabled / onToolDisabled")
    class EnableDisable {

        @Test
        @DisplayName("fires onToolDisabled when disabling an existing tool")
        void firesOnDisable() {
            registry.register(stubTool("echo"));
            listener.clear();

            registry.disable("echo");

            assertEquals(1, listener.disabled.size());
            assertEquals("echo", listener.disabled.get(0));
        }

        @Test
        @DisplayName("fires onToolEnabled when re-enabling a disabled tool")
        void firesOnEnable() {
            registry.register(stubTool("echo"));
            registry.disable("echo");
            listener.clear();

            registry.enable("echo");

            assertEquals(1, listener.enabled.size());
            assertEquals("echo", listener.enabled.get(0));
        }

        @Test
        @DisplayName("does not fire enable if tool was not disabled")
        void noFireEnableIfNotDisabled() {
            registry.register(stubTool("echo"));
            listener.clear();

            registry.enable("echo");
            assertTrue(listener.enabled.isEmpty());
        }
    }

    @Nested
    @DisplayName("listener management")
    class Management {

        @Test
        @DisplayName("removeListener stops receiving events")
        void removeStopsEvents() {
            registry.removeListener(listener);
            registry.register(stubTool("echo"));
            assertTrue(listener.registered.isEmpty());
        }

        @Test
        @DisplayName("multiple listeners all receive events")
        void multipleListeners() {
            RecordingListener second = new RecordingListener();
            registry.addListener(second);

            registry.register(stubTool("echo"));

            assertEquals(1, listener.registered.size());
            assertEquals(1, second.registered.size());
        }
    }

    private static Tool stubTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() {
                return new ToolSchema(Map.of("type", "object", "properties", Map.of()));
            }
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

        void clear() {
            registered.clear(); unregistered.clear(); enabled.clear(); disabled.clear();
        }
    }
}
