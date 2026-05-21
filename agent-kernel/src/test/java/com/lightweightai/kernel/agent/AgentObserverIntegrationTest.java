package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingAgentObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentObserver — lifecycle hooks fire with correct payloads")
class AgentObserverIntegrationTest {

    private MemoryProvider memory;
    private SimpleProvider llmProvider;
    private RecordingObserver observer;

    @BeforeEach
    void setUp() {
        memory = new InMemoryProvider();
        llmProvider = new SimpleProvider();
        observer = new RecordingObserver();
    }

    @Test
    @DisplayName("onAgentStart fires with input and sessionId")
    void onAgentStartFires() {
        llmProvider.nextText = "hi";
        AgentLoop loop = buildLoop();

        loop.run("hello", "s1");

        assertEquals(1, observer.starts.size());
        assertEquals("hello", observer.starts.get(0).input);
        assertEquals("s1", observer.starts.get(0).sessionId);
    }

    @Test
    @DisplayName("onAgentComplete fires with response text")
    void onAgentCompleteFires() {
        llmProvider.nextText = "response-text";
        AgentLoop loop = buildLoop();

        loop.run("test", "s1");

        assertEquals(1, observer.completes.size());
        assertEquals("response-text", observer.completes.get(0).getText());
    }

    @Test
    @DisplayName("onLLMRequest fires with messages including system prompt")
    void onLLMRequestFiresWithMessages() {
        llmProvider.nextText = "ok";
        AgentLoop loop = buildLoop();

        loop.run("test", "s1");

        assertEquals(1, observer.llmRequests.size());
        List<ConversationMessage> messages = observer.llmRequests.get(0);
        assertTrue(messages.size() >= 2);
        assertEquals(ConversationMessage.MessageRole.SYSTEM, messages.get(0).getRole());
    }

    @Test
    @DisplayName("onLLMResponse fires with LLM response")
    void onLLMResponseFires() {
        llmProvider.nextText = "answer";
        AgentLoop loop = buildLoop();

        loop.run("question", "s1");

        assertEquals(1, observer.llmResponses.size());
        assertNotNull(observer.llmResponses.get(0));
    }

    @Test
    @DisplayName("onError fires when LLM throws exception")
    void onErrorFires() {
        llmProvider.shouldThrow = new RuntimeException("boom");
        AgentLoop loop = buildLoop();

        assertThrows(RuntimeException.class, () -> loop.run("input", "s1"));

        assertEquals(1, observer.errors.size());
        assertEquals("boom", observer.errors.get(0).getMessage());
    }

    @Test
    @DisplayName("multiple observers all receive events")
    void multipleObserversReceiveEvents() {
        RecordingObserver second = new RecordingObserver();
        llmProvider.nextText = "hi";

        AgentLoop loop = AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memory)
            .systemPrompt("test")
            .addObserver(observer)
            .addObserver(second)
            .build();

        loop.run("test", "s1");

