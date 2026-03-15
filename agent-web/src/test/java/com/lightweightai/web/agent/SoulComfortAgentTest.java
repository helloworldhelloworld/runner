package com.lightweightai.web.agent;

import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.ConversationMemory;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.UserMemory;
import com.lightweightai.kernel.prompt.PromptEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SoulComfortAgent 测试
 *
 * 覆盖两种构造模式：
 * 1. 兼容模式（无 PromptEngine）- 使用 ConversationMemory
 * 2. OpenClaw 模式（有 PromptEngine）- 使用 MemoryProvider
 *
 * 以及关键业务路径：
 * - 简单对话
 * - 对话记忆保存与恢复
 * - 带工具的对话循环
 * - 流式对话
 * - 会话管理（清空、摘要）
 * - 观察者通知
 */
@DisplayName("SoulComfortAgent - 心灵引导 Agent")
class SoulComfortAgentTest {

    // ==================== 兼容模式测试 ====================

    @Nested
    @DisplayName("兼容模式（无 PromptEngine）")
    class FallbackModeTest {

        private MockLLMProvider llmProvider;
        private SoulComfortAgent agent;

        @BeforeEach
        void setUp() {
            llmProvider = new MockLLMProvider();
            agent = new SoulComfortAgent(llmProvider);
        }

        @Test
        @DisplayName("简单对话 - 返回 LLM 回复")
        void shouldReturnLLMResponse() {
            llmProvider.setNextResponse("我听到你了，你今天过得怎么样？");

            String response = agent.chat("session-1", "你好");

            assertEquals("我听到你了，你今天过得怎么样？", response);
        }

        @Test
        @DisplayName("对话包含系统提示（心灵港湾角色）")
        void shouldIncludeFallbackSystemPrompt() {
            llmProvider.setNextResponse("你好");

            agent.chat("session-1", "你好");

            List<ConversationMessage> sent = llmProvider.getLastMessages();
            assertNotNull(sent);
            assertTrue(sent.size() >= 2); // system + user
            ConversationMessage systemMsg = sent.get(0);
            assertEquals(ConversationMessage.MessageRole.SYSTEM, systemMsg.getRole());
            assertTrue(systemMsg.getTextContent().contains("心灵港湾"));
        }

        @Test
        @DisplayName("多轮对话保持会话历史")
        void shouldMaintainConversationHistory() {
            llmProvider.setNextResponse("你好！");
            agent.chat("session-1", "你好");

            llmProvider.setNextResponse("我记得你刚才说了你好。");
            agent.chat("session-1", "你记得我说了什么吗？");

            List<ConversationMessage> sent = llmProvider.getLastMessages();
            // 应包含历史消息（第一轮的 user + assistant）
            long userMsgCount = sent.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.USER)
                .count();
            assertTrue(userMsgCount >= 2, "应包含至少两轮用户消息");
        }

        @Test
        @DisplayName("外部记忆上下文注入到系统消息")
        void shouldInjectExternalMemoryContext() {
            llmProvider.setNextResponse("对，你确实喜欢咖啡。");

            agent.chat("session-1", "我喜欢什么？", "用户喜欢喝咖啡");

            List<ConversationMessage> sent = llmProvider.getLastMessages();
            boolean hasMemoryCtx = sent.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .anyMatch(m -> m.getTextContent().contains("咖啡"));
            assertTrue(hasMemoryCtx, "应将外部记忆上下文注入到系统消息");
        }

        @Test
        @DisplayName("情绪记录写入 UserMemory")
        void shouldTrackEmotionsInUserMemory() {
            llmProvider.setNextResponse("听起来你有些疲惫。");

            agent.chat("session-1", "我今天好累啊");

            UserMemory userMemory = agent.getUserMemory("session-1");
            List<UserMemory.EmotionRecord> emotions = userMemory.getRecentEmotions(5);
            assertFalse(emotions.isEmpty(), "应记录情绪");
        }

