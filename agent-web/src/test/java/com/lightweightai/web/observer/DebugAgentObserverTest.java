package com.lightweightai.web.observer;

import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.PromptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DebugAgentObserver - collects debug information across the agent lifecycle")
class DebugAgentObserverTest {

    private DebugAgentObserver observer;

    @BeforeEach
    void setUp() {
        observer = new DebugAgentObserver();
    }

    // ==================== onAgentStart ====================

    @Nested
    @DisplayName("onAgentStart")
    class OnAgentStart {

        @Test
        @DisplayName("Sets sessionId, userInput, and startTime in debug info")
        void shouldCaptureStartInfo() {
            observer.onAgentStart("Hello, what is AI?", "session-42");

            DebugInfo info = observer.getDebugInfo();
            assertEquals("session-42", info.getSessionId());
            assertEquals("Hello, what is AI?", info.getUserInput());
            assertTrue(info.getStartTime() > 0, "startTime should be set to current time");
        }
    }

    // ==================== onPromptBuilt ====================

    @Nested
    @DisplayName("onPromptBuilt")
    class OnPromptBuilt {

        @Test
        @DisplayName("Sets active skills and system prompt preview")
        void shouldCapturePromptInfo() {
            PromptContext context = PromptContext.builder()
                .activeSkillNames(List.of("weather", "calculator"))
                .systemPrompt("You are a helpful assistant.")
                .build();

            observer.onPromptBuilt(context);

            DebugInfo info = observer.getDebugInfo();
            assertEquals(List.of("weather", "calculator"), info.getActiveSkills());
            assertEquals("You are a helpful assistant.", info.getSystemPromptPreview());
        }

        @Test
        @DisplayName("Truncates system prompt preview to 500 characters")
        void shouldTruncateSystemPrompt() {
            String longPrompt = "A".repeat(600);

            PromptContext context = PromptContext.builder()
                .systemPrompt(longPrompt)
                .build();

            observer.onPromptBuilt(context);

            DebugInfo info = observer.getDebugInfo();
            assertNotNull(info.getSystemPromptPreview());
            assertTrue(info.getSystemPromptPreview().length() <= 504,
                "Truncated prompt should be at most 500 chars plus '...' suffix");
        }

        @Test
        @DisplayName("Default empty system prompt sets empty preview")
        void shouldHandleEmptySystemPrompt() {
            PromptContext context = PromptContext.builder()
                .build();

            observer.onPromptBuilt(context);

            DebugInfo info = observer.getDebugInfo();
            // Builder defaults systemPrompt to "" (non-null), so truncate("", 500) returns ""
            assertEquals("", info.getSystemPromptPreview());
        }
    }

    // ==================== onLLMRequest ====================

    @Nested
    @DisplayName("onLLMRequest")
    class OnLLMRequest {

        @Test
        @DisplayName("Adds MessageInfo entries for each conversation message")
        void shouldCaptureRequestMessages() {
            ConversationMessage userMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("What is machine learning?")
                .build();
            ConversationMessage systemMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.SYSTEM)
                .textContent("You are an expert.")
                .build();

            observer.onLLMRequest(List.of(systemMsg, userMsg));

            DebugInfo info = observer.getDebugInfo();
            List<DebugInfo.MessageInfo> messages = info.getRequestMessages();

            assertEquals(2, messages.size());

            assertEquals(0, messages.get(0).getIndex());
            assertEquals("SYSTEM", messages.get(0).getRole());
            assertEquals("You are an expert.", messages.get(0).getContent());

            assertEquals(1, messages.get(1).getIndex());
            assertEquals("USER", messages.get(1).getRole());
            assertEquals("What is machine learning?", messages.get(1).getContent());
        }