        assertEquals(1, observer.starts.size());
        assertEquals(1, second.starts.size());
    }

    @Test
    @DisplayName("observer exception does not break agent loop")
    void observerExceptionDoesNotBreakLoop() {
        AgentObserver failingObserver = new AgentObserver() {
            @Override
            public void onAgentStart(String input, String sessionId) {
                throw new RuntimeException("observer failure");
            }
        };

        llmProvider.nextText = "still works";

        AgentLoop loop = AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memory)
            .systemPrompt("test")
            .addObserver(failingObserver)
            .addObserver(observer)
            .build();

        AgentResponse response = loop.run("test", "s1");

        assertEquals("still works", response.getText());
        assertEquals(1, observer.starts.size());
    }

    @Test
    @DisplayName("onPreToolUse and onPostToolUse fire via CapturingAgentObserver in tool-calling loop")
    void toolObserverHooksFireThroughReactivePath() {
        CapturingAgentObserver capturingObserver = new CapturingAgentObserver();

        Tool echoTool = new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "echo tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echoed");
            }
        };

        llmProvider.nextToolCall = new ToolCall("tc1", "echo", Map.of("msg", "hi"));
        llmProvider.nextText = "done";

        AgentLoop loop = AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memory)
            .systemPrompt("test")
            .addTool(echoTool)
            .addObserver(capturingObserver)
            .build();

        loop.run("call echo", "s1");

        assertFalse(capturingObserver.preCalls().isEmpty(),
            "onPreToolUse must fire at least once through ToolCallingLoop");
        assertEquals("echo", capturingObserver.preCalls().get(0).name());

        assertFalse(capturingObserver.postCalls().isEmpty(),
            "onPostToolUse must fire at least once");
        assertEquals("echo", capturingObserver.postCalls().get(0).name());
        assertNotNull(capturingObserver.postCalls().get(0).result(),
            "post-call result must not be null");
    }

    @Test
    @DisplayName("full lifecycle order: start → llmRequest → llmResponse → complete")
    void lifecycleOrder() {
        llmProvider.nextText = "ok";
        AgentLoop loop = buildLoop();

        loop.run("test", "s1");

        List<String> order = observer.eventOrder;
        assertTrue(order.indexOf("start") < order.indexOf("llmRequest"));
        assertTrue(order.indexOf("llmRequest") < order.indexOf("llmResponse"));
        assertTrue(order.indexOf("llmResponse") < order.indexOf("complete"));
    }

    // ==================== Helpers ====================

    private AgentLoop buildLoop() {
        return AgentLoop.builder()
            .llmProvider(llmProvider)
            .memoryProvider(memory)
            .systemPrompt("test prompt")
            .addObserver(observer)
            .build();
    }

    private static class SimpleProvider implements LLMProvider {
        String nextText = "";
        ToolCall nextToolCall;
        RuntimeException shouldThrow;
        private final List<ConversationMessage> lastMessages = new ArrayList<>();
        private int callCount = 0;

        @Override
        public String getProviderName() {
            return "test";
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            if (shouldThrow != null) throw shouldThrow;
            lastMessages.clear();
            lastMessages.addAll(messages);
            callCount++;

            if (nextToolCall != null && callCount == 1) {
                ToolCall tc = nextToolCall;
                return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("")
                        .build())
                    .stopReason("tool_use")
                    .toolCalls(List.of(tc))
                    .build();
            }

            return LLMResponse.builder()
                .message(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT)
                    .textContent(nextText)
                    .build())
                .stopReason("end_turn")
                .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options,
                StreamEventHandler handler) {
            LLMResponse response = complete(messages, options);
            handler.onTextDelta(response.getMessage().getTextContent());
            handler.onComplete(response);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public ModelCapability getModelCapability() {
            return null;
        }
    }

    record StartEvent(String input, String sessionId) {}

    private static class RecordingObserver implements AgentObserver {
        final List<StartEvent> starts = new CopyOnWriteArrayList<>();
        final List<AgentResponse> completes = new CopyOnWriteArrayList<>();
        final List<List<ConversationMessage>> llmRequests = new CopyOnWriteArrayList<>();
        final List<LLMResponse> llmResponses = new CopyOnWriteArrayList<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
        final List<String> eventOrder = new CopyOnWriteArrayList<>();

        @Override
        public void onAgentStart(String input, String sessionId) {
            starts.add(new StartEvent(input, sessionId));
            eventOrder.add("start");
        }

        @Override
        public void onAgentComplete(AgentResponse response) {
            completes.add(response);
            eventOrder.add("complete");
        }

        @Override
        public void onLLMRequest(List<ConversationMessage> messages) {
            llmRequests.add(List.copyOf(messages));
            eventOrder.add("llmRequest");
        }

        @Override
        public void onLLMResponse(LLMResponse response) {
            llmResponses.add(response);
            eventOrder.add("llmResponse");
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
            eventOrder.add("error");
        }
    }
}
