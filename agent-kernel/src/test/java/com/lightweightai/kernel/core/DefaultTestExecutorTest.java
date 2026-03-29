package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultTestExecutor - 核心测试执行器")
class DefaultTestExecutorTest {

    private ReactiveMockProvider mockProvider;
    private DefaultTestExecutor executor;

    @BeforeEach
    void setUp() {
        mockProvider = new ReactiveMockProvider();
        executor = new DefaultTestExecutor(mockProvider, 3);
    }

    @Test
    @DisplayName("执行测试用例 - 发射 TEXT_DELTA 事件")
    void executeTestCase_emitsTextDeltaEvents() {
        mockProvider.addStreamResponse(List.of(
            StreamEvent.textDelta("Hello "),
            StreamEvent.textDelta("World"),
            StreamEvent.llmComplete(createTextResponse("Hello World"))
        ));

        SkillTestContext context = SkillTestContext.builder()
            .testCaseId("tc-1")
            .systemPrompt("You are a test skill.")
            .userInput("say hello")
            .build();

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(executor.executeTestCase(context))
            .recordWith(() -> events)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        long textDeltas = events.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA)
            .count();
        assertTrue(textDeltas >= 2, "Should have at least 2 TEXT_DELTA events");
    }

    @Test
    @DisplayName("执行测试用例 - 工具调用产生 TOOL_CALL_START 和 TOOL_RESULT 事件")
    void executeTestCase_withToolCall_emitsToolResultEvent() {
        // Round 1: LLM calls GetPoi
        mockProvider.addStreamResponse(List.of(
            StreamEvent.llmComplete(createToolCallResponse("GetPoi", Map.of("keyword", "故宫")))
        ));
        // Round 2: LLM final text
        mockProvider.addStreamResponse(List.of(
            StreamEvent.textDelta("找到了故宫"),
            StreamEvent.llmComplete(createTextResponse("找到了故宫"))
        ));

        SkillTestContext context = SkillTestContext.builder()
            .testCaseId("tc-2")
            .systemPrompt("You are a navigation skill.")
            .userInput("帮我找故宫")
            .mockResponses(Map.of("GetPoi", Map.of("status", "ok", "name", "故宫")))
            .build();

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(executor.executeTestCase(context))
            .recordWith(() -> events)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        List<StreamEvent.EventType> types = events.stream().map(StreamEvent::getType).toList();
        assertTrue(types.contains(StreamEvent.EventType.TOOL_CALL_START), "Should have TOOL_CALL_START");
        assertTrue(types.contains(StreamEvent.EventType.TOOL_RESULT), "Should have TOOL_RESULT");
    }

    @Test
    @DisplayName("执行测试用例 - 流尾部发射 test_case_result 汇总事件")
    void executeTestCase_emitsTestCaseResultAtEnd() {
        mockProvider.addStreamResponse(List.of(
            StreamEvent.textDelta("response text"),
            StreamEvent.llmComplete(createTextResponse("response text"))
        ));

        SkillTestContext context = SkillTestContext.builder()
            .testCaseId("tc-3")
            .userInput("test input")
            .build();

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(executor.executeTestCase(context))
            .recordWith(() -> events)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        // Last event should be POST_PROCESS_DATA with category "test_case_result"
        StreamEvent lastEvent = events.get(events.size() - 1);
        assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, lastEvent.getType());
        assertEquals("test_case_result", lastEvent.getCategory());

        Map<String, Object> data = lastEvent.getData();
        assertEquals("tc-3", data.get("testCaseId"));
        assertEquals("response text", data.get("llmOutput"));
        assertNotNull(data.get("durationMs"));
        assertNull(data.get("error"));
    }

    @Test
    @DisplayName("执行测试用例 - 工具调用信息包含在汇总事件中")
    @SuppressWarnings("unchecked")
    void executeTestCase_toolCallsRecordedInResult() {
        // Round 1: tool call
        mockProvider.addStreamResponse(List.of(
            StreamEvent.llmComplete(createToolCallResponse("Navigate", Map.of("dest", "北京")))
        ));
        // Round 2: final
        mockProvider.addStreamResponse(List.of(
            StreamEvent.textDelta("已导航"),
            StreamEvent.llmComplete(createTextResponse("已导航"))
        ));

        SkillTestContext context = SkillTestContext.builder()
            .testCaseId("tc-4")
            .userInput("导航去北京")
            .mockResponses(Map.of("Navigate", Map.of("status", "ok")))
            .build();

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(executor.executeTestCase(context))
            .recordWith(() -> events)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        StreamEvent resultEvent = events.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.POST_PROCESS_DATA
                && "test_case_result".equals(e.getCategory()))
            .findFirst().orElseThrow();

        List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) resultEvent.getData().get("toolCalls");
        assertFalse(toolCalls.isEmpty(), "Should have recorded tool calls");
        assertEquals("Navigate", toolCalls.get(0).get("name"));
    }

    @Test
    @DisplayName("LLM 异常 - 不崩溃，发射包含 error 的汇总事件")
    void executeTestCase_llmError_emitsErrorInResult() {
        mockProvider.setShouldFail(true);

        SkillTestContext context = SkillTestContext.builder()
            .testCaseId("tc-5")
            .userInput("trigger error")
            .build();

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(executor.executeTestCase(context))
            .recordWith(() -> events)
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        // Should have a result event with error info, not throw
        StreamEvent resultEvent = events.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.POST_PROCESS_DATA
                && "test_case_result".equals(e.getCategory()))
            .findFirst().orElseThrow();

        assertNotNull(resultEvent.getData().get("error"), "Should contain error message");
    }

    @Test
    @DisplayName("带记忆上下文的测试用例")
    void executeTestCase_withMemoryContext() {
        mockProvider.addStreamResponse(List.of(
            StreamEvent.textDelta("based on memory"),
            StreamEvent.llmComplete(createTextResponse("based on memory"))
        ));

        SkillTestContext context = SkillTestContext.builder()
            .testCaseId("tc-6")
            .systemPrompt("You are a skill.")
            .userInput("what do you remember?")
            .memoryContext("User prefers Chinese restaurants.")
            .build();

        StepVerifier.create(executor.executeTestCase(context))
            .thenConsumeWhile(e -> true)
            .verifyComplete();

        // Verify system message was built with memory context
        List<ConversationMessage> lastMessages = mockProvider.getLastMessages();
        assertNotNull(lastMessages);
        String systemContent = lastMessages.get(0).getTextContent();
        assertTrue(systemContent.contains("Chinese restaurants"), "Memory context should be in system prompt");
    }

    // ==================== Helper methods ====================

    private int callCounter = 0;

    private LLMResponse createTextResponse(String text) {
        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text)
                .build())
            .build();
    }

    private LLMResponse createToolCallResponse(String toolName, Map<String, Object> args) {
        ToolCall toolCall = new ToolCall("call_" + (++callCounter), toolName, args);
        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("")
                .build())
            .toolCalls(List.of(toolCall))
            .build();
    }

    /**
     * Mock LLM Provider that returns pre-configured reactive streams
     */
    private static class ReactiveMockProvider implements LLMProvider {
        private final Queue<List<StreamEvent>> streamResponses = new LinkedList<>();
        private boolean shouldFail = false;
        private List<ConversationMessage> lastMessages;

        void addStreamResponse(List<StreamEvent> events) {
            streamResponses.add(events);
        }

        void setShouldFail(boolean fail) {
            this.shouldFail = fail;
        }

        List<ConversationMessage> getLastMessages() {
            return lastMessages;
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            this.lastMessages = new ArrayList<>(messages);
            if (shouldFail) {
                return Flux.error(new RuntimeException("LLM mock failure"));
            }
            List<StreamEvent> events = streamResponses.poll();
            if (events == null) {
                return Flux.error(new RuntimeException("No more mock stream responses"));
            }
            return Flux.fromIterable(events);
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException("Use reactive path");
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException("Use reactive path");
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            throw new UnsupportedOperationException("Use reactive path");
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "test-mock"; }
    }
}
