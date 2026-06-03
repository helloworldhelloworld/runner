package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ListSubagentsTool - list active subagent status")
class ListSubagentsToolTest {

    private AgentRegistry registry;
    private SubagentRuntime runtime;
    private ListSubagentsTool tool;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        AgentProfile profile = AgentProfile.builder()
                .agentId("worker")
                .maxSpawnDepth(1)
                .build();
        registry.register(profile);
        registry.setDefault("worker");

        AgentFactory factory = new AgentFactory(
                new SlowMockProvider(5000), new TestMemory(), new ToolRegistry());
        runtime = new SubagentRuntime(factory, registry, 5);
        tool = new ListSubagentsTool(runtime);
    }

    @AfterEach
    void tearDown() {
        runtime.shutdown();
    }

    @Test
    @DisplayName("getName() returns 'list_subagents'")
    void nameIsListSubagents() {
        assertEquals("list_subagents", tool.getName());
    }

    @Test
    @DisplayName("getSchema() returns empty schema with no properties")
    void schemaIsEmpty() {
        ToolSchema schema = tool.getSchema();
        assertNotNull(schema);
        assertEquals("object", schema.getType());
        assertTrue(schema.getProperties().isEmpty(),
                "Empty schema should have no properties, but got: " + schema.getProperties());
    }

    @Test
    @DisplayName("No active subagents returns 'No active sub-agents.' message")
    void noActiveSubagentsReturnsNoActiveMessage() {
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError(), "Result should not be an error");
        assertEquals("No active sub-agents.", result.getContent());
    }

    @Test
    @DisplayName("Lists active subagents with runId, status, task, and duration")
    void listsActiveSubagentsWithDetails() throws InterruptedException {
        // Spawn two slow tasks so they remain RUNNING during listing
        String runId1 = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("analyze documents")
                .agentId("worker")
                .build(), e -> {});

        String runId2 = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("summarize results")
                .agentId("worker")
                .build(), e -> {});

        // Small delay to ensure tasks are registered in activeRuns
        Thread.sleep(100);

        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError(), "Result should not be an error");
        String content = result.getContent();

        // Verify both task descriptions appear
        assertTrue(content.contains("analyze documents"),
                "Output should contain first task description. Got: " + content);
        assertTrue(content.contains("summarize results"),
                "Output should contain second task description. Got: " + content);

        // Verify RUNNING status
        assertTrue(content.contains("RUNNING"),
                "Output should contain RUNNING status. Got: " + content);

        // Verify duration info
        assertTrue(content.contains("duration:"),
                "Output should contain duration info. Got: " + content);

        // Verify run IDs
        assertTrue(content.contains(runId1),
                "Output should contain first runId '" + runId1 + "'. Got: " + content);
        assertTrue(content.contains(runId2),
                "Output should contain second runId '" + runId2 + "'. Got: " + content);
    }

    @Test
    @DisplayName("Result includes count header 'Active sub-agents (N):'")
    void resultIncludesCount() throws InterruptedException {
        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task-a")
                .agentId("worker")
                .build(), e -> {});

        runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("task-b")
                .agentId("worker")
                .build(), e -> {});

        Thread.sleep(100);

        ToolResult result = tool.execute(Map.of());
        String content = result.getContent();

        assertTrue(content.contains("Active sub-agents (2):"),
                "Output should contain 'Active sub-agents (2):'. Got: " + content);
    }

    @Test
    @DisplayName("Each line format is '- [runId] STATUS | task: TASK | duration: Xms'")
    void resultFormatIncludesTaskAndDuration() throws InterruptedException {
        String runId = runtime.spawn(SpawnRequest.builder()
                .parentSessionKey("agent:worker:main:s1")
                .task("process data")
                .agentId("worker")
                .build(), e -> {});

        Thread.sleep(100);

        ToolResult result = tool.execute(Map.of());
        String content = result.getContent();

        // Verify format: - [runId] RUNNING | task: process data | duration: Xms
        String[] lines = content.split("\n");
        // First line is header, then blank, then entries
        // Find the entry line that contains the runId
        String entryLine = Arrays.stream(lines)
                .filter(l -> l.contains(runId))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No line contains runId '" + runId + "'. Output: " + content));

        assertTrue(entryLine.startsWith("- [" + runId + "]"),
                "Entry line should start with '- [runId]'. Got: " + entryLine);
        assertTrue(entryLine.contains("RUNNING"),
                "Entry line should contain RUNNING. Got: " + entryLine);
        assertTrue(entryLine.contains("| task: process data"),
                "Entry line should contain '| task: process data'. Got: " + entryLine);
        assertTrue(entryLine.matches(".*\\| duration: \\d+ms.*"),
                "Entry line should contain '| duration: <number>ms'. Got: " + entryLine);
    }

    // ==================== Helper classes ====================

    private static class SlowMockProvider implements LLMProvider {
        private final long delayMs;

        SlowMockProvider(long delayMs) {
            this.delayMs = delayMs;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done")
                            .build())
                    .stopReason("end_turn")
                    .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(
                List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.supplyAsync(() -> complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options,
                StreamEventHandler handler) {
            return CompletableFuture.supplyAsync(() -> complete(messages, options));
        }

        @Override
        public ModelCapability getModelCapability() {
            return null;
        }

        @Override
        public String getProviderName() {
            return "slow-mock";
        }
    }

    private static class TestMemory implements MemoryProvider {
        @Override
        public void addMessage(String sessionId, Message message) {}

        @Override
        public List<Message> getHistory(String sessionId, int limit) {
            return List.of();
        }

        @Override
        public void clearSession(String sessionId) {}

        @Override
        public void writeEphemeral(String content) {}

        @Override
        public void writeDurable(String section, String content) {}

        @Override
        public List<MemorySearchResult> search(String query) {
            return List.of();
        }
    }
}
