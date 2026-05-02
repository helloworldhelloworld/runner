package com.lightweightai.web.observer;

import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.PromptContext;
import com.lightweightai.kernel.prompt.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DebugAgentObserver - Lifecycle event collection for debugging")
class DebugAgentObserverTest {

    private DebugAgentObserver observer;

    @BeforeEach
    void setUp() {
        observer = new DebugAgentObserver();
    }

    @Test
    @DisplayName("onAgentStart records sessionId and userInput")
    void onAgentStartRecordsFields() {
        observer.onAgentStart("hello", "sess-1");

        DebugInfo info = observer.getDebugInfo();
        assertEquals("sess-1", info.getSessionId());
        assertEquals("hello", info.getUserInput());
        assertTrue(info.getStartTime() > 0);
    }

    @Test
    @DisplayName("onPromptBuilt records active skills and system prompt preview")
    void onPromptBuiltRecordsSkills() {
        PromptContext context = PromptContext.builder()
                .systemPrompt("You are a helpful assistant")
                .userMessage("hi")
                .activeSkillNames(List.of("weather", "math"))
                .tools(List.of())
                .build();

        observer.onPromptBuilt(context);

        DebugInfo info = observer.getDebugInfo();
        assertEquals(List.of("weather", "math"), info.getActiveSkills());
        assertNotNull(info.getSystemPromptPreview());
        assertTrue(info.getSystemPromptPreview().contains("helpful"));
    }

    @Test
    @DisplayName("onLLMRequest records messages")
    void onLLMRequestRecordsMessages() {
        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent("hi")
                        .build(),
                ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("hello")
                        .build()
        );

        observer.onLLMRequest(messages);

        DebugInfo info = observer.getDebugInfo();
        assertEquals(2, info.getRequestMessages().size());
        assertEquals("USER", info.getRequestMessages().get(0).getRole());
        assertEquals("hi", info.getRequestMessages().get(0).getContent());
    }

    @Test
    @DisplayName("onLLMResponse records response text, stopReason and tokens")
    void onLLMResponseRecordsPayload() {
        LLMResponse response = LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("answer text")
                        .build())
                .stopReason("end_turn")
                .usage(new LLMResponse.UsageInfo(100, 50))
                .build();

        observer.onLLMResponse(response);

        DebugInfo info = observer.getDebugInfo();
        assertEquals("answer text", info.getResponseText());
        assertEquals("end_turn", info.getStopReason());
        assertEquals(50, info.getResponseTokens());
        assertEquals(100, info.getTotalRequestTokens());
    }

    @Test
    @DisplayName("onToolCall records tool call info with duration")
    void onToolCallRecordsInfo() {
        observer.onToolCallStart("calc");
        ToolResult result = ToolResult.success("42");

        observer.onToolCall("calc", Map.of("x", 1), result);

        DebugInfo info = observer.getDebugInfo();
        assertEquals(1, info.getToolCalls().size());

        DebugInfo.ToolCallInfo tc = info.getToolCalls().get(0);
        assertEquals("calc", tc.getToolName());
        assertEquals("42", tc.getResult());
        assertTrue(tc.getDuration() >= 0);
    }

    @Test
    @DisplayName("onAgentComplete records end time and duration")
    void onAgentCompleteRecordsTiming() {
        observer.onAgentStart("hi", "s-1");
        AgentResponse resp = new AgentResponse("done");

        observer.onAgentComplete(resp);

        DebugInfo info = observer.getDebugInfo();
        assertTrue(info.getEndTime() > 0);
        assertTrue(info.getDuration() >= 0);
    }

    @Test
    @DisplayName("onError records error message and end time")
    void onErrorRecords() {
        observer.onAgentStart("hi", "s-1");

        observer.onError(new RuntimeException("boom"));

        DebugInfo info = observer.getDebugInfo();
        assertEquals("boom", info.getError());
        assertTrue(info.getEndTime() > 0);
    }

    @Test
    @DisplayName("full lifecycle records all events")
    void fullLifecycle() {
        observer.onAgentStart("hello", "sess-full");

        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent("hello")
                        .build()
        );
        observer.onLLMRequest(messages);

        LLMResponse response = LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("hi there")
                        .build())
                .stopReason("end_turn")
                .build();
        observer.onLLMResponse(response);

        observer.onAgentComplete(new AgentResponse("hi there"));

        DebugInfo info = observer.getDebugInfo();
        assertEquals("sess-full", info.getSessionId());
        assertEquals("hello", info.getUserInput());
        assertEquals("hi there", info.getResponseText());
        assertEquals(1, info.getRequestMessages().size());
        assertTrue(info.getDuration() >= 0);
        assertNull(info.getError());
    }
}
