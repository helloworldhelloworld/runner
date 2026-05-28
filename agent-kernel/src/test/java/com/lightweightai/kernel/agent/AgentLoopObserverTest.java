package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentLoop: observer lifecycle notifications")
class AgentLoopObserverTest {

    @Test
    @DisplayName("observers receive onAgentStart, onLLMRequest, onLLMResponse, onAgentComplete in order")
    void observerLifecycleInSync() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("observed");
        List<String> callOrder = new CopyOnWriteArrayList<>();
        AtomicReference<String> capturedInput = new AtomicReference<>();
        AtomicReference<String> capturedSessionId = new AtomicReference<>();
        AtomicReference<LLMResponse> capturedResponse = new AtomicReference<>();
        AtomicReference<AgentResponse> capturedAgentResponse = new AtomicReference<>();

        AgentObserver observer = new AgentObserver() {
            @Override
            public void onAgentStart(String input, String sessionId) {
                callOrder.add("start");
                capturedInput.set(input);
                capturedSessionId.set(sessionId);
            }

            @Override
            public void onLLMRequest(List<ConversationMessage> messages) {
                callOrder.add("llmRequest");
            }

            @Override
            public void onLLMResponse(LLMResponse response) {
                callOrder.add("llmResponse");
                capturedResponse.set(response);
            }

            @Override
            public void onAgentComplete(AgentResponse response) {
                callOrder.add("complete");
                capturedAgentResponse.set(response);
            }
        };

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new InMemoryProvider())
                .toolRegistry(new ToolRegistry())
                .systemPrompt("test")
                .addObserver(observer)
                .build();

        agent.run("hello observer", "session-obs");

        assertEquals(List.of("start", "llmRequest", "llmResponse", "complete"), callOrder);
        assertEquals("hello observer", capturedInput.get());
        assertEquals("session-obs", capturedSessionId.get());
        assertNotNull(capturedResponse.get());
        assertEquals("observed", capturedAgentResponse.get().getText());
    }

    @Test
    @DisplayName("observer exception does not break agent execution")
    void observerExceptionDoesNotBreakAgent() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("resilient");

        AgentObserver brokenObserver = new AgentObserver() {
            @Override
            public void onAgentStart(String input, String sessionId) {
                throw new RuntimeException("observer crash");
            }
        };

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new InMemoryProvider())
                .toolRegistry(new ToolRegistry())
                .systemPrompt("test")
                .addObserver(brokenObserver)
                .build();

        AgentResponse response = agent.run("test", "session-broken-obs");
        assertEquals("resilient", response.getText());
    }

    @Test
    @DisplayName("onError observer fires when LLM throws")
    void onErrorObserverFires() {
        AtomicReference<Throwable> capturedError = new AtomicReference<>();
        AgentObserver observer = new AgentObserver() {
            @Override
            public void onError(Throwable error) {
                capturedError.set(error);
            }
        };

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(new FailingLLMProvider())
                .memoryProvider(new InMemoryProvider())
                .toolRegistry(new ToolRegistry())
                .systemPrompt("test")
                .addObserver(observer)
                .build();

        assertThrows(RuntimeException.class, () -> agent.run("trigger error", "session-err"));
        assertNotNull(capturedError.get(), "onError observer should capture the exception");
        assertTrue(capturedError.get().getMessage().contains("LLM failed"));
    }

    private static class FailingLLMProvider implements com.lightweightai.kernel.llm.LLMProvider {
        @Override
        public com.lightweightai.kernel.llm.LLMResponse complete(
                List<ConversationMessage> messages,
                com.lightweightai.kernel.llm.LLMOptions options) {
            throw new RuntimeException("LLM failed");
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.lightweightai.kernel.llm.LLMResponse> completeAsync(
                List<ConversationMessage> messages,
                com.lightweightai.kernel.llm.LLMOptions options) {
            return java.util.concurrent.CompletableFuture.failedFuture(new RuntimeException("LLM failed"));
        }

        @Override
        public java.util.concurrent.CompletableFuture<com.lightweightai.kernel.llm.LLMResponse> completeStream(
                List<ConversationMessage> messages,
                com.lightweightai.kernel.llm.LLMOptions options,
                StreamEventHandler handler) {
            return java.util.concurrent.CompletableFuture.failedFuture(new RuntimeException("LLM failed"));
        }

        @Override
        public com.lightweightai.kernel.llm.ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "failing"; }
    }
}
