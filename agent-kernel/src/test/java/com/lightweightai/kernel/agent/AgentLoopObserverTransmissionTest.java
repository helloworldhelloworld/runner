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
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentLoop — tool definitions transmission to LLM provider (payload-capture)")
class AgentLoopObserverTransmissionTest {

    private TestMemory memory;

    @BeforeEach
    void setUp() {
        memory = new TestMemory();
    }

    @Test
    @DisplayName("tools registered in ToolRegistry are transmitted as toolDefinitions to LLM")
    void toolDefinitionsTransmittedToLLM() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        Tool searchTool = simpleTool("search", "Search docs");
        Tool calcTool = simpleTool("calc", "Calculate");

        ToolRegistry registry = new ToolRegistry();
        registry.register(searchTool);
        registry.register(calcTool);

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(registry)
                .systemPrompt("test")
                .build();

        loop.run("use tools", "s1");

        LLMOptions capturedOptions = spy.lastOptions();
        assertNotNull(capturedOptions);
        assertNotNull(capturedOptions.getToolDefinitions());
        assertEquals(2, capturedOptions.getToolDefinitions().size());

        List<String> toolNames = capturedOptions.getToolDefinitions().stream()
                .map(td -> (String) td.get("name"))
                .toList();
        assertTrue(toolNames.contains("search"));
        assertTrue(toolNames.contains("calc"));
    }

    @Test
    @DisplayName("systemPrompt is transmitted in messages to LLM")
    void systemPromptTransmittedToLLM() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("You are a helpful assistant")
                .build();

        loop.run("hi", "s1");

        List<ConversationMessage> messages = spy.lastMessages();
        assertNotNull(messages);
        assertTrue(messages.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .anyMatch(m -> m.getTextContent().contains("You are a helpful assistant")));
    }

    @Test
    @DisplayName("user input is transmitted as USER message to LLM")
    void userInputTransmittedAsMessage() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("reply");

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .systemPrompt("sys")
                .build();

        loop.run("what is 2+2?", "s1");

        List<ConversationMessage> messages = spy.lastMessages();
        assertTrue(messages.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.USER)
                .anyMatch(m -> m.getTextContent().contains("what is 2+2?")));
    }

    @Test
    @DisplayName("LLMOptions from builder are passed through to provider")
    void llmOptionsPassedThrough() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        LLMOptions customOptions = LLMOptions.builder()
                .temperature(0.5)
                .maxTokens(1000)
                .build();

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(new ToolRegistry())
                .llmOptions(customOptions)
                .build();

        loop.run("test", "s1");

        LLMOptions captured = spy.lastOptions();
        assertEquals(0.5, captured.getTemperature(), 0.001);
        assertEquals(1000, captured.getMaxTokens());
    }

    @Test
    @DisplayName("tool calling loop: tool result is fed back to LLM on second call")
    void toolResultFedBackToLLM() {
        Tool echoTool = new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "echoes input"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ECHO:" + args.get("msg"));
            }
        };

        CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("msg", "hello"), "final answer");

        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool);

        AgentLoop loop = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(memory)
                .toolRegistry(registry)
                .build();

        AgentResponse result = loop.run("echo something", "s1");
        assertEquals("final answer", result.getText());

        assertEquals(2, spy.callCount());
        List<ConversationMessage> secondCallMessages = spy.messagesHistory().get(1);
        String allText = secondCallMessages.stream()
                .map(ConversationMessage::getTextContent)
                .reduce("", String::concat);
        assertTrue(allText.contains("ECHO:hello") || secondCallMessages.stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.TOOL));
    }

    // ==================== Helpers ====================

    private Tool simpleTool(String name, String description) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
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
