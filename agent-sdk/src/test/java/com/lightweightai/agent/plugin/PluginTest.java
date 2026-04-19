package com.lightweightai.agent.plugin;

import com.lightweightai.kernel.plugin.PluginFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Plugin - 插件抽象类")
class PluginTest {

    @Nested
    @DisplayName("函数注册")
    class FunctionRegistrationTests {

        @Test
        @DisplayName("注册简单函数")
        void shouldRegisterSimpleFunction() {
            Plugin plugin = new TestPlugin("math") {
                {
                    registerFunction("add", args -> "result");
                }
            };

            assertEquals(1, plugin.getFunctions().size());
            assertNotNull(plugin.getFunction("add"));
        }

        @Test
        @DisplayName("注册类型安全函数")
        void shouldRegisterTypedFunction() {
            Plugin plugin = new TestPlugin("math") {
                {
                    registerFunction("calculate", "Perform calculation",
                            Map.class, args -> "42");
                }
            };

            PluginFunction fn = plugin.getFunction("calculate");
            assertNotNull(fn);
        }

        @Test
        @DisplayName("注册多个函数")
        void shouldRegisterMultipleFunctions() {
            Plugin plugin = new TestPlugin("math") {
                {
                    registerFunction("add", args -> "sum");
                    registerFunction("subtract", args -> "diff");
                    registerFunction("multiply", args -> "product");
                }
            };

            assertEquals(3, plugin.getFunctions().size());
            assertNotNull(plugin.getFunction("add"));
            assertNotNull(plugin.getFunction("subtract"));
            assertNotNull(plugin.getFunction("multiply"));
        }

        @Test
        @DisplayName("重复注册同名函数抛出异常")
        void shouldRejectDuplicateFunction() {
            assertThrows(IllegalArgumentException.class, () -> {
                new TestPlugin("dup") {
                    {
                        registerFunction("add", args -> "1");
                        registerFunction("add", args -> "2");
                    }
                };
            });
        }

        @Test
        @DisplayName("获取不存在的函数返回 null")
        void shouldReturnNullForUnknownFunction() {
            Plugin plugin = new TestPlugin("empty");
            assertNull(plugin.getFunction("nonexistent"));
        }
    }

    @Nested
    @DisplayName("getFunctions 不可变性")
    class ImmutabilityTests {

        @Test
        @DisplayName("getFunctions 返回不可修改的 map")
        void getFunctionsShouldBeUnmodifiable() {
            Plugin plugin = new TestPlugin("test") {
                {
                    registerFunction("fn", args -> "ok");
                }
            };

            assertThrows(UnsupportedOperationException.class,
                    () -> plugin.getFunctions().put("hack", null));
        }
    }

    @Nested
    @DisplayName("toClaudeTools 转换")
    class ClaudeToolConversionTests {

        @Test
        @DisplayName("无函数时返回空列表")
        void shouldReturnEmptyListWhenNoFunctions() {
            Plugin plugin = new TestPlugin("empty");
            List<Map<String, Object>> tools = plugin.toClaudeTools();
            assertTrue(tools.isEmpty());
        }

        @Test
        @DisplayName("有函数时返回对应数量的 tool 定义")
        void shouldReturnToolDefinitionsForEachFunction() {
            Plugin plugin = new TestPlugin("math") {
                {
                    registerFunction("add", args -> "sum");
                    registerFunction("sub", args -> "diff");
                }
            };

            List<Map<String, Object>> tools = plugin.toClaudeTools();
            assertEquals(2, tools.size());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString 包含插件名和函数数量")
        void shouldIncludeNameAndFunctionCount() {
            Plugin plugin = new TestPlugin("weather") {
                {
                    registerFunction("get_temp", args -> "25");
                }
            };

            String str = plugin.toString();
            assertTrue(str.contains("weather"));
            assertTrue(str.contains("1"));
        }
    }

    static class TestPlugin extends Plugin {
        private final String name;

        TestPlugin(String name) {
            this.name = name;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return "Test plugin: " + name; }
    }
}
