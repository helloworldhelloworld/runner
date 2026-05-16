package com.lightweightai.web.agent;

import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.ConversationMemory;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.UserMemory;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.prompt.PromptRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

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

    // ==================== 工具结果传递链测试 ====================

    @Nested
    @DisplayName("工具结果传递链（payload 传输验证）")
    class ToolPayloadTransmissionTest {

        @Test
        @DisplayName("工具执行结果文本出现在下一次 LLM 调用的消息中")
        void shouldPassToolResultTextToNextLLMCall() {
            MockLLMProvider llm = new MockLLMProvider();
            Tool echoTool = new MockTool("echo", "回声工具",
                args -> "工具返回值: " + args.getOrDefault("input", "默认"));

            MemoryProvider mp = new InMemoryProvider();
            PromptEngine pe = PromptEngine.builder()
                .memoryProvider(mp)
                .baseSystemPrompt("你是助手。")
                .build();
            SoulComfortAgent agent = new SoulComfortAgent(llm, pe, List.of(echoTool));

            // 第一次调用：LLM 返回工具调用
            llm.addToolCallResponse("echo", Map.of("input", "hello"));
            // 第二次调用：LLM 返回最终回复
            llm.addFollowUpResponse("工具说了 hello");

            agent.chat("s1", "用 echo 工具");

            // 捕获第二次 LLM 调用时的消息列表 — 应包含工具返回值
            List<ConversationMessage> sentMessages = llm.getLastMessages();
            boolean toolResultInMessages = sentMessages.stream()
                .anyMatch(m -> m.getTextContent() != null
                    && m.getTextContent().contains("工具返回值: hello"));
            assertTrue(toolResultInMessages,
                "第二次 LLM 调用的消息列表应包含工具执行结果 '工具返回值: hello'");
        }

        @Test
        @DisplayName("多个工具调用的结果全部传递给下一次 LLM 调用")
        void shouldPassAllToolResultsToNextLLMCall() {
            MockLLMProvider llm = new MockLLMProvider();
            Tool weatherTool = new MockTool("get_weather", "天气", args -> "晴天25度");
            Tool timeTool = new MockTool("get_time", "时间", args -> "14:30");

            MemoryProvider mp = new InMemoryProvider();
            PromptEngine pe = PromptEngine.builder()
                .memoryProvider(mp)
                .baseSystemPrompt("助手")
                .build();
            SoulComfortAgent agent = new SoulComfortAgent(llm, pe, List.of(weatherTool, timeTool));

            // LLM 同时调用两个工具 — 需要手动构建多工具响应
            LLMResponse multiToolResp = LLMResponse.builder()
                .message(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT)
                    .textContent("").build())
                .toolCalls(List.of(
                    new ToolCall("w-id", "get_weather", Map.of()),
                    new ToolCall("t-id", "get_time", Map.of())
                ))
                .stopReason("tool_use")
                .build();
            llm.addRawResponse(multiToolResp);
            llm.addFollowUpResponse("天气晴天25度，现在14:30。");

            String response = agent.chat("s1", "天气和时间？");

            assertEquals("天气晴天25度，现在14:30。", response);
            // 验证工具结果文本都出现在 LLM 收到的消息中
            List<ConversationMessage> lastSent = llm.getLastMessages();
            boolean hasWeatherResult = lastSent.stream()
                .anyMatch(m -> m.getTextContent() != null && m.getTextContent().contains("晴天25度"));
            boolean hasTimeResult = lastSent.stream()
                .anyMatch(m -> m.getTextContent() != null && m.getTextContent().contains("14:30"));
            assertTrue(hasWeatherResult, "应传递天气工具结果");
            assertTrue(hasTimeResult, "应传递时间工具结果");
        }
    }

    // ==================== 观察者 payload 测试 ====================

    @Nested
    @DisplayName("观察者 Payload 验证（非仅 boolean）")
    class ObserverPayloadTest {

        @Test
        @DisplayName("onAgentStart 收到正确的 userMessage 和 sessionId")
        void shouldReceiveCorrectStartPayload() {
            MockLLMProvider llm = new MockLLMProvider();
            llm.setNextResponse("你好！");
            SoulComfortAgent agent = new SoulComfortAgent(llm);
            PayloadCapturingObserver observer = new PayloadCapturingObserver();
            agent.setObserver(observer);

            agent.chat("test-session-42", "我很难过");

            assertEquals("我很难过", observer.capturedUserMessage,
                "onAgentStart 的 userMessage 应与输入一致");
            assertEquals("test-session-42", observer.capturedSessionId,
                "onAgentStart 的 sessionId 应与输入一致");
        }

        @Test
        @DisplayName("onLLMRequest 收到的消息列表包含系统提示和用户消息")
        void shouldReceiveLLMRequestMessages() {
            MockLLMProvider llm = new MockLLMProvider();
            llm.setNextResponse("你好！");
            SoulComfortAgent agent = new SoulComfortAgent(llm);
            PayloadCapturingObserver observer = new PayloadCapturingObserver();
            agent.setObserver(observer);

            agent.chat("s1", "你好吗");

            assertNotNull(observer.capturedLLMRequestMessages);
            assertTrue(observer.capturedLLMRequestMessages.size() >= 2,
                "LLM 请求消息列表应至少包含系统提示和用户消息");
            // 验证系统提示
            assertEquals(ConversationMessage.MessageRole.SYSTEM,
                observer.capturedLLMRequestMessages.get(0).getRole());
            // 验证用户消息出现
            boolean hasUserMsg = observer.capturedLLMRequestMessages.stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.USER
                    && "你好吗".equals(m.getTextContent()));
            assertTrue(hasUserMsg, "LLM 请求消息应包含用户消息 '你好吗'");
        }

        @Test
        @DisplayName("onLLMResponse 收到的响应包含正确的文本内容")
        void shouldReceiveLLMResponsePayload() {
            MockLLMProvider llm = new MockLLMProvider();
            llm.setNextResponse("我理解你的感受。");
            SoulComfortAgent agent = new SoulComfortAgent(llm);
            PayloadCapturingObserver observer = new PayloadCapturingObserver();
            agent.setObserver(observer);

            agent.chat("s1", "你好");

            assertNotNull(observer.capturedLLMResponse);
            assertEquals("我理解你的感受。",
                observer.capturedLLMResponse.getMessage().getTextContent(),
                "onLLMResponse 应包含 LLM 的实际回复文本");
        }

        @Test
        @DisplayName("onAgentComplete 收到的 AgentResponse 包含正确文本")
        void shouldReceiveAgentCompletePayload() {
            MockLLMProvider llm = new MockLLMProvider();
            llm.setNextResponse("一切都会好起来的。");
            SoulComfortAgent agent = new SoulComfortAgent(llm);
            PayloadCapturingObserver observer = new PayloadCapturingObserver();
            agent.setObserver(observer);

            agent.chat("s1", "你好");

            assertNotNull(observer.capturedAgentResponse);
            assertEquals("一切都会好起来的。", observer.capturedAgentResponse.getText(),
                "onAgentComplete 的 AgentResponse 应包含最终文本");
        }

        @Test
        @DisplayName("onError 收到的异常是实际抛出的异常")
        void shouldReceiveActualException() {
            MockLLMProvider llm = new MockLLMProvider();
            RuntimeException expected = new RuntimeException("连接超时");
            llm.setThrowOnComplete(expected);
            SoulComfortAgent agent = new SoulComfortAgent(llm);
            PayloadCapturingObserver observer = new PayloadCapturingObserver();
            agent.setObserver(observer);

            assertThrows(RuntimeException.class, () -> agent.chat("s1", "你好"));

            assertNotNull(observer.capturedError);
            assertSame(expected, observer.capturedError,
                "onError 收到的应是原始异常对象");
            assertEquals("连接超时", observer.capturedError.getMessage());
        }
    }

    // ==================== 最大迭代次数错误消息验证 ====================

    @Nested
    @DisplayName("最大工具迭代异常消息")
    class MaxToolIterationsMessageTest {

        @Test
        @DisplayName("异常消息包含 'Max tool iterations (5)'")
        void shouldContainMaxIterationsInErrorMessage() {
            MockLLMProvider llm = new MockLLMProvider();
            Tool loopTool = new MockTool("loop", "循环工具", args -> "继续");
            MemoryProvider mp = new InMemoryProvider();
            PromptEngine pe = PromptEngine.builder()
                .memoryProvider(mp).baseSystemPrompt("助手").build();
            SoulComfortAgent agent = new SoulComfortAgent(llm, pe, List.of(loopTool));

            for (int i = 0; i < 10; i++) {
                llm.addToolCallResponse("loop", Map.of());
            }

            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> agent.chat("s1", "开始"));
            assertTrue(ex.getMessage().contains("Max tool iterations (5)"),
                "异常消息应包含 'Max tool iterations (5)'，实际: " + ex.getMessage());
        }
    }

    // ==================== OpenClaw PromptEngine 调用验证（Mockito）====================

    @Nested
    @DisplayName("OpenClaw PromptEngine 调用验证")
    @ExtendWith(MockitoExtension.class)
    class OpenClawPromptEngineVerificationTest {

        @Test
        @DisplayName("PromptEngine.build() 被调用时传入正确的 sessionId 和 userMessage")
        void shouldCallPromptEngineBuildWithCorrectParams() {
            // 准备 mock MemoryProvider
            MemoryProvider mockMp = mock(MemoryProvider.class);
            when(mockMp.getHistory(anyString(), anyInt())).thenReturn(List.of());

            // 准备 mock PromptEngine（使用 spy 以验证调用）
            PromptEngine realPe = PromptEngine.builder()
                .memoryProvider(mockMp)
                .baseSystemPrompt("你是助手。")
                .build();
            PromptEngine spyPe = spy(realPe);

            // 准备 mock LLMProvider
            LLMProvider mockLlm = mock(LLMProvider.class);
            when(mockLlm.complete(anyList(), any(LLMOptions.class))).thenReturn(
                LLMResponse.builder()
                    .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("回复").build())
                    .stopReason("end_turn")
                    .build());

            SoulComfortAgent agent = new SoulComfortAgent(mockLlm, spyPe);
            agent.chat("my-session-123", "你好世界");

            // 捕获 PromptEngine.build() 的参数
            ArgumentCaptor<PromptRequest> captor = ArgumentCaptor.forClass(PromptRequest.class);
            verify(spyPe).build(captor.capture());

            PromptRequest capturedRequest = captor.getValue();
            assertEquals("my-session-123", capturedRequest.getSessionId(),
                "PromptRequest 的 sessionId 应与调用参数一致");
            assertEquals("你好世界", capturedRequest.getUserMessage(),
                "PromptRequest 的 userMessage 应与调用参数一致");
        }
    }

    // ==================== chatStreamReactive 测试 ====================

    @Nested
    @DisplayName("Reactive 流式对话")
    class ReactiveStreamingTest {

        @Test
        @DisplayName("chatStreamReactive 无 ToolCallingLoop 时回退到 callback 桥接")
        void shouldFallbackToCallbackBridgeWithoutToolCallingLoop() {
            MockLLMProvider llm = new MockLLMProvider();
            llm.setStreamResponse("你", "好", "！");
            // 兼容模式构造，无 ToolCallingLoop
            SoulComfortAgent agent = new SoulComfortAgent(llm);

            Flux<StreamEvent> flux = agent.chatStreamReactive("s1", "你好");

            List<StreamEvent> events = new ArrayList<>();
            StepVerifier.create(flux)
                .recordWith(() -> events)
                .expectNextCount(4) // 3 TEXT_DELTA + 1 LLM_COMPLETE
                .verifyComplete();

            // 验证 TEXT_DELTA 事件携带正确的文本片段
            List<String> deltas = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA)
                .map(StreamEvent::getTextDelta)
                .toList();
            assertEquals(List.of("你", "好", "！"), deltas,
                "TEXT_DELTA 事件应按顺序携带正确的文本片段");

            // 验证 LLM_COMPLETE 事件
            StreamEvent completeEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE)
                .findFirst()
                .orElse(null);
            assertNotNull(completeEvent, "应有 LLM_COMPLETE 事件");
            assertNotNull(completeEvent.getResponse(), "LLM_COMPLETE 应携带 LLMResponse");
            assertEquals("你好！", completeEvent.getResponse().getMessage().getTextContent(),
                "LLM_COMPLETE 的 response 应包含完整文本");
        }

        @Test
        @DisplayName("chatStreamReactive 有 ToolCallingLoop 时走 Reactive 管道")
        void shouldUseToolCallingLoopWhenProvided() {
            // 准备 mock LLMProvider — 直接返回文本（无工具调用）
            LLMProvider mockLlm = mock(LLMProvider.class);
            // 提供 providerName 以避免 NPE in toString
            when(mockLlm.getProviderName()).thenReturn("mock");

            LLMResponse finalResponse = LLMResponse.builder()
                .message(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT)
                    .textContent("reactive回复").build())
                .stopReason("end_turn")
                .build();

            // ToolCallingLoop.executeWithToolsReactive 返回 TEXT_DELTA + LLM_COMPLETE
            Flux<StreamEvent> mockFlux = Flux.just(
                StreamEvent.textDelta("reactive"),
                StreamEvent.textDelta("回复"),
                StreamEvent.llmComplete(finalResponse)
            );

            // 准备 mock ToolCallingLoop
            ToolCallingLoop mockLoop = mock(ToolCallingLoop.class);
            when(mockLoop.executeWithToolsReactive(anyList(), any(LLMOptions.class)))
                .thenReturn(mockFlux);

            // 准备 PromptEngine（需要真实的以提供 memoryProvider）
            MemoryProvider mp = new InMemoryProvider();
            PromptEngine pe = PromptEngine.builder()
                .memoryProvider(mp)
                .baseSystemPrompt("助手")
                .build();

            // 使用完整构造器：(LLMProvider, PromptEngine, List<Tool>, ToolCallingLoop)
            // 需要真实的 LLMProvider（因为构造器中 ReflectionService 需要它）
            // 但 chat 调用走 ToolCallingLoop，所以 mockLlm 用于构造即可
            // 但 PromptEngine.build 需要 LLM 不被调用 — 让 mockLlm.complete 返回默认值
            when(mockLlm.complete(anyList(), any(LLMOptions.class))).thenReturn(finalResponse);

            SoulComfortAgent agent = new SoulComfortAgent(mockLlm, pe, List.of(), mockLoop);

            Flux<StreamEvent> resultFlux = agent.chatStreamReactive("s1", "你好");

            List<StreamEvent> events = new ArrayList<>();
            StepVerifier.create(resultFlux)
                .recordWith(() -> events)
                .expectNextCount(3) // 2 TEXT_DELTA + 1 LLM_COMPLETE
                .verifyComplete();

            // 验证走了 ToolCallingLoop 的 Reactive 路径
            verify(mockLoop).executeWithToolsReactive(anyList(), any(LLMOptions.class));

            // 验证事件内容
            List<String> deltas = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA)
                .map(StreamEvent::getTextDelta)
                .toList();
            assertEquals(List.of("reactive", "回复"), deltas);
        }

        @Test
        @DisplayName("chatStreamReactive 保存助手回复到 MemoryProvider")
        void shouldSaveAssistantMessageAfterReactiveStream() {
            MockLLMProvider llm = new MockLLMProvider();
            llm.setStreamResponse("保存", "测试");
            SoulComfortAgent agent = new SoulComfortAgent(llm);

            Flux<StreamEvent> flux = agent.chatStreamReactive("reactive-save-s1", "你好");

            // 消费完 Flux
            StepVerifier.create(flux)
                .expectNextCount(3) // 2 TEXT_DELTA + 1 LLM_COMPLETE
                .verifyComplete();

            // 验证消息保存到了 ConversationMemory
            ConversationMemory memory = agent.getMemory();
            List<ConversationMessage> history = memory.getRecentMessages("reactive-save-s1", 10);
            assertTrue(history.size() >= 2, "应保存 user 和 assistant 消息");
            boolean hasAssistantMsg = history.stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.ASSISTANT
                    && "保存测试".equals(m.getTextContent()));
            assertTrue(hasAssistantMsg, "应保存完整的助手回复文本 '保存测试'");
        }
    }

    // ==================== clearSession 委托验证 ====================

    @Nested
    @DisplayName("clearSession 委托")
    class ClearSessionDelegationTest {

        @Test
        @DisplayName("兼容模式下 clearSession 委托给 ConversationMemory")
        void shouldDelegateToCovnersationMemoryInFallbackMode() {
            MockLLMProvider llm = new MockLLMProvider();
            llm.setNextResponse("你好！");
            ConversationMemory externalMemory = new ConversationMemory(10);
            SoulComfortAgent agent = new SoulComfortAgent(llm, externalMemory);

            agent.chat("clear-test-s1", "你好");
            assertFalse(externalMemory.getHistory("clear-test-s1").isEmpty(),
                "对话后应有历史记录");

            agent.clearSession("clear-test-s1");
            assertTrue(externalMemory.getHistory("clear-test-s1").isEmpty(),
                "clearSession 后历史记录应为空");
        }

        @Test
        @DisplayName("OpenClaw 模式下 clearSession 委托给 MemoryProvider")
        void shouldDelegateToMemoryProviderInOpenClawMode() {
            MockLLMProvider llm = new MockLLMProvider();
            llm.setNextResponse("你好！");
            MemoryProvider mp = new InMemoryProvider();
            PromptEngine pe = PromptEngine.builder()
                .memoryProvider(mp)
                .baseSystemPrompt("助手")
                .build();
            SoulComfortAgent agent = new SoulComfortAgent(llm, pe);

            agent.chat("clear-test-s2", "你好");
            assertFalse(mp.getHistory("clear-test-s2", 10).isEmpty(),
                "对话后 MemoryProvider 应有历史记录");

            agent.clearSession("clear-test-s2");
            assertTrue(mp.getHistory("clear-test-s2", 10).isEmpty(),
                "clearSession 后 MemoryProvider 历史记录应为空");
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

        void addRawResponse(LLMResponse response) {
            responseQueue.add(response);
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
            if (next instanceof LLMResponse raw) {
                return raw;
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

    /**
     * 捕获完整 payload 的观察者（用于验证回调参数的具体内容）
     */
    static class PayloadCapturingObserver implements AgentObserver {
        String capturedUserMessage;
        String capturedSessionId;
        List<ConversationMessage> capturedLLMRequestMessages;
        LLMResponse capturedLLMResponse;
        AgentResponse capturedAgentResponse;
        Throwable capturedError;

        @Override
        public void onAgentStart(String userMessage, String sessionId) {
            this.capturedUserMessage = userMessage;
            this.capturedSessionId = sessionId;
        }

        @Override
        public void onLLMRequest(List<ConversationMessage> messages) {
            this.capturedLLMRequestMessages = new ArrayList<>(messages);
        }

        @Override
        public void onLLMResponse(LLMResponse response) {
            this.capturedLLMResponse = response;
        }

        @Override
        public void onAgentComplete(AgentResponse response) {
            this.capturedAgentResponse = response;
        }

        @Override
        public void onError(Throwable error) {
            this.capturedError = error;
        }
    }
}
