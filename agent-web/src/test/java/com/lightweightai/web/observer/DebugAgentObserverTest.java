package com.lightweightai.web.observer;

import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.PromptContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DebugAgentObserver — Agent 可观测性收集器")
class DebugAgentObserverTest {

    private DebugAgentObserver observer;

    @BeforeEach
    void setUp() {
        observer = new DebugAgentObserver();
    }

    @Test
    @DisplayName("onAgentStart 设置 sessionId、userInput 和 startTime")
    void onAgentStartSetsFields() {
        observer.onAgentStart("你好", "sess-1");

        DebugInfo info = observer.getDebugInfo();
        assertEquals("sess-1", info.getSessionId());
        assertEquals("你好", info.getUserInput());
        assertTrue(info.getStartTime() > 0);
    }

    @Test
    @DisplayName("onPromptBuilt 采集 activeSkills 和 systemPromptPreview")
    void onPromptBuiltCollectsSkills() {
        PromptContext ctx = PromptContext.builder()
                .activeSkillNames(List.of("navigation", "weather"))
                .systemPrompt("你是一个导航助手，帮助用户规划路线")
                .userMessage("导航去新街口")
                .build();

        observer.onPromptBuilt(ctx);

        DebugInfo info = observer.getDebugInfo();
        assertEquals(List.of("navigation", "weather"), info.getActiveSkills());
        assertNotNull(info.getSystemPromptPreview());
        assertTrue(info.getSystemPromptPreview().contains("导航助手"));
    }

    @Test
    @DisplayName("onPromptBuilt 长 systemPrompt 截断到 500 字符")
    void longSystemPromptTruncated() {
        String longPrompt = "x".repeat(1000);
        PromptContext ctx = PromptContext.builder()
                .systemPrompt(longPrompt)
                .userMessage("test")
                .build();

        observer.onPromptBuilt(ctx);

        DebugInfo info = observer.getDebugInfo();
        assertTrue(info.getSystemPromptPreview().length() <= 504); // 500 + "..."
    }

    @Test
    @DisplayName("onLLMRequest 采集所有消息（role + content）")
    void onLLMRequestCollectsMessages() {
        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                        .role(MessageRole.SYSTEM)
                        .textContent("system prompt")
                        .build(),
                ConversationMessage.builder()
                        .role(MessageRole.USER)
                        .textContent("hello")
                        .build()
        );

        observer.onLLMRequest(messages);

        DebugInfo info = observer.getDebugInfo();
        assertEquals(2, info.getRequestMessages().size());
        assertEquals("SYSTEM", info.getRequestMessages().get(0).getRole());
        assertEquals("USER", info.getRequestMessages().get(1).getRole());
    }

    @Test
    @DisplayName("onLLMResponse 采集 responseText、stopReason、token 统计")
    void onLLMResponseCollectsFields() {
        LLMResponse response = LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(MessageRole.ASSISTANT)
                        .textContent("导航已开始")
                        .build())
                .stopReason("end_turn")
                .usage(new LLMResponse.UsageInfo(100, 50))
                .build();

        observer.onLLMResponse(response);

        DebugInfo info = observer.getDebugInfo();
        assertEquals("导航已开始", info.getResponseText());
        assertEquals("end_turn", info.getStopReason());
        assertEquals(50, info.getResponseTokens());
        assertEquals(100, info.getTotalRequestTokens());
    }

    @Test
    @DisplayName("onToolCall 记录工具调用信息（toolName、args、result）")
    void onToolCallRecordsInfo() {
        observer.onToolCallStart("GetPoi");
        observer.onToolCall("GetPoi", Map.of("query", "新街口"), ToolResult.success("found"));

        DebugInfo info = observer.getDebugInfo();
        assertEquals(1, info.getToolCalls().size());
        DebugInfo.ToolCallInfo tc = info.getToolCalls().get(0);
        assertEquals("GetPoi", tc.getToolName());
        assertEquals("found", tc.getResult());
        assertTrue(tc.getDuration() >= 0);
    }

    @Test
    @DisplayName("onAgentComplete 设置 endTime，duration 大于 0")
    void onAgentCompleteSetsEndTime() {
        observer.onAgentStart("hi", "s1");
        observer.onAgentComplete(new AgentResponse("done"));

        DebugInfo info = observer.getDebugInfo();
        assertTrue(info.getEndTime() >= info.getStartTime());
        assertTrue(info.getDuration() >= 0);
    }

    @Test
    @DisplayName("onError 设置 error 并记录 endTime")
    void onErrorSetsErrorAndEndTime() {
        observer.onAgentStart("hi", "s1");
        observer.onError(new RuntimeException("timeout"));

        DebugInfo info = observer.getDebugInfo();
        assertEquals("timeout", info.getError());
        assertTrue(info.getEndTime() > 0);
    }

    @Test
    @DisplayName("完整生命周期：start → prompt → request → response → toolCall → complete")
    void fullLifecycle() {
        observer.onAgentStart("导航去新街口", "sess-lifecycle");

        PromptContext ctx = PromptContext.builder()
                .systemPrompt("导航助手")
                .activeSkillNames(List.of("nav"))
                .userMessage("导航去新街口")
                .build();
        observer.onPromptBuilt(ctx);

        observer.onLLMRequest(List.of(
                ConversationMessage.builder().role(MessageRole.USER).textContent("导航去新街口").build()
        ));

        observer.onLLMResponse(LLMResponse.builder()
                .message(ConversationMessage.builder().role(MessageRole.ASSISTANT).textContent("").build())
                .stopReason("tool_use")
                .build());

        observer.onToolCallStart("GetPoi");
        observer.onToolCall("GetPoi", Map.of("q", "新街口"), ToolResult.success("ok"));

        observer.onAgentComplete(new AgentResponse("导航已开始"));

        DebugInfo info = observer.getDebugInfo();
        assertEquals("sess-lifecycle", info.getSessionId());
        assertEquals("导航去新街口", info.getUserInput());
        assertEquals(List.of("nav"), info.getActiveSkills());
        assertEquals(1, info.getRequestMessages().size());
        assertEquals(1, info.getToolCalls().size());
        assertTrue(info.getDuration() >= 0);
    }
}
