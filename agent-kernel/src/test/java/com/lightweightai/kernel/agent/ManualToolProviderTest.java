package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ManualToolProvider - 手动注册工具 Provider")
class ManualToolProviderTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    // ==================== 内部测试工具 ====================

    private static class SimpleTool implements Tool {
        private final String name;

        SimpleTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "test tool " + name;
        }

        @Override
        public ToolSchema getSchema() {
            return ToolSchema.empty();
        }

        @Override
        public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("result from " + name);
        }
    }

    /**
     * 带注解的工具类，用于测试 addAnnotated 路径
     */
    public static class AnnotatedGreeter {
        @ToolFunction(name = "greet", description = "Say hello to someone")
        public String greet(
                @ToolParam(name = "name", description = "Person name", required = true) String name) {
            return "Hello, " + name + "!";
        }
    }

    // ==================== 测试用例 ====================

    @Test
    @DisplayName("sourceType 返回 manual 字符串标识")
    void sourceTypeReturnsManual() {
        ManualToolProvider provider = new ManualToolProvider();
        assertEquals("manual", provider.sourceType());
    }

    @Test
    @DisplayName("addTool 流式 API 返回 this 支持链式调用")
    void addToolReturnsSelfForChaining() {
        ManualToolProvider provider = new ManualToolProvider();
        ManualToolProvider returned = provider.addTool(new SimpleTool("alpha"));

        assertSame(provider, returned, "addTool 应返回同一实例以支持链式调用");
    }

    @Test
    @DisplayName("addAnnotated 流式 API 返回 this 支持链式调用")
    void addAnnotatedReturnsSelfForChaining() {
        ManualToolProvider provider = new ManualToolProvider();
        ManualToolProvider returned = provider.addAnnotated(new AnnotatedGreeter());

        assertSame(provider, returned, "addAnnotated 应返回同一实例以支持链式调用");
    }

    @Test
    @DisplayName("start 将工具注册进 registry，通过名称可查到且可执行")
    void startRegistersToolIntoRegistry() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new SimpleTool("my_tool"));

        provider.start(registry);

        // 验证 registry 包含该工具
        assertTrue(registry.has("my_tool"), "registry 应包含注册的工具");

        // 验证 payload：通过 get 取出工具并执行，断言返回结果内容
        Optional<Tool> retrieved = registry.get("my_tool");
        assertTrue(retrieved.isPresent(), "get(name) 应返回注册的工具");

        ToolResult result = retrieved.get().execute(Map.of());
        assertFalse(result.isError(), "执行结果不应为错误");
        assertEquals("result from my_tool", result.getContent(),
                "工具执行结果 payload 应与预期一致");
    }

    @Test
    @DisplayName("start 注册注解对象，通过 registry.has 可查到")
    void startRegistersAnnotatedObject() {
        ManualToolProvider provider = new ManualToolProvider()
                .addAnnotated(new AnnotatedGreeter());

        provider.start(registry);

        assertTrue(registry.has("greet"), "registry 应包含注解扫描发现的 greet 工具");

        // 验证 payload：取出工具并执行，断言返回结果内容
        Optional<Tool> greetTool = registry.get("greet");
        assertTrue(greetTool.isPresent());

        ToolResult result = greetTool.get().execute(Map.of("name", "World"));
        assertFalse(result.isError());
        assertEquals("Hello, World!", result.getContent(),
                "注解工具执行结果应与预期一致");
    }

    @Test
    @DisplayName("stop 从 registry 注销所有已注册的工具")
    void stopUnregistersAllTools() {
        SimpleTool toolA = new SimpleTool("tool_a");
        SimpleTool toolB = new SimpleTool("tool_b");

        ManualToolProvider provider = new ManualToolProvider()
                .addTool(toolA)
                .addTool(toolB);

        provider.start(registry);
        assertTrue(registry.has("tool_a"));
        assertTrue(registry.has("tool_b"));
        assertEquals(2, registry.size());

        provider.stop(registry);

        assertFalse(registry.has("tool_a"), "stop 后 tool_a 应被注销");
        assertFalse(registry.has("tool_b"), "stop 后 tool_b 应被注销");
        assertEquals(0, registry.size(), "stop 后 registry 应为空");
    }

    @Test
    @DisplayName("多个工具注册后全部可在 registry 中访问并执行")
    void multipleToolsAllAccessibleAfterStart() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new SimpleTool("alpha"))
                .addTool(new SimpleTool("beta"))
                .addTool(new SimpleTool("gamma"));

        provider.start(registry);

        assertEquals(3, registry.size(), "应注册 3 个工具");

        // 逐个验证每个工具的 payload
        for (String name : new String[]{"alpha", "beta", "gamma"}) {
            assertTrue(registry.has(name), name + " 应存在于 registry 中");
            Optional<Tool> tool = registry.get(name);
            assertTrue(tool.isPresent(), name + " 应可通过 get 获取");

            ToolResult result = tool.get().execute(Map.of());
            assertEquals("result from " + name, result.getContent(),
                    name + " 执行结果 payload 应正确");
        }
    }

    @Test
    @DisplayName("传输链测试：addTool → start → registry.get → execute → 验证结果 payload")
    void transmissionTestToolAddedThroughProviderCanExecuteViaRegistry() {
        // 构造一个有具体业务逻辑的工具
        Tool calculator = new Tool() {
            @Override
            public String getName() {
                return "add_numbers";
            }

            @Override
            public String getDescription() {
                return "Add two numbers together";
            }

            @Override
            public ToolSchema getSchema() {
                return ToolSchema.withRequired(
                        Map.of(
                                "a", Map.of("type", "number", "description", "first number"),
                                "b", Map.of("type", "number", "description", "second number")
                        ),
                        "a", "b"
                );
            }

            @Override
            public ToolResult execute(Map<String, Object> args) {
                double a = ((Number) args.get("a")).doubleValue();
                double b = ((Number) args.get("b")).doubleValue();
                return ToolResult.success(String.valueOf(a + b));
            }
        };

        // 传输链：ManualToolProvider → ToolRegistry → get → execute
        ManualToolProvider provider = new ManualToolProvider().addTool(calculator);
        provider.start(registry);

        // 从 registry 取出工具（模拟 ToolExecutor 的行为）
        Optional<Tool> retrieved = registry.get("add_numbers");
        assertTrue(retrieved.isPresent(), "工具应通过 registry.get 可获取");

        // 执行并断言结果 payload
        ToolResult result = retrieved.get().execute(Map.of("a", 3, "b", 7));
        assertFalse(result.isError(), "计算结果不应为错误");
        assertEquals("10.0", result.getContent(),
                "传输链末端：execute 的结果 payload 应为正确的计算值");
    }

    @Test
    @DisplayName("stop 不影响其他来源注册的工具")
    void stopDoesNotAffectToolsFromOtherSources() {
        // 直接向 registry 注册一个"外部"工具
        registry.register(new SimpleTool("external_tool"));

        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new SimpleTool("internal_tool"));

        provider.start(registry);
        assertEquals(2, registry.size());

        provider.stop(registry);

        assertTrue(registry.has("external_tool"),
                "stop 不应影响其他来源注册的工具");
        assertFalse(registry.has("internal_tool"),
                "stop 应注销自身注册的工具");
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("同时注册 Tool 和注解对象，start 后两者均可用")
    void mixedToolAndAnnotatedRegistration() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new SimpleTool("plain_tool"))
                .addAnnotated(new AnnotatedGreeter());

        provider.start(registry);

        assertTrue(registry.has("plain_tool"), "普通 Tool 应已注册");
        assertTrue(registry.has("greet"), "注解工具应已注册");

        // 验证两者均可执行并返回正确 payload
        ToolResult plainResult = registry.get("plain_tool").orElseThrow().execute(Map.of());
        assertEquals("result from plain_tool", plainResult.getContent());

        ToolResult greetResult = registry.get("greet").orElseThrow().execute(Map.of("name", "Test"));
        assertEquals("Hello, Test!", greetResult.getContent());
    }

    @Test
    @DisplayName("链式调用 addTool 多次后 start 注册全部工具")
    void fluentChainingRegistersAll() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new SimpleTool("t1"))
                .addTool(new SimpleTool("t2"))
                .addTool(new SimpleTool("t3"));

        provider.start(registry);

        assertEquals(3, registry.size(), "链式添加的 3 个工具应全部注册");
        assertTrue(registry.has("t1"));
        assertTrue(registry.has("t2"));
        assertTrue(registry.has("t3"));
    }

    @Test
    @DisplayName("未调用 start 时 registry 应为空")
    void registryEmptyBeforeStart() {
        new ManualToolProvider()
                .addTool(new SimpleTool("not_started"));

        assertEquals(0, registry.size(), "未调用 start 时 registry 不应有工具");
        assertFalse(registry.has("not_started"));
    }

    @Test
    @DisplayName("工具定义在注册后可通过 getToolDefinitions 暴露给 LLM")
    void toolDefinitionsExposedToLLMAfterStart() {
        ManualToolProvider provider = new ManualToolProvider()
                .addTool(new SimpleTool("llm_tool"));

        provider.start(registry);

        var definitions = registry.getToolDefinitions();
        assertEquals(1, definitions.size(), "应有 1 个工具定义");

        Map<String, Object> def = definitions.get(0);
        assertEquals("llm_tool", def.get("name"), "工具定义的 name 应与注册名一致");
        assertEquals("test tool llm_tool", def.get("description"),
                "工具定义的 description 应与 Tool.getDescription() 一致");
        assertNotNull(def.get("input_schema"), "工具定义应包含 input_schema");
    }
}
