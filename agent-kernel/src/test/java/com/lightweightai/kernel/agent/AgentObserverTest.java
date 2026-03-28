package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.PromptContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentObserver - Agent 观察者接口")
class AgentObserverTest {

    @Test
    @DisplayName("默认方法全部为空操作（不抛异常）")
    void defaultMethodsAreNoOp() {
        AgentObserver observer = new AgentObserver() {};

        PromptContext ctx = PromptContext.builder()
            .systemPrompt("test")
            .userMessage("hello")
            .build();

        LLMResponse response = LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("reply")
                .build())
            .build();

        assertDoesNotThrow(() -> observer.onPromptBuilt(ctx));
        assertDoesNotThrow(() -> observer.onLLMRequest(List.of()));
        assertDoesNotThrow(() -> observer.onLLMResponse(response));
        assertDoesNotThrow(() -> observer.onToolCall("tool", Map.of(), ToolResult.success("ok")));
        assertDoesNotThrow(() -> observer.onAgentStart("input", "session1"));
        assertDoesNotThrow(() -> observer.onAgentComplete(new AgentResponse("done")));
        assertDoesNotThrow(() -> observer.onError(new RuntimeException("err")));
    }

    @Test
    @DisplayName("自定义实现可捕获所有回调")
    void customImplementationReceivesCallbacks() {
        List<String> events = new ArrayList<>();

        AgentObserver observer = new AgentObserver() {
            @Override
            public void onAgentStart(String input, String sessionId) {
                events.add("start:" + input + ":" + sessionId);
            }

            @Override
            public void onPromptBuilt(PromptContext context) {
                events.add("prompt:" + context.getUserMessage());
            }

            @Override
            public void onLLMRequest(List<ConversationMessage> messages) {
                events.add("llm_req:" + messages.size());
            }

            @Override
            public void onLLMResponse(LLMResponse response) {
                events.add("llm_resp:" + response.getStopReason());
            }

            @Override
            public void onToolCall(String toolName, Map<String, Object> args, ToolResult result) {
                events.add("tool:" + toolName + ":" + result.getContent());
            }

            @Override
            public void onAgentComplete(AgentResponse response) {
                events.add("complete:" + response.getText());
            }

            @Override
            public void onError(Throwable error) {
                events.add("error:" + error.getMessage());
            }
        };

        // Simulate full agent lifecycle
        observer.onAgentStart("hello", "sess1");

        PromptContext ctx = PromptContext.builder().userMessage("hello").build();
        observer.onPromptBuilt(ctx);

        observer.onLLMRequest(List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("hello")
                .build()
        ));

        LLMResponse resp = LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("Hi there")
                .build())
            .stopReason("end_turn")
            .build();
        observer.onLLMResponse(resp);

        observer.onToolCall("search", Map.of("q", "test"), ToolResult.success("3 results"));
        observer.onAgentComplete(new AgentResponse("Hi there"));

        assertEquals(6, events.size());
        assertEquals("start:hello:sess1", events.get(0));
        assertEquals("prompt:hello", events.get(1));
        assertEquals("llm_req:1", events.get(2));
        assertEquals("llm_resp:end_turn", events.get(3));
        assertEquals("tool:search:3 results", events.get(4));
        assertEquals("complete:Hi there", events.get(5));
    }

    @Test
    @DisplayName("onError 捕获异常信息")
    void onErrorCapturesException() {
        List<String> errors = new ArrayList<>();
        AgentObserver observer = new AgentObserver() {
            @Override
            public void onError(Throwable error) {
                errors.add(error.getClass().getSimpleName() + ":" + error.getMessage());
            }
        };

        observer.onError(new IllegalArgumentException("bad input"));
        observer.onError(new RuntimeException("timeout"));

        assertEquals(2, errors.size());
        assertEquals("IllegalArgumentException:bad input", errors.get(0));
        assertEquals("RuntimeException:timeout", errors.get(1));
    }
}