        @Test
        @DisplayName("Multiple onLLMRequest calls accumulate messages")
        void shouldAccumulateMessages() {
            ConversationMessage msg1 = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("First")
                .build();
            ConversationMessage msg2 = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("Second")
                .build();

            observer.onLLMRequest(List.of(msg1));
            observer.onLLMRequest(List.of(msg2));

            assertEquals(2, observer.getDebugInfo().getRequestMessages().size());
        }
    }

    // ==================== onLLMResponse ====================

    @Nested
    @DisplayName("onLLMResponse")
    class OnLLMResponse {

        @Test
        @DisplayName("Captures response text, stop reason, and token usage")
        void shouldCaptureResponseDetails() {
            ConversationMessage responseMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("Machine learning is a branch of AI.")
                .build();

            LLMResponse response = LLMResponse.builder()
                .message(responseMsg)
                .stopReason("end_turn")
                .usage(new LLMResponse.UsageInfo(150, 42))
                .build();

            observer.onLLMResponse(response);

            DebugInfo info = observer.getDebugInfo();
            assertEquals("Machine learning is a branch of AI.", info.getResponseText());
            assertEquals("end_turn", info.getStopReason());
            assertEquals(42, info.getResponseTokens());
            assertEquals(150, info.getTotalRequestTokens());
        }

        @Test
        @DisplayName("Handles response with null message gracefully")
        void shouldHandleNullMessage() {
            LLMResponse response = LLMResponse.builder()
                .stopReason("tool_use")
                .build();

            observer.onLLMResponse(response);

            DebugInfo info = observer.getDebugInfo();
            assertNull(info.getResponseText());
            assertEquals("tool_use", info.getStopReason());
        }

        @Test
        @DisplayName("Handles response with null usage gracefully")
        void shouldHandleNullUsage() {
            ConversationMessage msg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("Response text")
                .build();

            LLMResponse response = LLMResponse.builder()
                .message(msg)
                .stopReason("end_turn")
                .build();

            observer.onLLMResponse(response);

            DebugInfo info = observer.getDebugInfo();
            assertEquals("Response text", info.getResponseText());
            assertEquals(0, info.getResponseTokens());
            assertEquals(0, info.getTotalRequestTokens());
        }
    }

    // ==================== onToolCall ====================

    @Nested
    @DisplayName("onToolCall and onToolCallStart")
    class OnToolCall {

        @Test
        @DisplayName("Records tool call with name, args, result, and duration")
        void shouldCaptureToolCallInfo() throws InterruptedException {
            observer.onToolCallStart("weather_check");
            // Simulate some processing time
            Thread.sleep(10);

            Map<String, Object> args = Map.of("city", "Tokyo");
            ToolResult result = ToolResult.success("Sunny, 25C");

            observer.onToolCall("weather_check", args, result);

            DebugInfo info = observer.getDebugInfo();
            List<DebugInfo.ToolCallInfo> toolCalls = info.getToolCalls();

            assertEquals(1, toolCalls.size());
            DebugInfo.ToolCallInfo call = toolCalls.get(0);
            assertEquals("weather_check", call.getToolName());
            assertEquals(Map.of("city", "Tokyo"), call.getArguments());
            assertEquals("Sunny, 25C", call.getResult());
            assertTrue(call.getDuration() >= 0, "Duration should be non-negative");
        }

        @Test
        @DisplayName("Multiple tool calls are accumulated in order")
        void shouldAccumulateToolCalls() {
            observer.onToolCallStart("tool_a");
            observer.onToolCall("tool_a", Map.of(), ToolResult.success("result_a"));

            observer.onToolCallStart("tool_b");
            observer.onToolCall("tool_b", Map.of("key", "val"), ToolResult.success("result_b"));

            List<DebugInfo.ToolCallInfo> toolCalls = observer.getDebugInfo().getToolCalls();
            assertEquals(2, toolCalls.size());
            assertEquals("tool_a", toolCalls.get(0).getToolName());
            assertEquals("tool_b", toolCalls.get(1).getToolName());
            assertEquals("result_b", toolCalls.get(1).getResult());
        }
    }

    // ==================== onAgentComplete ====================

    @Nested
    @DisplayName("onAgentComplete")
    class OnAgentComplete {

        @Test
        @DisplayName("Sets endTime and allows duration calculation")
        void shouldCaptureEndTime() {
            observer.onAgentStart("test input", "s1");

            AgentResponse response = new AgentResponse("Done processing.");

            observer.onAgentComplete(response);

            DebugInfo info = observer.getDebugInfo();
            assertTrue(info.getEndTime() > 0, "endTime should be set");
            assertTrue(info.getDuration() >= 0, "Duration should be non-negative");
            assertNull(info.getError(), "Error should remain null on success");
        }
    }

    // ==================== onError ====================

    @Nested
    @DisplayName("onError")
    class OnError {

        @Test
        @DisplayName("Sets endTime and error message")
        void shouldCaptureError() {
            observer.onAgentStart("test input", "s1");

            observer.onError(new RuntimeException("Something went wrong"));

            DebugInfo info = observer.getDebugInfo();
            assertTrue(info.getEndTime() > 0, "endTime should be set on error");
            assertEquals("Something went wrong", info.getError());
        }

        @Test
        @DisplayName("Duration is calculated even on error path")
        void shouldCalculateDurationOnError() {
            observer.onAgentStart("input", "s1");

            observer.onError(new IllegalArgumentException("bad arg"));

            DebugInfo info = observer.getDebugInfo();
            assertTrue(info.getDuration() >= 0, "Duration should be calculable after error");
        }
    }

    // ==================== Full lifecycle ====================

    @Nested
    @DisplayName("Full lifecycle integration")
    class FullLifecycle {

        @Test
        @DisplayName("Complete agent lifecycle populates all debug info fields")
        void shouldPopulateAllFieldsAcrossLifecycle() {
            // 1. Agent start
            observer.onAgentStart("What is the weather?", "session-123");

            // 2. Prompt built
            PromptContext promptContext = PromptContext.builder()
                .activeSkillNames(List.of("weather-skill"))
                .systemPrompt("You are a weather assistant.")
                .build();
            observer.onPromptBuilt(promptContext);

            // 3. LLM request
            ConversationMessage userMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("What is the weather?")
                .build();
            observer.onLLMRequest(List.of(userMsg));

            // 4. LLM response (with tool_use stop reason)
            ConversationMessage assistantMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("")
                .build();
            LLMResponse llmResponse = LLMResponse.builder()
                .message(assistantMsg)
                .stopReason("tool_use")
                .usage(new LLMResponse.UsageInfo(200, 50))
                .build();
            observer.onLLMResponse(llmResponse);

            // 5. Tool call
            observer.onToolCallStart("get_weather");
            ToolResult toolResult = ToolResult.success("Tokyo: Sunny, 25C");
            observer.onToolCall("get_weather", Map.of("city", "Tokyo"), toolResult);

            // 6. Agent complete
            AgentResponse agentResponse = new AgentResponse("The weather in Tokyo is sunny and 25C.");
            observer.onAgentComplete(agentResponse);

            // Assert everything
            DebugInfo info = observer.getDebugInfo();

            // Start info
            assertEquals("session-123", info.getSessionId());
            assertEquals("What is the weather?", info.getUserInput());
            assertTrue(info.getStartTime() > 0);

            // Prompt info
            assertEquals(List.of("weather-skill"), info.getActiveSkills());
            assertEquals("You are a weather assistant.", info.getSystemPromptPreview());

            // Request messages
            assertEquals(1, info.getRequestMessages().size());
            assertEquals("USER", info.getRequestMessages().get(0).getRole());
            assertEquals("What is the weather?", info.getRequestMessages().get(0).getContent());

            // Response info
            assertEquals("tool_use", info.getStopReason());
            assertEquals(50, info.getResponseTokens());
            assertEquals(200, info.getTotalRequestTokens());

            // Tool calls
            assertEquals(1, info.getToolCalls().size());
            assertEquals("get_weather", info.getToolCalls().get(0).getToolName());
            assertEquals("Tokyo: Sunny, 25C", info.getToolCalls().get(0).getResult());
            assertEquals(Map.of("city", "Tokyo"), info.getToolCalls().get(0).getArguments());

            // Completion
            assertTrue(info.getEndTime() > 0);
            assertTrue(info.getDuration() >= 0);
            assertNull(info.getError());
        }

        @Test
        @DisplayName("Error lifecycle sets error instead of completing normally")
        void shouldHandleErrorLifecycle() {
            observer.onAgentStart("broken input", "session-err");

            PromptContext context = PromptContext.builder()
                .systemPrompt("System prompt here")
                .build();
            observer.onPromptBuilt(context);

            observer.onError(new RuntimeException("LLM timed out"));

            DebugInfo info = observer.getDebugInfo();
            assertEquals("session-err", info.getSessionId());
            assertEquals("broken input", info.getUserInput());
            assertEquals("LLM timed out", info.getError());
            assertTrue(info.getEndTime() > 0);
            assertTrue(info.getDuration() >= 0);
        }
    }

    // ==================== getDebugInfo ====================

    @Test
    @DisplayName("getDebugInfo returns same instance each call")
    void shouldReturnSameDebugInfoInstance() {
        DebugInfo first = observer.getDebugInfo();
        DebugInfo second = observer.getDebugInfo();

        assertSame(first, second);
    }

    @Test
    @DisplayName("Fresh observer has empty debug info")
    void shouldStartWithEmptyDebugInfo() {
        DebugInfo info = observer.getDebugInfo();

        assertNotNull(info);
        assertEquals(0, info.getStartTime());
        assertEquals(0, info.getEndTime());
        assertNull(info.getSessionId());
        assertNull(info.getUserInput());
        assertTrue(info.getRequestMessages().isEmpty());
        assertTrue(info.getToolCalls().isEmpty());
        assertTrue(info.getActiveSkills().isEmpty());
        assertNull(info.getSystemPromptPreview());
        assertNull(info.getResponseText());
        assertNull(info.getStopReason());
        assertEquals(0, info.getResponseTokens());
        assertEquals(0, info.getTotalRequestTokens());
        assertNull(info.getError());
    }
}
