package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentLoop Reactive 流式执行路径测试
 *
 * 覆盖关键路径：
 * - runReactive 基本流式输出
 * - TEXT_DELTA 聚合后写入记忆
 * - 记忆保存失败不阻断流
 * - 多轮对话记忆检索
 * - 工具调用的流式事件
 */
@DisplayName("AgentLoop - Reactive 流式执行")
class AgentLoopReactiveTest {

    private MemoryProvider memory;
    private ReactiveMockLLMProvider llmProvider;
    private AgentLoop agentLoop;

    @BeforeEach
    void setUp() {
        memory = new InMemoryProvider();
        llmProvider = new ReactiveMockLLMProvider();
        agentLoop = AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memory)
            .systemPrompt("你是一个友好的助手。")
            .build();
    }

    @Test
    @DisplayName("基本流式输出 - TEXT_DELTA 事件")
    void shouldStreamTextDeltas() {
        llmProvider.setStreamEvents(List.of(
            StreamEvent.textDelta("你"),
            StreamEvent.textDelta("好"),
            StreamEvent.textDelta("！"),
            StreamEvent.llmComplete(createTextResponse("你好！"))
        ));

        Flux<StreamEvent> flux = agentLoop.runReactive("测试", "session-1");

        List<String> textDeltas = new ArrayList<>();
        StepVerifier.create(flux)
            .thenConsumeWhile(event -> {
                if (event.getType() == StreamEvent.EventType.TEXT_DELTA) {
                    textDeltas.add(event.getTextDelta());
                }
                return true;
            })
            .verifyComplete();

        assertEquals(List.of("你", "好", "！"), textDeltas);
    }

    @Test
    @DisplayName("流完成后将响应写入记忆")
    void shouldSaveResponseToMemoryOnComplete() {
        llmProvider.setStreamEvents(List.of(
            StreamEvent.textDelta("回复内容"),
            StreamEvent.llmComplete(createTextResponse("回复内容"))
        ));

        Flux<StreamEvent> flux = agentLoop.runReactive("用户消息", "session-1");

        // Subscribe and wait for completion
        StepVerifier.create(flux)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        // Check memory was updated
        List<Message> history = memory.getHistory("session-1", 10);
        assertEquals(2, history.size()); // user + assistant
        assertEquals("用户消息", history.get(0).getContent());
        assertEquals("回复内容", history.get(1).getContent());
    }

    @Test
    @DisplayName("空响应不写入助手消息")
    void shouldNotSaveEmptyResponse() {
        llmProvider.setStreamEvents(List.of(
            StreamEvent.llmComplete(createTextResponse(""))
        ));

        Flux<StreamEvent> flux = agentLoop.runReactive("test", "session-1");
        StepVerifier.create(flux)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        List<Message> history = memory.getHistory("session-1", 10);
        // Only user message should be saved, not empty assistant
        assertEquals(1, history.size());
        assertEquals("user", history.get(0).getRole());
    }

    @Test
    @DisplayName("带工具的流式执行 - 包含工具事件")
    void shouldStreamToolEvents() {
        Tool echoTool = new Tool() {
            public String getName() { return "echo"; }
            public String getDescription() { return "Echo"; }
            public ToolSchema getSchema() { return ToolSchema.empty(); }
            public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echoed");
            }
        };

        AgentLoop loopWithTool = AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memory)
            .addTool(echoTool)
            .systemPrompt("test")
            .build();

        // Round 1: tool call
        ToolCall toolCall = new ToolCall("tc1", "echo", Map.of("input", "hi"));
        LLMResponse toolCallResponse = LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("")
                .build())
            .toolCalls(List.of(toolCall))
            .build();

        llmProvider.setStreamEvents(List.of(
            StreamEvent.llmComplete(toolCallResponse)
        ));

        // Round 2: final response
        llmProvider.addStreamEvents(List.of(
            StreamEvent.textDelta("done"),
            StreamEvent.llmComplete(createTextResponse("done"))
        ));

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(loopWithTool.runReactive("echo hi", "session-1"))
            .recordWith(() -> events)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        // Should have tool-related events
        boolean hasToolStart = events.stream()
            .anyMatch(e -> e.getType() == StreamEvent.EventType.TOOL_CALL_START);
        boolean hasToolResult = events.stream()
            .anyMatch(e -> e.getType() == StreamEvent.EventType.TOOL_RESULT);
        assertTrue(hasToolStart, "Should have TOOL_CALL_START event");
        assertTrue(hasToolResult, "Should have TOOL_RESULT event");
    }

    @Test
    @DisplayName("Builder 必须提供 llmProvider")
    void shouldRequireLlmProvider() {
        assertThrows(NullPointerException.class, () ->
            AgentLoop.builder()
                .memoryProvider(memory)
                .build());
    }

    @Test
    @DisplayName("Builder 必须提供 memoryProvider")
    void shouldRequireMemoryProvider() {
        assertThrows(NullPointerException.class, () ->
            AgentLoop.builder()
                .llmProvider(llmProvider)
                .build());
    }

    // ==================== 辅助方法 ====================

    private LLMResponse createTextResponse(String text) {
        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text)
                .build())
            .stopReason("stop")
            .build();
    }

    /**
     * LLM Provider that returns pre-configured reactive streams
     */
    static class ReactiveMockLLMProvider implements LLMProvider {
        private final List<List<StreamEvent>> streamQueue = new ArrayList<>();
        private int streamIndex = 0;

        void setStreamEvents(List<StreamEvent> events) {
            streamQueue.clear();
            streamQueue.add(events);
            streamIndex = 0;
        }

        void addStreamEvents(List<StreamEvent> events) {
            streamQueue.add(events);
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            if (streamIndex >= streamQueue.size()) {
                return Flux.error(new RuntimeException("No more mock stream responses"));
            }
            List<StreamEvent> events = streamQueue.get(streamIndex++);
            return Flux.fromIterable(events);
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException("Use reactive path");
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            throw new UnsupportedOperationException("Use reactive path");
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "reactive-mock"; }
    }
}
