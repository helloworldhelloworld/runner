package com.lightweightai.kernel.gateway;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission-chain test: Gateway → ChatHandler → AgentLoop → LLMProvider
 *
 * Verifies that the payload (message, sessionId, toolDefinitions) survives the
 * full Gateway path and arrives at the LLM provider intact. This is the kind of
 * wiring test CLAUDE.md demands — each layer works alone, but the chain must carry
 * the payload end-to-end.
 */
@DisplayName("Gateway → AgentLoop transmission chain")
class GatewayTransmissionChainTest {

    private ToolRegistry toolRegistry;
    private TestMemory memory;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolRegistry.register(noopTool("calculator"));
        toolRegistry.register(noopTool("web_search"));
        memory = new TestMemory();
    }

    @Test
    @DisplayName("sync handle: user message reaches LLM and toolDefinitions are present")
    void syncHandle_transmitsMessageAndTools() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("response text");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(toolRegistry)
                .systemPrompt("test prompt")
                .build();

        Gateway gateway = Gateway.builder().agentLoop(agent).build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-1")
                .message("hello")
                .build();

        GatewayResponse response = gateway.handle(request);

        assertNotNull(response);
        assertEquals("response text", response.getText());
        assertFalse(response.isError());

        // Verify the LLM received the user message in the conversation
        List<ConversationMessage> sentMessages = spy.lastMessages();
        assertNotNull(sentMessages);
        boolean hasUserMessage = sentMessages.stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.USER
                        && m.getTextContent().contains("hello"));
        assertTrue(hasUserMessage, "User message 'hello' must reach LLM");

        // Verify toolDefinitions were injected
        LLMOptions opts = spy.lastOptions();
        assertNotNull(opts);
        List<Map<String, Object>> defs = opts.getToolDefinitions();
        assertNotNull(defs, "toolDefinitions must not be null");
        assertEquals(2, defs.size(), "both tools should be in toolDefinitions");
    }

    @Test
    @DisplayName("reactive stream: events flow through Gateway including TEXT_DELTA")
    void reactiveStream_emitsTextDeltaEvents() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("streamed text");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(toolRegistry)
                .build();

        Gateway gateway = Gateway.builder().agentLoop(agent).build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-reactive")
                .message("stream me")
                .build();

        List<StreamEvent> events = gateway.handleStreamReactive(request)
                .collectList().block();

        assertNotNull(events);
        assertFalse(events.isEmpty());

        // Must have at least a TRACE (user.message) and LLM_COMPLETE
        boolean hasTrace = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.TRACE);
        boolean hasLlmComplete = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE);
        assertTrue(hasTrace, "Gateway should emit TRACE event for user message");
        assertTrue(hasLlmComplete, "Stream should contain LLM_COMPLETE event");
    }

    @Test
    @DisplayName("sync handle: error from ChatHandler surfaces as error response")
    void syncHandle_errorSurfacesCorrectly() {
        ChatHandler failingHandler = new ChatHandler() {
            @Override
            public GatewayResponse chat(GatewayRequest request) {
                throw new RuntimeException("boom");
            }

            @Override
            public java.util.concurrent.CompletableFuture<GatewayResponse> chatStream(
                    GatewayRequest request, StreamCallback callback) {
                return java.util.concurrent.CompletableFuture.failedFuture(new RuntimeException("boom"));
            }
        };

        Gateway gateway = Gateway.builder().chatHandler(failingHandler).build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-err")
                .message("fail")
                .build();

        GatewayResponse response = gateway.handle(request);
        assertTrue(response.isError());
        assertEquals("boom", response.getErrorMessage());
    }

    @Test
    @DisplayName("system prompt reaches LLM via the chain")
    void systemPrompt_reachesLLM() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(toolRegistry)
                .systemPrompt("You are a helpful assistant")
                .build();

        Gateway gateway = Gateway.builder().agentLoop(agent).build();

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-sys")
                .message("hi")
                .build();

        gateway.handle(request);

        List<ConversationMessage> msgs = spy.lastMessages();
        boolean hasSystemPrompt = msgs.stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM
                        && m.getTextContent().contains("You are a helpful assistant"));
        assertTrue(hasSystemPrompt, "System prompt must be in the messages sent to LLM");
    }

    private static Tool noopTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    private static class TestMemory implements MemoryProvider {
        private final Map<String, List<Message>> sessions = new HashMap<>();

        @Override
        public void addMessage(String sessionId, Message message) {
            sessions.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>()).add(message);
        }

        @Override
        public List<Message> getHistory(String sessionId, int limit) {
            List<Message> msgs = sessions.getOrDefault(sessionId, List.of());
            int from = Math.max(0, msgs.size() - limit);
            return new ArrayList<>(msgs.subList(from, msgs.size()));
        }

        @Override public void clearSession(String s) { sessions.remove(s); }
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
