package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission chain tests: Profile → AgentFactory → AgentLoop → LLM.
 *
 * Per CLAUDE.md: "assert the payload at the bottom of the chain, not just the ceremony."
 * Uses CapturingLLMProvider to verify what actually reached the provider.
 */
@DisplayName("AgentFactory Transmission Chain — payload reaches LLM")
class AgentFactoryTransmissionTest {

    private ToolRegistry globalRegistry;
    private StubMemoryProvider memory;

    @BeforeEach
    void setUp() {
        globalRegistry = new ToolRegistry();
        globalRegistry.register(simpleTool("search"));
        globalRegistry.register(simpleTool("shell_exec"));
        globalRegistry.register(simpleTool("read_file"));
        memory = new StubMemoryProvider();
    }

    @Test
    @DisplayName("systemPrompt from Profile reaches LLM as SYSTEM message")
    void systemPromptReachesLLM() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        AgentProfile profile = AgentProfile.builder()
                .agentId("helper")
                .systemPrompt("你是一个安全助手")
                .build();

        AgentLoop agent = factory.create(profile);
        agent.run("hello", "sess-1");

        List<ConversationMessage> msgs = spy.lastMessages();
        assertNotNull(msgs);
        assertTrue(msgs.stream().anyMatch(m ->
                        m.getRole() == ConversationMessage.MessageRole.SYSTEM
                                && m.getTextContent().contains("你是一个安全助手")),
                "System prompt should reach LLM: " + msgs);
    }

    @Test
    @DisplayName("deny list filtered toolDefinitions reach LLM options")
    void denyListToolDefinitionsReachLLM() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe")
                .systemPrompt("safe agent")
                .toolDenyList(Set.of("shell_exec"))
                .build();

        AgentLoop agent = factory.create(profile);
        agent.run("hello", "sess-2");

        LLMOptions opts = spy.lastOptions();
        assertNotNull(opts, "LLM options should be captured");
        List<Map<String, Object>> defs = opts.getToolDefinitions();
        assertNotNull(defs, "Tool definitions should be present");

        List<String> names = defs.stream().map(d -> (String) d.get("name")).toList();
        assertTrue(names.contains("search"), "search should be present");
        assertTrue(names.contains("read_file"), "read_file should be present");
        assertFalse(names.contains("shell_exec"), "shell_exec should be denied");
    }

    @Test
    @DisplayName("allow list restricts toolDefinitions that reach LLM")
    void allowListToolDefinitionsReachLLM() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        AgentProfile profile = AgentProfile.builder()
                .agentId("narrow")
                .systemPrompt("narrow agent")
                .toolAllowList(Set.of("read_file"))
                .build();

        AgentLoop agent = factory.create(profile);
        agent.run("hello", "sess-3");

        LLMOptions opts = spy.lastOptions();
        List<Map<String, Object>> defs = opts.getToolDefinitions();
        assertEquals(1, defs.size(), "Only 1 tool should pass allow list");
        assertEquals("read_file", defs.get(0).get("name"));
    }

    @Test
    @DisplayName("default CompactionChain compresses large TOOL results before second LLM call")
    void defaultCompactionChainCompressesLargeToolResults() {
        String largeTool = "x".repeat(5000);
        globalRegistry.register(new Tool() {
            @Override public String getName() { return "search"; }
            @Override public String getDescription() { return "search"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(largeTool);
            }
        });

        CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                "search", Map.of("q", "test"), "done");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        AgentProfile profile = AgentProfile.builder()
                .agentId("compacted")
                .systemPrompt("agent")
                .build();

        AgentLoop agent = factory.create(profile);
        agent.run("test", "sess-compact");

        assertTrue(spy.callCount() >= 2,
                "Should have at least 2 LLM calls (tool_use → end_turn), got: " + spy.callCount());

        List<ConversationMessage> secondCallMsgs = spy.messagesHistory().get(1);
        boolean hasCompressedTool = secondCallMsgs.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                .anyMatch(m -> m.getTextContent() != null
                        && m.getTextContent().contains("[...snipped"));
        assertTrue(hasCompressedTool,
                "Large TOOL result should be micro-compressed by default CompactionChain");
    }

    @Test
    @DisplayName("different Profiles create isolated ScopedToolRegistries — each sees its own filtered tools")
    void independentScopedRegistriesPerProfile() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);

        AgentProfile p1 = AgentProfile.builder()
                .agentId("agent-a")
                .systemPrompt("A")
                .toolDenyList(Set.of("search"))
                .build();
        AgentProfile p2 = AgentProfile.builder()
                .agentId("agent-b")
                .systemPrompt("B")
                .toolDenyList(Set.of("read_file"))
                .build();

        AgentLoop a1 = factory.create(p1);
        AgentLoop a2 = factory.create(p2);

        a1.run("test", "sess-a");
        List<String> a1Tools = spy.lastOptions().getToolDefinitions().stream()
                .map(d -> (String) d.get("name")).toList();
        assertFalse(a1Tools.contains("search"), "Agent A should deny search");
        assertTrue(a1Tools.contains("read_file"), "Agent A should allow read_file");

        a2.run("test", "sess-b");
        List<String> a2Tools = spy.lastOptions().getToolDefinitions().stream()
                .map(d -> (String) d.get("name")).toList();
        assertTrue(a2Tools.contains("search"), "Agent B should allow search");
        assertFalse(a2Tools.contains("read_file"), "Agent B should deny read_file");
    }

    @Test
    @DisplayName("no systemPrompt in Profile → LLM messages have no SYSTEM message")
    void noSystemPromptMeansNoSystemMessage() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        AgentProfile profile = AgentProfile.builder()
                .agentId("bare")
                .build();

        AgentLoop agent = factory.create(profile);
        agent.run("hello", "sess-bare");

        List<ConversationMessage> msgs = spy.lastMessages();
        boolean hasSystem = msgs.stream()
                .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM);
        assertFalse(hasSystem, "No SYSTEM message when systemPrompt is null");
    }

    private Tool simpleTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    private static class StubMemoryProvider implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
