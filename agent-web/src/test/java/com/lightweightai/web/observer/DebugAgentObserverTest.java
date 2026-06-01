package com.lightweightai.web.observer;

import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DebugAgentObserver — 调试观察器")
class DebugAgentObserverTest {

    private DebugAgentObserver observer;

    @BeforeEach
    void setUp() {
        observer = new DebugAgentObserver();
    }

    @Nested
    @DisplayName("onAgentStart")
    class AgentStartTests {

        @Test
        @DisplayName("记录 sessionId 和用户输入")
        void recordsSessionAndInput() {
            observer.onAgentStart("你好世界", "session-123");

            DebugInfo info = observer.getDebugInfo();
            assertEquals("session-123", info.getSessionId());
            assertEquals("你好世界", info.getUserInput());
            assertTrue(info.getStartTime() > 0, "startTime 应被设置");
        }
    }

    @Nested
    @DisplayName("onLLMRequest")
    class LLMRequestTests {

        @Test
        @DisplayName("记录请求消息列表")
        void recordsRequestMessages() {
            List<ConversationMessage> messages = List.of(
                    ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.USER)
                            .textContent("Hello")
                            .build(),
                    ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("Hi there")
                            .build()
            );

            observer.onLLMRequest(messages);

            DebugInfo info = observer.getDebugInfo();
            assertEquals(2, info.getRequestMessages().size());
            assertEquals("USER", info.getRequestMessages().get(0).getRole());
            assertEquals("Hello", info.getRequestMessages().get(0).getContent());
            assertEquals("ASSISTANT", info.getRequestMessages().get(1).getRole());
        }
    }

    @Nested
    @DisplayName("onLLMResponse")
    class LLMResponseTests {

        @Test
        @DisplayName("记录响应文本和 stopReason")
        void recordsResponseAndStopReason() {
            LLMResponse response = LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("我很好，谢谢！")
                            .build())
                    .stopReason("end_turn")
                    .build();

            observer.onLLMResponse(response);

            DebugInfo info = observer.getDebugInfo();
            assertEquals("我很好，谢谢！", info.getResponseText());
            assertEquals("end_turn", info.getStopReason());
        }
    }

    @Nested
    @DisplayName("onToolCall")
    class ToolCallTests {

        @Test
        @DisplayName("记录工具调用名称、参数和结果")
        void recordsToolCallDetails() {
            observer.onToolCallStart("search");
            observer.onToolCall("search", Map.of("query", "weather"),
                    ToolResult.success("Sunny, 25°C"));

            DebugInfo info = observer.getDebugInfo();
            assertEquals(1, info.getToolCalls().size());
            assertEquals("search", info.getToolCalls().get(0).getToolName());
            assertEquals("weather", info.getToolCalls().get(0).getArguments().get("query"));
            assertEquals("Sunny, 25°C", info.getToolCalls().get(0).getResult());
        }

        @Test
        @DisplayName("多次工具调用全部记录")
        void recordsMultipleToolCalls() {
            observer.onToolCallStart("tool1");
            observer.onToolCall("tool1", Map.of(), ToolResult.success("r1"));
            observer.onToolCallStart("tool2");
            observer.onToolCall("tool2", Map.of(), ToolResult.success("r2"));

            assertEquals(2, observer.getDebugInfo().getToolCalls().size());
        }
    }

    @Nested
    @DisplayName("onAgentComplete / onError")
    class LifecycleTests {

        @Test
        @DisplayName("onAgentComplete 设置 endTime，可计算 duration")
        void agentCompleteSetsDuration() throws InterruptedException {
            observer.onAgentStart("input", "s1");
            Thread.sleep(10);
            observer.onAgentComplete(new AgentResponse("done"));

            DebugInfo info = observer.getDebugInfo();
            assertTrue(info.getEndTime() > 0);
            assertTrue(info.getDuration() >= 10, "duration 应 >= 10ms");
        }

        @Test
        @DisplayName("onError 记录错误信息")
        void errorRecordsMessage() {
            observer.onAgentStart("input", "s1");
            observer.onError(new RuntimeException("LLM timeout"));

            DebugInfo info = observer.getDebugInfo();
            assertEquals("LLM timeout", info.getError());
            assertTrue(info.getEndTime() > 0);
        }
    }

    @Nested
    @DisplayName("端到端：完整 Agent 生命周期")
    class EndToEndTests {

        @Test
        @DisplayName("完整调用链路中所有信息正确收集")
        void fullLifecycleCaptured() {
            observer.onAgentStart("搜索天气", "session-42");

            observer.onLLMRequest(List.of(
                    ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.USER)
                            .textContent("搜索天气").build()
            ));

            LLMResponse llmResponse = LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("").build())
                    .stopReason("tool_use")
                    .build();
            observer.onLLMResponse(llmResponse);

            observer.onToolCallStart("weather");
            observer.onToolCall("weather", Map.of("city", "上海"),
                    ToolResult.success("晴天 28°C"));

            observer.onAgentComplete(AgentResponse.builder()
                    .text("上海今天晴天，28°C")
                    .stopReason("end_turn")
                    .build());

            DebugInfo info = observer.getDebugInfo();
            assertEquals("session-42", info.getSessionId());
            assertEquals("搜索天气", info.getUserInput());
            assertEquals(1, info.getRequestMessages().size());
            assertEquals(1, info.getToolCalls().size());
            assertEquals("weather", info.getToolCalls().get(0).getToolName());
            assertTrue(info.getDuration() >= 0);
            assertNull(info.getError());
        }
    }
}
