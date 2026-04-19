package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

// 使用 llm 包中的 ToolResult
import static com.lightweightai.kernel.llm.ToolResult.success;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TDD: AgentLoop 测试
 *
 * AgentLoop 是 OpenClaw 风格的核心执行循环：
 * 1. 检索记忆 → 2. 构建上下文 → 3. LLM调用 → 4. Tool循环 → 5. 写入记忆
 */
@DisplayName("AgentLoop - Agent执行循环")
class AgentLoopTest {

    private MemoryProvider memory;
    private MockLLMProvider llmProvider;
    private AgentLoop agentLoop;

    @BeforeEach
    void setUp() {
        memory = new InMemoryProvider();
        llmProvider = new MockLLMProvider();
        agentLoop = AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memory)
            .systemPrompt("你是一个友好的助手。")
            .build();
    }

    // ==================== 基本对话测试 ====================

    @Test
    @DisplayName("简单对话 - 无工具调用")
    void shouldHandleSimpleConversation() {
        // Given
        llmProvider.setNextResponse("你好！有什么可以帮助你的？");

        // When
        AgentResponse response = agentLoop.run("你好", "session-1");

        // Then
        assertNotNull(response);
        assertEquals("你好！有什么可以帮助你的？", response.getText());
        assertFalse(response.hasToolCalls());
    }

    @Test
    @DisplayName("对话保存到记忆")
    void shouldSaveConversationToMemory() {
        // Given
        llmProvider.setNextResponse("你好张三！");

        // When
        agentLoop.run("我叫张三", "session-1");

        // Then
        List<Message> history = memory.getHistory("session-1", 10);
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).getRole());
        assertEquals("我叫张三", history.get(0).getContent());
        assertEquals("assistant", history.get(1).getRole());
    }

    @Test
    @DisplayName("多轮对话保持上下文")
    void shouldMaintainConversationContext() {
        // Given
        llmProvider.setNextResponse("你好张三！");
        agentLoop.run("我叫张三", "session-1");

        llmProvider.setNextResponse("你叫张三。");

        // When
        AgentResponse response = agentLoop.run("我叫什么名字？", "session-1");

        // Then
        // LLM 应该收到包含历史的消息
        List<ConversationMessage> lastMessages = llmProvider.getLastMessages();
        assertTrue(lastMessages.size() >= 3); // system + 历史 + 当前
    }

    // ==================== 记忆检索测试 ====================

    @Test
    @DisplayName("从记忆中检索相关上下文")
    void shouldRetrieveMemoryContext() {
        // Given - 预存记忆
        memory.writeEphemeral("用户张三喜欢喝咖啡");
        llmProvider.setNextResponse("根据我的记忆，你喜欢喝咖啡。");

        // When
        AgentResponse response = agentLoop.run("我喜欢喝什么？", "session-1");

        // Then
        // LLM 应该收到包含记忆的系统消息
        List<ConversationMessage> lastMessages = llmProvider.getLastMessages();
        String allContent = lastMessages.stream()
            .map(m -> m.getTextContent())
            .reduce("", (a, b) -> a + " " + b);
        assertTrue(allContent.contains("咖啡") || response.getText().contains("咖啡"));
    }

    // ==================== 工具调用测试 ====================

    @Test
    @DisplayName("处理单个工具调用")
    void shouldHandleSingleToolCall() {
        // Given - 配置工具
        Tool weatherTool = new MockTool("get_weather", "获取天气",
            args -> Map.of("weather", "晴天", "temp", 25));

        // 创建专门的 mock provider
        MockLLMProvider toolMockProvider = new MockLLMProvider();

        AgentLoop loopWithTools = AgentLoop.builder()
            .llmProvider(toolMockProvider)
            .memoryProvider(memory)
            .addTool(weatherTool)
            .build();

        // LLM 第一次响应：调用工具
        toolMockProvider.addToolCallResponse("get_weather", Map.of("city", "北京"));
        // LLM 第二次响应：最终回复
        toolMockProvider.addFollowUpResponse("北京今天是晴天，气温25度。");

        // When
        AgentResponse response = loopWithTools.run("北京天气怎么样？", "session-1");

        // Then
        assertEquals("北京今天是晴天，气温25度。", response.getText());
    }

    @Test
    @DisplayName("工具调用循环 - 超过最大次数抛出异常")
    void shouldLimitToolCallIterations() {
        // Given - 工具总是触发更多工具调用
        Tool loopTool = new MockTool("loop_tool", "循环工具",
            args -> Map.of("result", "continue"));

        AgentLoop loopWithTools = AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memory)
            .addTool(loopTool)
            .maxToolIterations(5) // 限制为5次
            .build();

        // 每次都返回工具调用
        for (int i = 0; i < 10; i++) {
            llmProvider.addToolCallResponse("loop_tool", Map.of());
        }

        // When / Then - 应该在达到限制后抛出异常
        assertThrows(RuntimeException.class, () ->
            loopWithTools.run("开始循环", "session-1"));
    }

    // ==================== 工具定义自动注入测试 ====================

    @Test
    @DisplayName("构造时自动从 ToolRegistry 注入 toolDefinitions 到 LLMOptions")
    void shouldAutoDeriveToolDefinitionsFromRegistry() {
        Tool searchTool = new MockTool("search", "search the web",
            args -> Map.of("result", "ok"));
        Tool readTool = new MockTool("read_file", "read a file",
            args -> Map.of("result", "ok"));

        AgentLoop loop = AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memory)
            .addTool(searchTool)
            .addTool(readTool)
            .build();

        List<Map<String, Object>> defs = loop.getLlmOptions().getToolDefinitions();
        assertNotNull(defs);
        assertEquals(2, defs.size());
        List<String> names = defs.stream().map(d -> (String) d.get("name")).toList();
        assertTrue(names.contains("search"));
        assertTrue(names.contains("read_file"));
    }

    @Test
    @DisplayName("调用方已设置 toolDefinitions 时保留调用方版本（override）")
    void shouldNotOverrideCallerSuppliedToolDefinitions() {
        Tool fromRegistry = new MockTool("registry_tool", "from registry",
            args -> Map.of("r", "x"));

        List<Map<String, Object>> customDefs = List.of(
            Map.of("name", "custom_tool", "description", "caller-supplied",
                   "input_schema", Map.of("type", "object")));

        AgentLoop loop = AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memory)
            .addTool(fromRegistry)
            .llmOptions(LLMOptions.builder().toolDefinitions(customDefs).build())
            .build();

        List<Map<String, Object>> defs = loop.getLlmOptions().getToolDefinitions();
        assertEquals(1, defs.size());
        assertEquals("custom_tool", defs.get(0).get("name"));
    }

    @Test
    @DisplayName("空 registry + 空 options → 空 toolDefinitions（不 NPE）")
    void shouldHandleEmptyRegistryAndOptions() {
        AgentLoop loop = AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memory)
            .build();

        List<Map<String, Object>> defs = loop.getLlmOptions().getToolDefinitions();
        assertNotNull(defs);
        assertTrue(defs.isEmpty());
    }

    // ==================== 流式响应测试 ====================

    @Test
    @DisplayName("流式响应 - 逐字输出")
    void shouldStreamResponse() {
        // Given
        llmProvider.setStreamResponse("你好！", "有什么", "可以帮助你？");
        List<String> deltas = new ArrayList<>();

        // When
        CompletableFuture<AgentResponse> future = agentLoop.runStream(
            "你好",
            "session-1",
            delta -> deltas.add(delta)
        );
        AgentResponse response = future.join();

        // Then
        assertEquals(3, deltas.size());
        assertEquals("你好！", deltas.get(0));
        assertEquals("你好！有什么可以帮助你？", response.getText());
    }

    // ==================== 辅助类 ====================

    /**
     * Mock LLM Provider for testing
     */
    static class MockLLMProvider implements LLMProvider {
        private final List<Object> responseQueue = new ArrayList<>(); // String or ToolCallSetup
        private List<ConversationMessage> lastMessages;
        private int responseIndex = 0;
        private String[] streamChunks;

        void setNextResponse(String response) {
            responseQueue.clear();
            responseQueue.add(response);
            responseIndex = 0;
        }

        void addFollowUpResponse(String response) {
            responseQueue.add(response);
        }

        void setNextResponseWithToolCall(String toolName, Map<String, Object> args) {
            responseQueue.add(new ToolCallSetup(toolName, args, 0));
        }

        void addToolCallResponse(String toolName, Map<String, Object> args) {
            responseQueue.add(new ToolCallSetup(toolName, args, 0));
        }

        void setStreamResponse(String... chunks) {
            this.streamChunks = chunks;
        }

        List<ConversationMessage> getLastMessages() {
            return lastMessages;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            this.lastMessages = new ArrayList<>(messages);

            if (responseIndex >= responseQueue.size()) {
                return createTextResponse("Mock response");
            }

            Object nextResponse = responseQueue.get(responseIndex++);

            if (nextResponse instanceof ToolCallSetup setup) {
                return createToolCallResponse(setup.toolName, setup.args);
            } else if (nextResponse instanceof String text) {
                return createTextResponse(text);
            }

            return createTextResponse("Mock response");
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages,
                LLMOptions options,
                StreamEventHandler handler) {

            this.lastMessages = new ArrayList<>(messages);
            StringBuilder full = new StringBuilder();

            if (streamChunks != null) {
                for (String chunk : streamChunks) {
                    handler.onTextDelta(chunk);
                    full.append(chunk);
                }
            }

            LLMResponse response = createTextResponse(full.toString());
            handler.onComplete(response);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public String getProviderName() {
            return "mock";
        }

        @Override
        public ModelCapability getModelCapability() {
            return null;
        }

        private LLMResponse createTextResponse(String text) {
            ConversationMessage msg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text)
                .build();
            return LLMResponse.builder()
                .message(msg)
                .stopReason("stop")
                .build();
        }

        private LLMResponse createToolCallResponse(String toolName, Map<String, Object> args) {
            ConversationMessage msg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("")
                .build();
            return LLMResponse.builder()
                .message(msg)
                .toolCalls(List.of(new ToolCall(toolName + "-id", toolName, args)))
                .stopReason("tool_use")
                .build();
        }

        static class ToolCallSetup {
            String toolName;
            Map<String, Object> args;
            int responseIndex;

            ToolCallSetup(String toolName, Map<String, Object> args, int responseIndex) {
                this.toolName = toolName;
                this.args = args;
                this.responseIndex = responseIndex;
            }
        }
    }

    /**
     * Mock Tool for testing
     */
    static class MockTool implements Tool {
        private final String name;
        private final String description;
        private final java.util.function.Function<Map<String, Object>, Map<String, Object>> executor;

        MockTool(String name, String description,
                 java.util.function.Function<Map<String, Object>, Map<String, Object>> executor) {
            this.name = name;
            this.description = description;
            this.executor = executor;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getDescription() { return description; }

        @Override
        public ToolSchema getSchema() {
            return new ToolSchema(Map.of("type", "object"));
        }

        @Override
        public com.lightweightai.kernel.llm.ToolResult execute(Map<String, Object> args) {
            Map<String, Object> result = executor.apply(args);
            return com.lightweightai.kernel.llm.ToolResult.success(result.toString());
        }
    }
}
