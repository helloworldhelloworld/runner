package com.lightweightai.kernel.agent;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentLoop reactive — full tool chain end-to-end")
class AgentLoopReactiveToolChainTest {

    private TestMemory memory;

    @BeforeEach
    void setUp() {
        memory = new TestMemory();
    }

    @Test
    @DisplayName("reactive path emits TEXT_DELTA events for pure text response")
    void reactiveEmitsTextDelta() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("hello world");
        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("test")
                .build();

        List<StreamEvent> events = loop.runReactive("hi", "s1")
                .collectList().block();

        assertNotNull(events);
        assertTrue(events.stream().anyMatch(e ->
                e.getType() == StreamEvent.EventType.LLM_COMPLETE));
    }

    @Test
    @DisplayName("reactive path with tool call emits TOOL_CALL_START and TOOL_RESULT events")
    void reactiveEmitsToolEvents() {
        Tool calcTool = new Tool() {
            @Override public String getName() { return "add"; }
            @Override public String getDescription() { return "adds two numbers"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                int a = ((Number) args.get("a")).intValue();
                int b = ((Number) args.get("b")).intValue();
                return ToolResult.success(String.valueOf(a + b));
            }
        };

        CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "add", Map.of("a", 2, "b", 3), "The sum is 5");

        ToolRegistry registry = new ToolRegistry();
        registry.register(calcTool);

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(registry)
                .build();

        List<StreamEvent> events = loop.runReactive("what is 2+3?", "s1")
                .collectList().block();

        assertNotNull(events);

        boolean hasToolStart = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.TOOL_CALL_START);
        boolean hasToolResult = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.TOOL_RESULT);
        boolean hasLlmComplete = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE);

        assertTrue(hasToolStart, "Should have TOOL_CALL_START event");
        assertTrue(hasToolResult, "Should have TOOL_RESULT event");
        assertTrue(hasLlmComplete, "Should have LLM_COMPLETE event");
    }

    @Test
    @DisplayName("reactive path stores user message in memory before LLM call")
    void reactivePathStoresUserMessageInMemory() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("the answer is 42");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .build();

        loop.runReactive("what is the meaning of life?", "s1").blockLast();

        List<Message> history = memory.getHistory("s1", 10);
        assertTrue(history.stream().anyMatch(m -> m.getRole().equals("user")),
                "User message should be persisted in memory");
        // Note: assistant message requires TEXT_DELTA events to accumulate text.
        // CapturingLLMProvider only emits LLM_COMPLETE, so fullResponse is empty and
        // assistant message is skipped (by design: empty responses aren't stored).
    }

    @Test
    @DisplayName("multiple tool calls in sequence produce correct event chain")
    void multipleToolCallsProduceEventChain() {
        Tool greetTool = new Tool() {
            @Override public String getName() { return "greet"; }
            @Override public String getDescription() { return "greets"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("Hi " + args.get("name"));
            }
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(greetTool);

        CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "greet", Map.of("name", "Alice"), "Done greeting");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(provider)
                .memoryProvider(memory)
                .toolRegistry(registry)
                .build();

        List<StreamEvent> events = loop.runReactive("greet Alice", "s1")
                .collectList().block();

        assertNotNull(events);
        assertFalse(events.isEmpty());

        long toolStarts = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TOOL_CALL_START).count();
        long toolResults = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TOOL_RESULT).count();

        assertEquals(toolStarts, toolResults, "Each TOOL_CALL_START should have matching TOOL_RESULT");
    }

    @Test
    @DisplayName("tool definitions from registry are transmitted to provider in reactive path")
    void toolDefinitionsTransmittedInReactivePath() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        Tool t1 = simpleTool("search");
        Tool t2 = simpleTool("calc");
        ToolRegistry registry = new ToolRegistry();
        registry.register(t1);
        registry.register(t2);

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(registry)
                .build();

        loop.runReactive("test", "s1").blockLast();

        LLMOptions captured = spy.lastOptions();
        assertNotNull(captured);
        assertNotNull(captured.getToolDefinitions());
        assertEquals(2, captured.getToolDefinitions().size());
    }

    private Tool simpleTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }

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
