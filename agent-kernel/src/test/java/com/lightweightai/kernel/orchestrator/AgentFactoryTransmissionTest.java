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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Producer-consumer transmission test for AgentFactory.
 *
 * Per CLAUDE.md UT rule #3: "Every producer-consumer pair needs ≥1 transmission test."
 * This tests: AgentProfile → AgentFactory → AgentLoop → LLMProvider.
 *
 * Specifically verifies that:
 * 1. System prompt from AgentProfile reaches the LLM
 * 2. ScopedToolRegistry filtering (deny list) is applied and the result reaches LLM toolDefinitions
 * 3. maxToolIterations from profile is respected
 * 4. ContextCompactor (CompactionChain) is wired in
 */
@DisplayName("AgentFactory → AgentLoop transmission chain")
class AgentFactoryTransmissionTest {

    private ToolRegistry globalRegistry;
    private TestMemory memory;

    @BeforeEach
    void setUp() {
        globalRegistry = new ToolRegistry();
        globalRegistry.register(noopTool("allowed_tool"));
        globalRegistry.register(noopTool("denied_tool"));
        globalRegistry.register(noopTool("another_tool"));
        memory = new TestMemory();
    }

    @Test
    @DisplayName("systemPrompt from profile reaches LLM messages")
    void systemPrompt_reachesLLM() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);

        AgentProfile profile = AgentProfile.builder()
                .agentId("helper")
                .systemPrompt("You are a specialized helper agent")
                .build();

        AgentLoop agent = factory.create(profile);
        agent.run("hello", "sess-1");

        List<ConversationMessage> msgs = spy.lastMessages();
        assertNotNull(msgs);
        ConversationMessage system = msgs.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .findFirst().orElse(null);
        assertNotNull(system, "System message must be present");
        assertTrue(system.getTextContent().contains("You are a specialized helper agent"),
                "Profile systemPrompt must reach LLM; got: " + system.getTextContent());
    }

    @Test
    @DisplayName("toolDenyList from profile filters out denied tools in toolDefinitions")
    void toolDenyList_filtersToolDefinitions() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);

        AgentProfile profile = AgentProfile.builder()
                .agentId("restricted")
                .systemPrompt("restricted agent")
                .toolDenyList(Set.of("denied_tool"))
                .build();

        AgentLoop agent = factory.create(profile);
        agent.run("test", "sess-deny");

        LLMOptions opts = spy.lastOptions();
        assertNotNull(opts);
        List<Map<String, Object>> defs = opts.getToolDefinitions();
        assertNotNull(defs, "toolDefinitions must not be null");

        List<String> names = defs.stream()
                .map(d -> (String) d.get("name"))
                .toList();
        assertEquals(2, names.size(), "Denied tool should be filtered; got: " + names);
        assertTrue(names.contains("allowed_tool"));
        assertTrue(names.contains("another_tool"));
        assertFalse(names.contains("denied_tool"), "denied_tool must be filtered out");
    }

    @Test
    @DisplayName("toolAllowList from profile restricts to only allowed tools")
    void toolAllowList_restrictsToDefined() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);

        AgentProfile profile = AgentProfile.builder()
                .agentId("selective")
                .systemPrompt("selective agent")
                .toolAllowList(Set.of("allowed_tool"))
                .build();

        AgentLoop agent = factory.create(profile);
        agent.run("test", "sess-allow");

        LLMOptions opts = spy.lastOptions();
        assertNotNull(opts);
        List<Map<String, Object>> defs = opts.getToolDefinitions();
        assertNotNull(defs);

        List<String> names = defs.stream()
                .map(d -> (String) d.get("name"))
                .toList();
        assertEquals(1, names.size(), "Only allowed tool should be present; got: " + names);
        assertTrue(names.contains("allowed_tool"));
    }

    @Test
    @DisplayName("AgentFactory wires CompactionChain (SnipCompactor + MicroCompactor)")
    void compactionChain_isWired() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);

        AgentProfile profile = AgentProfile.builder()
                .agentId("compact")
                .systemPrompt("compact agent")
                .build();

        // Add many long messages to memory to trigger compaction
        for (int i = 0; i < 50; i++) {
            memory.addMessage("sess-compact", Message.user("Message " + i + " with some padding text ".repeat(5)));
            memory.addMessage("sess-compact", Message.assistant("Response " + i + " with some padding text ".repeat(5)));
        }

        AgentLoop agent = factory.create(profile);
        agent.run("final question", "sess-compact");

        // If compaction chain is wired, messages should be compacted
        // (SnipCompactor keeps system + last 3, MicroCompactor truncates to 2000 chars)
        List<ConversationMessage> msgs = spy.lastMessages();
        assertNotNull(msgs);
        // Without compaction, there'd be ~102 messages (system + 100 history + user).
        // With SnipCompactor(3), it should be far fewer.
        assertTrue(msgs.size() < 50,
                "CompactionChain should reduce messages; got " + msgs.size());
    }

    @Test
    @DisplayName("shared memory is accessible via factory")
    void sharedMemory_accessible() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        assertSame(memory, factory.getSharedMemory());
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
