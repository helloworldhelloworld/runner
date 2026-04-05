package com.lightweightai.web.observer;

import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.PromptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DebugAgentObserver - collects LLM call info for debugging")
class DebugAgentObserverTest {

    private DebugAgentObserver observer;

    @BeforeEach
    void setUp() {
        observer = new DebugAgentObserver();
    }

    @Test
    @DisplayName("onAgentStart records session and input")
    void onAgentStartRecordsSessionAndInput() {
        observer.onAgentStart("Hello world", "sess-1");

        DebugInfo info = observer.getDebugInfo();
        assertEquals("sess-1", info.getSessionId());
        assertEquals("Hello world", info.getUserInput());
        assertTrue(info.getStartTime() > 0);
    }

    @Test
    @DisplayName("onPromptBuilt records active skills and system prompt preview")
    void onPromptBuiltRecordsSkillsAndPrompt() {
        PromptContext context = PromptContext.builder()
            .systemPrompt("You are a helpful assistant. Be kind and supportive.")
            .activeSkillNames(List.of("soul-comfort", "memory"))
            .build();

        observer.onPromptBuilt(context);

        DebugInfo info = observer.getDebugInfo();
        assertEquals(List.of("soul-comfort", "memory"), info.getActiveSkills());
        assertTrue(info.getSystemPromptPreview().contains("helpful assistant"));
    }

    @Test
    @DisplayName("onLLMRequest records all messages")
    void onLLMRequestRecordsMessages() {
        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.SYSTEM)
                .textContent("System prompt")
                .build(),
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Hello")
                .build()
        );

        observer.onLLMRequest(messages);

        DebugInfo info = observer.getDebugInfo();
        assertEquals(2, info.getRequestMessages().size());
        assertEquals("SYSTEM", info.getRequestMessages().get(0).getRole());
        assertEquals("System prompt", info.getRequestMessages().get(0).getContent());
        assertEquals(0, info.getRequestMessages().get(0).getIndex());
        assertEquals("USER", info.getRequestMessages().get(1).getRole());
        assertEquals(1, info.getRequestMessages().get(1).getIndex());
    }

    @Test
    @DisplayName("onLLMResponse records response text and stop reason")
    void onLLMResponseRecordsTextAndStopReason() {
        ConversationMessage respMsg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .textContent("Hi there!")
            .build();
        LLMResponse response = LLMResponse.builder()
            .message(respMsg)
            .stopReason("end_turn")
            .build();

        observer.onLLMResponse(response);

        DebugInfo info = observer.getDebugInfo();
        assertEquals("Hi there!", info.getResponseText());
        assertEquals("end_turn", info.getStopReason());
    }

    @Test
    @DisplayName("onLLMResponse records token usage when available")
    void onLLMResponseRecordsTokenUsage() {
        ConversationMessage respMsg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .textContent("response")
            .build();
        LLMResponse response = LLMResponse.builder()
            .message(respMsg)
            .usage(new LLMResponse.UsageInfo(100, 50))
            .build();

        observer.onLLMResponse(response);

        DebugInfo info = observer.getDebugInfo();
        assertEquals(100, info.getTotalRequestTokens());
        assertEquals(50, info.getResponseTokens());
    }

    @Test
    @DisplayName("onToolCall records tool call details")
    void onToolCallRecordsDetails() {
        observer.onToolCallStart("weather");

        ToolResult result = ToolResult.success("tc-1", "Sunny, 25C");

        observer.onToolCall("weather", Map.of("city", "Beijing"), result);

        DebugInfo info = observer.getDebugInfo();
        assertEquals(1, info.getToolCalls().size());
        DebugInfo.ToolCallInfo tc = info.getToolCalls().get(0);
        assertEquals("weather", tc.getToolName());
        assertEquals("Beijing", tc.getArguments().get("city"));
        assertEquals("Sunny, 25C", tc.getResult());
        assertTrue(tc.getDuration() >= 0);
    }

    @Test
    @DisplayName("onAgentComplete records end time and calculates duration")
    void onAgentCompleteRecordsDuration() {
        observer.onAgentStart("test", "sess-2");

        AgentResponse agentResponse = new AgentResponse("Done");
        observer.onAgentComplete(agentResponse);

        DebugInfo info = observer.getDebugInfo();
        assertTrue(info.getEndTime() > 0);
        assertTrue(info.getDuration() >= 0);
    }

    @Test
    @DisplayName("onError records error message and end time")
    void onErrorRecordsErrorAndEndTime() {
        observer.onAgentStart("test", "sess-3");
        observer.onError(new RuntimeException("API timeout"));

        DebugInfo info = observer.getDebugInfo();
        assertEquals("API timeout", info.getError());
        assertTrue(info.getEndTime() > 0);
    }

    @Test
    @DisplayName("full lifecycle collects complete debug info")
    void fullLifecycleCollectsCompleteInfo() {
        // Start
        observer.onAgentStart("What's the weather?", "sess-full");

        // Prompt built
        PromptContext context = PromptContext.builder()
            .systemPrompt("You are an assistant")
            .activeSkillNames(List.of("weather"))
            .build();
        observer.onPromptBuilt(context);

        // LLM request
        observer.onLLMRequest(List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("What's the weather?")
                .build()
        ));

        // LLM response
        ConversationMessage respMsg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .textContent("It's sunny")
            .build();
        observer.onLLMResponse(LLMResponse.builder()
            .message(respMsg)
            .stopReason("end_turn")
            .usage(new LLMResponse.UsageInfo(50, 20))
            .build());

        // Complete
        observer.onAgentComplete(new AgentResponse("It's sunny"));

        DebugInfo info = observer.getDebugInfo();
        assertEquals("sess-full", info.getSessionId());
        assertEquals("What's the weather?", info.getUserInput());
        assertEquals(List.of("weather"), info.getActiveSkills());
        assertEquals(1, info.getRequestMessages().size());
        assertEquals("It's sunny", info.getResponseText());
        assertEquals("end_turn", info.getStopReason());
        assertEquals(50, info.getTotalRequestTokens());
        assertEquals(20, info.getResponseTokens());
        assertTrue(info.getDuration() >= 0);
        assertNull(info.getError());
    }
}
