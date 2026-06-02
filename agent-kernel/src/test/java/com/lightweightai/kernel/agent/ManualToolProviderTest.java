package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider — lifecycle-managed tool registration")
class ManualToolProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ==================== Test fixtures ====================

    static class EchoTool implements Tool {
        @Override public String getName() { return "echo"; }
        @Override public String getDescription() { return "Echo input"; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success(String.valueOf(args.get("text")));
        }
    }

    static class HelloTool implements Tool {
        @Override public String getName() { return "hello"; }
        @Override public String getDescription() { return "Say hello"; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("Hello!");
        }
    }

    static class AnnotatedGreeter {
        @ToolFunction(name = "greet", description = "Greet by name")
        public String greet(@ToolParam(name = "name", required = true) String name) {
            return "Hi, " + name + "!";
        }
    }

    // ==================== Tests ====================

    @Nested
    @DisplayName("Tool registration via start()")
    class StartLifecycle {

        @Test
        @DisplayName("start() registers Tool instances into ToolRegistry")
        void registersTool() {
            ManualToolProvider provider = new ManualToolProvider()
                    .addTool(new EchoTool());

            provider.start(registry);

            assertTrue(registry.has("echo"));
            assertEquals(1, registry.size());
        }

        @Test
        @DisplayName("start() registers multiple tools")
        void registersMultiple() {
            ManualToolProvider provider = new ManualToolProvider()
                    .addTool(new EchoTool())
                    .addTool(new HelloTool());

            provider.start(registry);

            assertTrue(registry.has("echo"));
            assertTrue(registry.has("hello"));
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("start() registers annotated objects")
        void registersAnnotated() {
            ManualToolProvider provider = new ManualToolProvider()
                    .addAnnotated(new AnnotatedGreeter());

            provider.start(registry);

            assertTrue(registry.has("greet"));
            ToolResult result = registry.get("greet").orElseThrow()
                    .execute(Map.of("name", "World"));
            assertEquals("Hi, World!", result.getContent());
        }

        @Test
        @DisplayName("start() registers both Tool instances and annotated objects")
        void registersMixed() {
            ManualToolProvider provider = new ManualToolProvider()
                    .addTool(new EchoTool())
                    .addAnnotated(new AnnotatedGreeter());

            provider.start(registry);

            assertTrue(registry.has("echo"));
            assertTrue(registry.has("greet"));
            assertEquals(2, registry.size());
        }
    }

    @Nested
    @DisplayName("Tool unregistration via stop()")
    class StopLifecycle {

        @Test
        @DisplayName("stop() unregisters Tool instances from ToolRegistry")
        void unregistersTool() {
            ManualToolProvider provider = new ManualToolProvider()
                    .addTool(new EchoTool())
                    .addTool(new HelloTool());

            provider.start(registry);
            assertEquals(2, registry.size());

            provider.stop(registry);
            assertFalse(registry.has("echo"));
            assertFalse(registry.has("hello"));
        }

        @Test
        @DisplayName("stop() does not affect tools registered by other providers")
        void doesNotAffectOthers() {
            ManualToolProvider providerA = new ManualToolProvider().addTool(new EchoTool());
            ManualToolProvider providerB = new ManualToolProvider().addTool(new HelloTool());

            providerA.start(registry);
            providerB.start(registry);
            assertEquals(2, registry.size());

            providerA.stop(registry);
            assertFalse(registry.has("echo"));
            assertTrue(registry.has("hello"), "providerB's tool should survive");
        }
    }

    @Nested
    @DisplayName("Source type")
    class SourceType {

        @Test
        @DisplayName("sourceType returns 'manual'")
        void sourceType() {
            assertEquals("manual", new ManualToolProvider().sourceType());
        }
    }

    @Nested
    @DisplayName("Fluent API")
    class FluentApi {

        @Test
        @DisplayName("addTool returns this for chaining")
        void chainAddTool() {
            ManualToolProvider provider = new ManualToolProvider();
            ManualToolProvider returned = provider.addTool(new EchoTool());
            assertSame(provider, returned);
        }

        @Test
        @DisplayName("addAnnotated returns this for chaining")
        void chainAddAnnotated() {
            ManualToolProvider provider = new ManualToolProvider();
            ManualToolProvider returned = provider.addAnnotated(new AnnotatedGreeter());
            assertSame(provider, returned);
        }
    }

    @Nested
    @DisplayName("Payload transmission — registered tool executes correctly")
    class PayloadTransmission {

        @Test
        @DisplayName("tool registered via ManualToolProvider produces correct result through registry")
        void executesThroughRegistry() {
            ManualToolProvider provider = new ManualToolProvider()
                    .addTool(new EchoTool());

            provider.start(registry);

            Tool tool = registry.get("echo").orElseThrow();
            ToolResult result = tool.execute(Map.of("text", "payload"));

            assertFalse(result.isError());
            assertEquals("payload", result.getContent());
        }
    }
}
