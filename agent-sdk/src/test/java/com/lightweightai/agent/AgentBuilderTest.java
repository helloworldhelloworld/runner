package com.lightweightai.agent;

import com.lightweightai.agent.plugin.Plugin;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentBuilder 单元测试
 *
 * 覆盖关键路径：
 * - Builder 验证（必填字段缺失抛异常）
 * - Claude / OpenAI 提供者配置
 * - 工具注册（ToolRegistry、单个 Tool）
 * - 插件注册
 * - 配置选项（memory、timeout、options）
 * - 默认值
 */
@DisplayName("AgentBuilder - Agent 构建器")
class AgentBuilderTest {

    // ==================== 验证测试 ====================

    @Test
    @DisplayName("未设置 provider 和 apiKey 时 build() 抛异常")
    void shouldThrowWhenProviderNotSet() {
        assertThrows(IllegalStateException.class, () ->
            Agent.builder().build());
    }

    @Test
    @DisplayName("设置 provider 但未设置 apiKey 不应发生")
    void shouldThrowWhenApiKeyMissing() {
        // AgentBuilder requires both provider and apiKey through claude()/openai()
        // There's no way to set provider without apiKey via the public API,
        // so we test the internal state by calling build() without any provider method
        AgentBuilder builder = new AgentBuilder();
        assertThrows(IllegalStateException.class, builder::build);
    }

    // ==================== Claude 配置测试 ====================

    @Test
    @DisplayName("使用 Claude 默认模型创建 Agent")
    void shouldCreateWithClaudeDefault() {
        Agent agent = Agent.builder()
            .claude("test-key")
            .build();

        assertNotNull(agent);
    }

    @Test
    @DisplayName("使用 Claude 指定模型创建 Agent")
    void shouldCreateWithClaudeCustomModel() {
        Agent agent = Agent.builder()
            .claude("test-key", "claude-3-opus-20240229")
            .build();

        assertNotNull(agent);
    }

    @Test
    @DisplayName("Builder 内部状态正确设置 Claude 配置")
    void shouldSetClaudeInternalState() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("my-key", "claude-3-haiku-20240307");

