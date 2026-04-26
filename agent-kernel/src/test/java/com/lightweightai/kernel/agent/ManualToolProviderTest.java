package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider - 手动工具注册 Provider")
class ManualToolProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    private Tool stubTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Stub tool: " + name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(name + " executed");
            }
        };
    }

    @Nested
    @DisplayName("sourceType")
    class SourceTypeTests {

        @Test
        @DisplayName("sourceType 返回 'manual'")
        void returnsManual() {
            ManualToolProvider provider = new ManualToolProvider();
            assertEquals("manual", provider.sourceType());
        }
    }

    @Nested
    @DisplayName("start - 注册工具")
    class StartTests {

        @Test
        @DisplayName("start 后，addTool 添加的工具注册到 registry")
        void registersToolsOnStart() {
            ManualToolProvider provider = new ManualToolProvider()
                    .addTool(stubTool("calc"))
                    .addTool(stubTool("search"));

            provider.start(registry);

            assertTrue(registry.has("calc"));
            assertTrue(registry.has("search"));
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("注册的工具可通过 registry.get() 获取并执行")
        void registeredToolIsExecutable() {
            ManualToolProvider provider = new ManualToolProvider()
                    .addTool(stubTool("greet"));

            provider.start(registry);

            Tool retrieved = registry.get("greet").orElseThrow();
            ToolResult result = retrieved.execute(Map.of());
            assertEquals("greet executed", result.getContent());
        }

        @Test
        @DisplayName("空 provider start 不注册任何工具")
        void emptyProviderRegistersNothing() {
            ManualToolProvider provider = new ManualToolProvider();
            provider.start(registry);
            assertEquals(0, registry.size());
        }
    }

    @Nested
    @DisplayName("stop - 注销工具")
    class StopTests {

        @Test
        @DisplayName("stop 后，addTool 添加的工具从 registry 中注销")
        void unregistersToolsOnStop() {
            ManualToolProvider provider = new ManualToolProvider()
                    .addTool(stubTool("calc"))
                    .addTool(stubTool("search"));

            provider.start(registry);
            assertEquals(2, registry.size());

            provider.stop(registry);
            assertFalse(registry.has("calc"));
            assertFalse(registry.has("search"));
            assertEquals(0, registry.size());
        }
    }

    @Nested
    @DisplayName("fluent builder pattern")
    class FluentApiTests {

        @Test
        @DisplayName("addTool 返回 this 支持链式调用")
        void addToolReturnsSelf() {
            ManualToolProvider provider = new ManualToolProvider();
            ManualToolProvider returned = provider.addTool(stubTool("a"));
            assertSame(provider, returned);
        }

        @Test
        @DisplayName("addAnnotated 返回 this 支持链式调用")
        void addAnnotatedReturnsSelf() {
            ManualToolProvider provider = new ManualToolProvider();
            ManualToolProvider returned = provider.addAnnotated(new Object());
            assertSame(provider, returned);
        }
    }
}
