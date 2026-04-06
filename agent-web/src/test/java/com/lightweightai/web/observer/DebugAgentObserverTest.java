package com.lightweightai.web.observer;

import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.PromptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DebugAgentObserver - 调试观察器")
class DebugAgentObserverTest {

    private DebugAgentObserver observer;

    @BeforeEach
    void setUp() {
        observer = new DebugAgentObserver();
    }

    @Test
    @DisplayName("onAgentStart 记录启动信息")
    void shouldRecordStart() {
        observer.onAgentStart("你好", "session-1");

        DebugInfo info = observer.getDebugInfo();
        assertEquals("session-1", info.getSessionId());
        assertEquals("你好", info.getUserInput());
        assertTrue(info.getStartTime() > 0);
    }

    @Test
    @DisplayName("onPromptBuilt 记录技能和 prompt")
    void shouldRecordPromptInfo() {
        PromptContext ctx = PromptContext.builder()
            .systemPrompt("你是一个助手")
            .activeSkillNames(List.of("weather", "calendar"))
            .userMessage("test")
            .build();

        observer.onPromptBuilt(ctx);

        DebugInfo info = observer.getDebugInfo();
        assertEquals(List.of("weather", "calendar"), info.getActiveSkills());
        assertNotNull(info.getSystemPromptPreview());
    }

    @Test
    @DisplayName("onLLMRequest 记录请求消息")
    void shouldRecordRequestMessages() {
        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.SYSTEM)
                .textContent("system prompt")
                .build(),
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("hello")
                .build()
        );

        observer.onLLMRequest(messages);

        DebugInfo info = observer.getDebugInfo();
        assertEquals(2, info.getRequestMessages().size());
        assertEquals("SYSTEM", info.getRequestMessages().get(0).getRole());
    }

    @Test
    @DisplayName("onLLMResponse 记录响应信息")
    void shouldRecordResponse() {
        LLMResponse response = LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("你好！")
                .build())
            .stopReason("end_turn")
            .build();

        observer.onLLMResponse(response);

        DebugInfo info = observer.getDebugInfo();
        assertEquals("你好！", info.getResponseText());
        assertEquals("end_turn", info.getStopReason());
    }

    @Test
    @DisplayName("onToolCall 记录工具调用")
    void shouldRecordToolCall() {
        observer.onToolCallStart("weather");
        observer.onToolCall("weather", Map.of("city", "北京"), ToolResult.success("晴天 25℃"));

        DebugInfo info = observer.getDebugInfo();
        assertEquals(1, info.getToolCalls().size());
        assertEquals("weather", info.getToolCalls().get(0).getToolName());
    }

    @Test
    @DisplayName("onAgentComplete 记录结束时间")
    void shouldRecordCompletion() {
        observer.onAgentStart("test", "s1");
        AgentResponse response = AgentResponse.builder()
            .text("done")
            .stopReason("end_turn")
            .build();
        observer.onAgentComplete(response);

        DebugInfo info = observer.getDebugInfo();
        assertTrue(info.getEndTime() > 0);
        assertTrue(info.getDuration() >= 0);
    }

    @Test
    @DisplayName("onError 记录错误信息")
    void shouldRecordError() {
        observer.onAgentStart("test", "s1");
        observer.onError(new RuntimeException("network timeout"));

        DebugInfo info = observer.getDebugInfo();
        assertEquals("network timeout", info.getError());
        assertTrue(info.getEndTime() > 0);
    }
}