        assertEquals("claude", builder.getLlmProvider());
        assertEquals("my-key", builder.getApiKey());
        assertEquals("claude-3-haiku-20240307", builder.getModel());
    }

    // ==================== OpenAI 配置测试 ====================

    @Test
    @DisplayName("使用 OpenAI 默认模型创建 Agent")
    void shouldCreateWithOpenAIDefault() {
        Agent agent = Agent.builder()
            .openai("test-key")
            .build();

        assertNotNull(agent);
    }

    @Test
    @DisplayName("使用 OpenAI 指定模型创建 Agent")
    void shouldCreateWithOpenAICustomModel() {
        Agent agent = Agent.builder()
            .openai("test-key", "gpt-4-turbo")
            .build();

        assertNotNull(agent);
    }

    @Test
    @DisplayName("Builder 内部状态正确设置 OpenAI 配置")
    void shouldSetOpenAIInternalState() {
        AgentBuilder builder = new AgentBuilder();
        builder.openai("openai-key", "gpt-4-turbo");

        assertEquals("openai", builder.getLlmProvider());
        assertEquals("openai-key", builder.getApiKey());
        assertEquals("gpt-4-turbo", builder.getModel());
    }

    // ==================== 工具注册测试 ====================

    @Test
    @DisplayName("通过 ToolRegistry 注册工具")
    void shouldRegisterToolRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(createDummyTool("test-tool"));

        Agent agent = Agent.builder()
            .claude("key")
            .tools(registry)
            .build();

        assertNotNull(agent);
    }

    @Test
    @DisplayName("通过 tool() 注册单个工具")
    void shouldRegisterSingleTool() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("key");
        builder.tool(createDummyTool("my-tool"));

        ToolRegistry registry = builder.getToolRegistry();
        assertNotNull(registry);
        assertTrue(registry.get("my-tool").isPresent());
    }

    @Test
    @DisplayName("tool() 自动创建 ToolRegistry")
    void shouldAutoCreateRegistryOnToolAdd() {
        AgentBuilder builder = new AgentBuilder();
        assertNull(builder.getToolRegistry());

        builder.tool(createDummyTool("auto-created"));
        assertNotNull(builder.getToolRegistry());
    }

    @Test
    @DisplayName("多次 tool() 调用累积工具")
    void shouldAccumulateMultipleTools() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("key")
            .tool(createDummyTool("t1"))
            .tool(createDummyTool("t2"));

        ToolRegistry registry = builder.getToolRegistry();
        assertTrue(registry.get("t1").isPresent());
        assertTrue(registry.get("t2").isPresent());
    }

    // ==================== 插件测试 ====================

    @Test
    @DisplayName("注册单个插件")
    void shouldRegisterPlugin() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("key");

        Plugin mockPlugin = createDummyPlugin("test-plugin");
        builder.plugin(mockPlugin);

        assertEquals(1, builder.getPlugins().size());
    }

    @Test
    @DisplayName("批量注册插件")
    void shouldRegisterMultiplePlugins() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("key");

        builder.plugins(
            createDummyPlugin("p1"),
            createDummyPlugin("p2"),
            createDummyPlugin("p3")
        );

        assertEquals(3, builder.getPlugins().size());
    }

    @Test
    @DisplayName("getPlugins() 返回副本不影响内部状态")
    void shouldReturnPluginsCopy() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("key").plugin(createDummyPlugin("p1"));

        var plugins = builder.getPlugins();
        plugins.clear();

        assertEquals(1, builder.getPlugins().size());
    }

    // ==================== 选项测试 ====================

    @Test
    @DisplayName("启用 memory")
    void shouldEnableMemory() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("key").withMemory();

        assertTrue(builder.isEnableMemory());
    }

    @Test
    @DisplayName("默认 memory 关闭")
    void shouldDefaultMemoryDisabled() {
        AgentBuilder builder = new AgentBuilder();
        assertFalse(builder.isEnableMemory());
    }

    @Test
    @DisplayName("自定义 timeout")
    void shouldSetTimeout() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("key").timeout(Duration.ofSeconds(30));

        assertEquals(Duration.ofSeconds(30), builder.getTimeout());
    }

    @Test
    @DisplayName("默认 timeout 为 5 分钟")
    void shouldDefaultTimeoutFiveMinutes() {
        AgentBuilder builder = new AgentBuilder();
        assertEquals(Duration.ofMinutes(5), builder.getTimeout());
    }

    @Test
    @DisplayName("设置 ChatOptions")
    void shouldSetChatOptions() {
        ChatOptions options = ChatOptions.builder().build();

        AgentBuilder builder = new AgentBuilder();
        builder.claude("key").options(options);

        assertNotNull(builder.getDefaultOptions());
    }

    // ==================== Fluent API 测试 ====================

    @Test
    @DisplayName("Builder 支持链式调用")
    void shouldSupportFluentChaining() {
        Agent agent = Agent.builder()
            .claude("key", "claude-3-5-sonnet-20241022")
            .tool(createDummyTool("tool1"))
            .plugin(createDummyPlugin("plugin1"))
            .withMemory()
            .timeout(Duration.ofMinutes(10))
            .build();

        assertNotNull(agent);
    }

    @Test
    @DisplayName("切换 provider 覆盖之前的设置")
    void shouldOverridePreviousProvider() {
        AgentBuilder builder = new AgentBuilder();
        builder.claude("claude-key");
        builder.openai("openai-key");

        assertEquals("openai", builder.getLlmProvider());
        assertEquals("openai-key", builder.getApiKey());
    }

    // ==================== 辅助方法 ====================

    private Tool createDummyTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Dummy tool: " + name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    private Plugin createDummyPlugin(String name) {
        return new Plugin() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Dummy plugin: " + name; }
        };
    }
}