        @Test
        @DisplayName("清空会话")
        void shouldClearSession() {
            llmProvider.setNextResponse("你好！");
            agent.chat("session-1", "你好");

            agent.clearSession("session-1");

            // 清空后重新对话，不应有历史
            llmProvider.setNextResponse("你好，初次见面！");
            agent.chat("session-1", "你好");

            List<ConversationMessage> sent = llmProvider.getLastMessages();
            long userMsgCount = sent.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.USER)
                .count();
            assertEquals(1, userMsgCount, "清空后应只有当前一条用户消息");
        }

        @Test
        @DisplayName("使用外部 ConversationMemory 构造")
        void shouldAcceptExternalConversationMemory() {
            ConversationMemory externalMemory = new ConversationMemory(10);
            SoulComfortAgent agentWithMemory = new SoulComfortAgent(llmProvider, externalMemory);

            llmProvider.setNextResponse("你好！");
            agentWithMemory.chat("s1", "你好");

            assertSame(externalMemory, agentWithMemory.getMemory());
            assertFalse(externalMemory.getHistory("s1").isEmpty());
        }
    }

    // ==================== OpenClaw 模式测试 ====================

    @Nested
    @DisplayName("OpenClaw 模式（有 PromptEngine）")
    class OpenClawModeTest {

        private MockLLMProvider llmProvider;
        private MemoryProvider memoryProvider;
        private PromptEngine promptEngine;
        private SoulComfortAgent agent;

        @BeforeEach
        void setUp() {
            llmProvider = new MockLLMProvider();
            memoryProvider = new InMemoryProvider();
            promptEngine = PromptEngine.builder()
                .memoryProvider(memoryProvider)
                .baseSystemPrompt("你是一个温暖的引导者。")
                .build();
            agent = new SoulComfortAgent(llmProvider, promptEngine);
        }

        @Test
        @DisplayName("通过 PromptEngine 构建消息")
        void shouldBuildMessagesViaPromptEngine() {
            llmProvider.setNextResponse("你好，我在这里陪伴你。");

            String response = agent.chat("session-1", "你好");

            assertEquals("你好，我在这里陪伴你。", response);

            // 消息应写入 MemoryProvider
            var history = memoryProvider.getHistory("session-1", 10);
            assertEquals(2, history.size()); // user + assistant
        }

        @Test
        @DisplayName("助手回复保存到 MemoryProvider")
        void shouldSaveAssistantMessageToMemoryProvider() {
            llmProvider.setNextResponse("你好！");
            agent.chat("session-1", "你好");

            llmProvider.setNextResponse("我记得你。");
            agent.chat("session-1", "还记得我吗？");

            var history = memoryProvider.getHistory("session-1", 10);
            assertEquals(4, history.size()); // user1, assistant1, user2, assistant2
            assertEquals("assistant", history.get(1).getRole());
            assertEquals("你好！", history.get(1).getContent());
        }

        @Test
        @DisplayName("清空会话通过 MemoryProvider")
        void shouldClearSessionViaMemoryProvider() {
            llmProvider.setNextResponse("你好！");
            agent.chat("session-1", "你好");

            agent.clearSession("session-1");

            var history = memoryProvider.getHistory("session-1", 10);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("getUserMemory 返回空对象（OpenClaw 模式）")
        void shouldReturnEmptyUserMemoryInOpenClawMode() {
            UserMemory um = agent.getUserMemory("session-1");
            assertNotNull(um);
            // OpenClaw 模式下返回新的空 UserMemory
        }
    }

    // ==================== 工具调用测试 ====================

    @Nested
    @DisplayName("工具调用")
    class ToolCallingTest {

        private MockLLMProvider llmProvider;
        private SoulComfortAgent agent;

        @BeforeEach
        void setUp() {
            llmProvider = new MockLLMProvider();
        }

        @Test
        @DisplayName("执行单个工具调用并获取最终回复")
        void shouldExecuteToolCallAndReturnFinalResponse() {
            Tool weatherTool = new MockTool("get_weather", "获取天气",
                args -> "北京今天晴天，25度");

            MemoryProvider mp = new InMemoryProvider();
            PromptEngine pe = PromptEngine.builder()
                .memoryProvider(mp)
                .baseSystemPrompt("你是助手。")
                .build();
            agent = new SoulComfortAgent(llmProvider, pe, List.of(weatherTool));

            // 第一次：LLM 返回工具调用
            llmProvider.addToolCallResponse("get_weather", Map.of("city", "北京"));
            // 第二次：LLM 返回最终回复
            llmProvider.addFollowUpResponse("北京今天是晴天，气温25度，适合外出。");

            String response = agent.chat("s1", "北京天气怎么样？");

            assertEquals("北京今天是晴天，气温25度，适合外出。", response);
        }

        @Test
        @DisplayName("工具不存在时返回错误信息给 LLM")
        void shouldHandleMissingTool() {
            Tool onlyTool = new MockTool("existing_tool", "存在的工具", args -> "ok");
            MemoryProvider mp = new InMemoryProvider();
            PromptEngine pe = PromptEngine.builder()
                .memoryProvider(mp).baseSystemPrompt("助手").build();
            agent = new SoulComfortAgent(llmProvider, pe, List.of(onlyTool));

            // LLM 调用一个不存在的工具
            llmProvider.addToolCallResponse("nonexistent_tool", Map.of());
            llmProvider.addFollowUpResponse("抱歉，工具不可用。");

            String response = agent.chat("s1", "做些什么");

            assertEquals("抱歉，工具不可用。", response);
            // 工具结果消息应包含 "Tool not found"
            List<ConversationMessage> sent = llmProvider.getLastMessages();
            boolean hasToolNotFound = sent.stream()
                .anyMatch(m -> m.getTextContent().contains("Tool not found"));
            assertTrue(hasToolNotFound);
        }

        @Test
        @DisplayName("工具执行异常时返回错误信息")
        void shouldHandleToolExecutionError() {
            Tool failingTool = new MockTool("fail_tool", "会失败的工具", args -> {
                throw new RuntimeException("工具执行出错");
            });
            MemoryProvider mp = new InMemoryProvider();
            PromptEngine pe = PromptEngine.builder()
                .memoryProvider(mp).baseSystemPrompt("助手").build();
            agent = new SoulComfortAgent(llmProvider, pe, List.of(failingTool));

            llmProvider.addToolCallResponse("fail_tool", Map.of());
            llmProvider.addFollowUpResponse("工具出了问题，我来帮你想其他办法。");

            String response = agent.chat("s1", "用这个工具");

            assertEquals("工具出了问题，我来帮你想其他办法。", response);
            List<ConversationMessage> sent = llmProvider.getLastMessages();
            boolean hasToolError = sent.stream()
                .anyMatch(m -> m.getTextContent().contains("Tool error"));
            assertTrue(hasToolError);
        }

        @Test
        @DisplayName("超过最大工具迭代次数抛出异常")
        void shouldThrowWhenMaxToolIterationsExceeded() {
            Tool loopTool = new MockTool("loop", "循环工具", args -> "continue");
            MemoryProvider mp = new InMemoryProvider();
            PromptEngine pe = PromptEngine.builder()
                .memoryProvider(mp).baseSystemPrompt("助手").build();
            agent = new SoulComfortAgent(llmProvider, pe, List.of(loopTool));

            // 每次都返回工具调用，超过 MAX_TOOL_ITERATIONS (5)
            for (int i = 0; i < 10; i++) {
                llmProvider.addToolCallResponse("loop", Map.of());
            }

            assertThrows(RuntimeException.class, () ->
                agent.chat("s1", "开始循环"));
        }
    }

    // ==================== 流式对话测试 ====================

    @Nested
    @DisplayName("流式对话")
    class StreamingTest {

        private MockLLMProvider llmProvider;
        private SoulComfortAgent agent;

        @BeforeEach
        void setUp() {
            llmProvider = new MockLLMProvider();
            agent = new SoulComfortAgent(llmProvider);
        }

        @Test
        @DisplayName("流式对话 - 收集所有 delta")
        void shouldStreamDeltas() throws Exception {
            llmProvider.setStreamResponse("你好", "！", "有什么", "可以帮你？");
            List<String> deltas = new ArrayList<>();

            CompletableFuture<String> future = agent.chatStream("s1", "你好",
                new LLMProvider.StreamEventHandler() {
                    @Override
                    public void onTextDelta(String delta) {
                        deltas.add(delta);
                    }

                    @Override
                    public void onComplete(LLMResponse response) {}

                    @Override
                    public void onError(Throwable error) {
                        fail("不应出错: " + error.getMessage());
                    }
                });

            String result = future.get(5, TimeUnit.SECONDS);

            assertEquals(4, deltas.size());
            assertEquals("你好", deltas.get(0));
            assertEquals("可以帮你？", deltas.get(3));
            assertEquals("你好！有什么可以帮你？", result);
        }

        @Test
        @DisplayName("流式对话保存到会话记忆")
        void shouldSaveStreamResponseToMemory() throws Exception {
            llmProvider.setStreamResponse("我在", "这里");

            CompletableFuture<String> future = agent.chatStream("s1", "你好",
                new LLMProvider.StreamEventHandler() {
                    @Override
                    public void onTextDelta(String delta) {}
                    @Override
                    public void onComplete(LLMResponse response) {}
                });

            future.get(5, TimeUnit.SECONDS);

            // 验证消息已存入 ConversationMemory
            ConversationMemory memory = agent.getMemory();
            List<ConversationMessage> history = memory.getRecentMessages("s1", 10);
            assertTrue(history.size() >= 2); // user + assistant
        }
    }

    // ==================== 观察者测试 ====================

    @Nested
    @DisplayName("观察者通知")
    class ObserverTest {

        @Test
        @DisplayName("Observer 收到完整生命周期回调")
        void shouldNotifyObserverThroughLifecycle() {
            MockLLMProvider llm = new MockLLMProvider();
            llm.setNextResponse("你好！");

            SoulComfortAgent agent = new SoulComfortAgent(llm);
            RecordingObserver observer = new RecordingObserver();
            agent.setObserver(observer);

            agent.chat("s1", "你好");

            assertTrue(observer.startCalled, "onAgentStart 应被调用");
            assertTrue(observer.llmRequestCalled, "onLLMRequest 应被调用");
            assertTrue(observer.llmResponseCalled, "onLLMResponse 应被调用");
            assertTrue(observer.completeCalled, "onAgentComplete 应被调用");
            assertFalse(observer.errorCalled, "不应调用 onError");
        }

        @Test
        @DisplayName("LLM 异常时通知 Observer.onError")
        void shouldNotifyObserverOnError() {
            MockLLMProvider llm = new MockLLMProvider();
            llm.setThrowOnComplete(new RuntimeException("LLM 连接失败"));

            SoulComfortAgent agent = new SoulComfortAgent(llm);
            RecordingObserver observer = new RecordingObserver();
            agent.setObserver(observer);

            assertThrows(RuntimeException.class, () -> agent.chat("s1", "你好"));

            assertTrue(observer.errorCalled, "onError 应被调用");
        }
    }

    // ==================== 辅助类 ====================

    static class MockLLMProvider implements LLMProvider {
        private final List<Object> responseQueue = new ArrayList<>();
        private List<ConversationMessage> lastMessages;
        private int responseIndex = 0;
        private String[] streamChunks;
        private RuntimeException throwOnComplete;

        void setNextResponse(String response) {
            responseQueue.clear();
            responseQueue.add(response);
            responseIndex = 0;
        }

        void addFollowUpResponse(String response) {
            responseQueue.add(response);
        }

        void addToolCallResponse(String toolName, Map<String, Object> args) {
            responseQueue.add(new ToolCallSetup(toolName, args));
        }

        void setStreamResponse(String... chunks) {
            this.streamChunks = chunks;
        }

        void setThrowOnComplete(RuntimeException e) {
            this.throwOnComplete = e;
        }

        List<ConversationMessage> getLastMessages() {
            return lastMessages;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            this.lastMessages = new ArrayList<>(messages);

            if (throwOnComplete != null) throw throwOnComplete;

            if (responseIndex >= responseQueue.size()) {
                return createTextResponse("Mock response");
            }

            Object next = responseQueue.get(responseIndex++);
            if (next instanceof ToolCallSetup setup) {
                return createToolCallResponse(setup.toolName, setup.args);
            }
            return createTextResponse((String) next);
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options,
                StreamEventHandler handler) {
            this.lastMessages = new ArrayList<>(messages);
            StringBuilder full = new StringBuilder();

            handler.onStart();
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
        public String getProviderName() { return "mock"; }

        @Override
        public ModelCapability getModelCapability() { return null; }

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
            final String toolName;
            final Map<String, Object> args;
            ToolCallSetup(String toolName, Map<String, Object> args) {
                this.toolName = toolName;
                this.args = args;
            }
        }
    }

    static class MockTool implements Tool {
        private final String name;
        private final String description;
        private final java.util.function.Function<Map<String, Object>, String> executor;

        MockTool(String name, String description,
                 java.util.function.Function<Map<String, Object>, String> executor) {
            this.name = name;
            this.description = description;
            this.executor = executor;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }

        @Override
        public ToolResult execute(Map<String, Object> args) {
            String result = executor.apply(args);
            return ToolResult.success(result);
        }
    }

    static class RecordingObserver implements AgentObserver {
        boolean startCalled = false;
        boolean llmRequestCalled = false;
        boolean llmResponseCalled = false;
        boolean completeCalled = false;
        boolean errorCalled = false;

        @Override
        public void onAgentStart(String userMessage, String sessionId) { startCalled = true; }
        @Override
        public void onLLMRequest(List<ConversationMessage> messages) { llmRequestCalled = true; }
        @Override
        public void onLLMResponse(LLMResponse response) { llmResponseCalled = true; }
        @Override
        public void onAgentComplete(AgentResponse response) { completeCalled = true; }
        @Override
        public void onError(Throwable error) { errorCalled = true; }
    }
}
