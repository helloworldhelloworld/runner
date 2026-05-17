package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingAgentObserver;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentObserver — lifecycle hooks fire with correct payloads")
class AgentObserverTest {

    private TestMemory memory;
    private ToolRegistry tools;

    @BeforeEach
    void setUp() {
        memory = new TestMemory();
        tools = new ToolRegistry();
    }

    @Test
    @DisplayName("onAgentStart fires with correct input and sessionId")
    void onAgentStartFires() {
        AtomicReference<String> capturedInput = new AtomicReference<>();
        AtomicReference<String> capturedSession = new AtomicReference<>();

        AgentObserver observer = new AgentObserver() {
            @Override
            public void onAgentStart(String input, String sessionId) {
                capturedInput.set(input);
                capturedSession.set(sessionId);
            }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("response");
        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(tools)
                .systemPrompt("test")
                .addObserver(observer)
                .build();

        loop.run("hello world", "session-1");

        assertEquals("hello world", capturedInput.get());
        assertEquals("session-1", capturedSession.get());
    }

    @Test
    @DisplayName("onLLMRequest fires with the actual messages sent to LLM")
    void onLLMRequestReceivesMessages() {
        AtomicReference<List<ConversationMessage>> capturedMessages = new AtomicReference<>();

        AgentObserver observer = new AgentObserver() {
            @Override
            public void onLLMRequest(List<ConversationMessage> messages) {
                capturedMessages.set(new ArrayList<>(messages));
            }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("ok");
        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(tools)
                .systemPrompt("You are helpful")
                .addObserver(observer)
                .build();

        loop.run("test input", "s1");

        assertNotNull(capturedMessages.get());
        assertFalse(capturedMessages.get().isEmpty());
        assertTrue(capturedMessages.get().stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM));
    }

    @Test
    @DisplayName("onLLMResponse fires with the actual LLM response")
    void onLLMResponseReceivesResponse() {
        AtomicReference<LLMResponse> capturedResponse = new AtomicReference<>();

        AgentObserver observer = new AgentObserver() {
            @Override
            public void onLLMResponse(LLMResponse response) {
                capturedResponse.set(response);
            }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("the answer");
        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(tools)
                .addObserver(observer)
                .build();

        loop.run("question", "s1");

        assertNotNull(capturedResponse.get());
        assertEquals("end_turn", capturedResponse.get().getStopReason());
    }

    @Test
    @DisplayName("onAgentComplete fires with the AgentResponse")
    void onAgentCompleteFires() {
        AtomicReference<AgentResponse> capturedResp = new AtomicReference<>();

        AgentObserver observer = new AgentObserver() {
            @Override
            public void onAgentComplete(AgentResponse response) {
                capturedResp.set(response);
            }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("done");
        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(tools)
                .addObserver(observer)
                .build();

        loop.run("do something", "s1");

        assertNotNull(capturedResp.get());
        assertEquals("done", capturedResp.get().getText());
    }

    @Test
    @DisplayName("onError fires when exception occurs")
    void onErrorFires() {
        AtomicReference<Throwable> capturedError = new AtomicReference<>();

        AgentObserver observer = new AgentObserver() {
            @Override
            public void onError(Throwable error) {
                capturedError.set(error);
            }
        };

        LLMProvider failingProvider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                throw new RuntimeException("LLM crashed");
            }
            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.failedFuture(new RuntimeException("LLM crashed"));
            }
            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
                return CompletableFuture.failedFuture(new RuntimeException("LLM crashed"));
            }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "failing"; }
        };

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(failingProvider)
                .memoryProvider(memory)
                .toolRegistry(tools)
                .addObserver(observer)
                .build();

        assertThrows(RuntimeException.class, () -> loop.run("input", "s1"));
        assertNotNull(capturedError.get());
        assertTrue(capturedError.get().getMessage().contains("LLM crashed"));
    }

    @Test
    @DisplayName("observer exception does not interrupt agent execution")
    void observerExceptionDoesNotInterruptAgent() {
        AgentObserver brokenObserver = new AgentObserver() {
            @Override
            public void onAgentStart(String input, String sessionId) {
                throw new RuntimeException("observer bug");
            }
        };

        AtomicBoolean completeFired = new AtomicBoolean(false);
        AgentObserver goodObserver = new AgentObserver() {
            @Override
            public void onAgentComplete(AgentResponse response) {
                completeFired.set(true);
            }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("ok");
        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(tools)
                .addObserver(brokenObserver)
                .addObserver(goodObserver)
                .build();

        AgentResponse result = loop.run("test", "s1");
        assertEquals("ok", result.getText());
        assertTrue(completeFired.get());
    }

    @Test
    @DisplayName("CapturingAgentObserver records pre/post tool calls with payloads via reactive path")
    void capturingObserverRecordsToolCalls() {
        CapturingAgentObserver observer = new CapturingAgentObserver();

        Tool echoTool = new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "echoes input"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echoed: " + args.get("text"));
            }
        };

        tools.register(echoTool);

        CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("text", "hi"), "done");
        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(tools)
                .addObserver(observer)
                .build();

        // Use reactive path because observers for pre/post tool use fire in reactive ToolCallingLoop
        loop.runReactive("call echo", "s1").blockLast();

        assertEquals(1, observer.preCalls().size());
        assertEquals("echo", observer.preCalls().get(0).name());
        assertEquals("hi", observer.preCalls().get(0).args().get("text"));

        assertEquals(1, observer.postCalls().size());
        assertEquals("echo", observer.postCalls().get(0).name());
        assertNotNull(observer.postCalls().get(0).result());
        assertEquals("echoed: hi", observer.postCalls().get(0).result().getContent());
    }

    // ==================== Helpers ====================

    private static class TestMemory implements MemoryProvider {
        private final List<Message> messages = new ArrayList<>();
        @Override public void addMessage(String s, Message m) { messages.add(m); }
        @Override public List<Message> getHistory(String s, int l) {
            int start = Math.max(0, messages.size() - l);
            return new ArrayList<>(messages.subList(start, messages.size()));
        }
        @Override public void clearSession(String s) { messages.clear(); }
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
