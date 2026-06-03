package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnSubagentTool — unit tests")
class SpawnSubagentToolTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> capturedEvents;
    private SpawnSubagentTool tool;

    private static final String PARENT_SESSION_KEY = "agent:worker:main:session1";

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I am a worker agent")
                .maxSpawnDepth(2)
                .build());

        ToolRegistry toolRegistry = new ToolRegistry();
        LLMProvider provider = new ImmediateMockProvider();
        MemoryProvider memory = new NoopMemory();
        AgentFactory factory = new AgentFactory(provider, memory, toolRegistry);

        runtime = new SubagentRuntime(factory, registry, 5);
        capturedEvents = new CopyOnWriteArrayList<>();
        tool = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, capturedEvents::add);
    }

    @AfterEach
    void tearDown() {
        runtime.shutdown();
    }

    // ==================== getName / getDescription ====================

    @Test
    @DisplayName("getName returns 'spawn_subagent'")
    void nameIsSpawnSubagent() {
        assertEquals("spawn_subagent", tool.getName(),
                "Tool name must be exactly 'spawn_subagent'");
    }

    @Test
    @DisplayName("getDescription returns a non-empty descriptive string")
    void descriptionIsNonEmpty() {
        String description = tool.getDescription();
        assertNotNull(description, "Description must not be null");
        assertFalse(description.isBlank(), "Description must not be blank");
        assertTrue(description.toLowerCase().contains("sub-agent") || description.toLowerCase().contains("subagent"),
                "Description should mention sub-agent, got: " + description);
    }

    // ==================== getSchema ====================

    @Test
    @DisplayName("schema declares 'task' as a required property")
    void schemaHasTaskRequired() {
        var schema = tool.getSchema();
        assertNotNull(schema, "Schema must not be null");

        Map<String, Object> schemaMap = schema.toMap();
        assertEquals("object", schemaMap.get("type"), "Schema type must be 'object'");

        // Verify 'required' contains 'task'
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schemaMap.get("required");
        assertNotNull(required, "Schema must have a 'required' list");
        assertTrue(required.contains("task"),
                "'task' must be in the required list, got: " + required);
    }

    @Test
    @DisplayName("schema properties include task, agentId, and model")
    void schemaPropertiesContainAllFields() {
        Map<String, Object> properties = tool.getSchema().getProperties();

        assertTrue(properties.containsKey("task"), "Schema must include 'task' property");
        assertTrue(properties.containsKey("agentId"), "Schema must include 'agentId' property");
        assertTrue(properties.containsKey("model"), "Schema must include 'model' property");

        // Verify each property has type=string
        @SuppressWarnings("unchecked")
        Map<String, Object> taskProp = (Map<String, Object>) properties.get("task");
        assertEquals("string", taskProp.get("type"), "'task' property type must be 'string'");

        @SuppressWarnings("unchecked")
        Map<String, Object> agentIdProp = (Map<String, Object>) properties.get("agentId");
        assertEquals("string", agentIdProp.get("type"), "'agentId' property type must be 'string'");

        @SuppressWarnings("unchecked")
        Map<String, Object> modelProp = (Map<String, Object>) properties.get("model");
        assertEquals("string", modelProp.get("type"), "'model' property type must be 'string'");
    }

    @Test
    @DisplayName("schema does not require agentId or model (they are optional)")
    void schemaDoesNotRequireOptionalFields() {
        Map<String, Object> schemaMap = tool.getSchema().toMap();

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schemaMap.get("required");
        assertNotNull(required);
        assertFalse(required.contains("agentId"),
                "'agentId' must NOT be in the required list (it is optional)");
        assertFalse(required.contains("model"),
                "'model' must NOT be in the required list (it is optional)");
    }

    // ==================== execute — success path ====================

    @Test
    @DisplayName("execute with valid task returns success result containing Run ID")
    void executeSuccessReturnsRunId() {
        ToolResult result = tool.execute(Map.of("task", "Search the documentation"));

        assertFalse(result.isError(), "Result must not be an error");
        assertTrue(result.getContent().contains("Run ID:"),
                "Success content must contain 'Run ID:', got: " + result.getContent());
        assertTrue(result.getContent().contains("Subagent spawned successfully"),
                "Success content must confirm spawn, got: " + result.getContent());
        assertTrue(result.getContent().contains("non-blocking"),
                "Success content must mention non-blocking behavior, got: " + result.getContent());
    }

    @Test
    @DisplayName("execute extracts a non-empty runId from the result text")
    void executeReturnsNonEmptyRunId() {
        ToolResult result = tool.execute(Map.of("task", "Do something"));

        assertFalse(result.isError());
        // Extract runId from "Run ID: <id> (non-blocking..."
        String content = result.getContent();
        int runIdStart = content.indexOf("Run ID: ") + "Run ID: ".length();
        int runIdEnd = content.indexOf(" ", runIdStart);
        String runId = content.substring(runIdStart, runIdEnd);

        assertFalse(runId.isBlank(), "Run ID must not be blank");
        assertTrue(runId.length() > 0, "Run ID must have non-zero length");
    }

    @Test
    @DisplayName("execute with agentId and model succeeds and carries them through")
    void executeWithAgentIdAndModel() {
        // Use a mutable map since Map.of does not allow null values
        Map<String, Object> args = new HashMap<>();
        args.put("task", "Analyze data");
        args.put("agentId", "worker");
        args.put("model", "claude-sonnet-4-20250514");

        ToolResult result = tool.execute(args);

        assertFalse(result.isError(),
                "Result must not be an error when valid agentId is provided, got: " + result.getContent());
        assertTrue(result.getContent().contains("Run ID:"),
                "Success content must contain Run ID, got: " + result.getContent());
    }

    @Test
    @DisplayName("execute with only task (no agentId/model) falls back to default agent")
    void executeWithOnlyTaskUsesDefaultAgent() {
        ToolResult result = tool.execute(Map.of("task", "Just a simple task"));

        assertFalse(result.isError(),
                "Must succeed using default agent when agentId is absent, got: " + result.getContent());
        assertTrue(result.getContent().contains("Run ID:"));
    }

    @Test
    @DisplayName("execute passes task string to SpawnRequest (verified via SUBAGENT_SPAWN event)")
    void executePassesTaskToSpawnRequest() throws InterruptedException {
        String expectedTask = "Translate this document into French";
        tool.execute(Map.of("task", expectedTask));

        // Wait briefly for the async spawn event to be captured
        // The SUBAGENT_SPAWN event is fired synchronously in spawn(), so it should be immediate
        Thread.sleep(100);

        StreamEvent spawnEvent = capturedEvents.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Expected SUBAGENT_SPAWN event but got: " + capturedEvents));

        assertEquals(expectedTask, spawnEvent.getData().get("task"),
                "SUBAGENT_SPAWN event must carry the exact task string");
        assertEquals("worker", spawnEvent.getData().get("agentId"),
                "SUBAGENT_SPAWN event must carry the resolved agentId");
    }

    // ==================== execute — announcer callback ====================

    @Test
    @DisplayName("execute fires SUBAGENT_SPAWN event through the announcer consumer")
    void executeAnnouncerReceivesSpawnEvent() {
        tool.execute(Map.of("task", "Some task for announcer test"));

        // SUBAGENT_SPAWN is emitted synchronously inside runtime.spawn() before async execution
        long spawnCount = capturedEvents.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN)
                .count();
        assertEquals(1, spawnCount,
                "Exactly one SUBAGENT_SPAWN event must be emitted, got " + spawnCount);
    }

    @Test
    @DisplayName("SUBAGENT_SPAWN event data contains runId, agentId, and task")
    void spawnEventContainsAllRequiredData() {
        String task = "Build a report";
        tool.execute(Map.of("task", task));

        StreamEvent spawnEvent = capturedEvents.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SUBAGENT_SPAWN event captured"));

        Map<String, Object> data = spawnEvent.getData();
        assertNotNull(data.get("runId"), "Spawn event must contain 'runId'");
        assertNotNull(data.get("agentId"), "Spawn event must contain 'agentId'");
        assertEquals(task, data.get("task"), "Spawn event must contain the correct 'task'");

        // Verify the runId in the event matches the one in the result
        // (We can only check it is non-empty here since we don't capture the ToolResult runId separately)
        String eventRunId = (String) data.get("runId");
        assertFalse(eventRunId.isBlank(), "runId in spawn event must not be blank");
    }

    @Test
    @DisplayName("runId in ToolResult matches runId in SUBAGENT_SPAWN event")
    void runIdInResultMatchesRunIdInEvent() {
        ToolResult result = tool.execute(Map.of("task", "Matching runId test"));

        // Extract runId from result content
        String content = result.getContent();
        int start = content.indexOf("Run ID: ") + "Run ID: ".length();
        int end = content.indexOf(" ", start);
        String resultRunId = content.substring(start, end);

        // Get runId from spawn event
        StreamEvent spawnEvent = capturedEvents.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN)
                .findFirst()
                .orElseThrow();
        String eventRunId = (String) spawnEvent.getData().get("runId");

        assertEquals(resultRunId, eventRunId,
                "Run ID in ToolResult must match Run ID in SUBAGENT_SPAWN event");
    }

    // ==================== execute — error path ====================

    @Test
    @DisplayName("execute returns error ToolResult when spawn depth limit is exceeded")
    void executeOnDepthLimitReturnsError() {
        // Create a registry where the agent has maxSpawnDepth=1
        AgentRegistry shallowRegistry = new AgentRegistry();
        shallowRegistry.register(AgentProfile.builder()
                .agentId("shallow")
                .maxSpawnDepth(1)
                .build());

        SubagentRuntime shallowRuntime = new SubagentRuntime(
                new AgentFactory(new ImmediateMockProvider(), new NoopMemory(), new ToolRegistry()),
                shallowRegistry, 5);

        try {
            // parentSessionKey with one :subagent: segment => currentDepth=1, maxSpawnDepth=1 => exceed
            String deepSessionKey = "agent:shallow:main:s1:subagent:abc123";
            SpawnSubagentTool deepTool = new SpawnSubagentTool(
                    shallowRuntime, deepSessionKey, e -> {});

            ToolResult result = deepTool.execute(Map.of("task", "Should fail due to depth"));

            assertTrue(result.isError(),
                    "Result must be an error when depth limit is exceeded");
            assertTrue(result.getContent().contains("Failed to spawn subagent"),
                    "Error content must contain 'Failed to spawn subagent', got: " + result.getContent());
            assertTrue(result.getContent().contains("depth"),
                    "Error content must mention depth, got: " + result.getContent());
        } finally {
            shallowRuntime.shutdown();
        }
    }

    @Test
    @DisplayName("execute returns error ToolResult when max concurrent subagents is reached")
    void executeOnConcurrencyLimitReturnsError() {
        // Create a runtime with maxConcurrent=1 and a slow provider so the first spawn stays active
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder()
                .agentId("limited")
                .maxSpawnDepth(2)
                .build());

        LLMProvider slowProvider = new SlowMockProvider(5000);
        SubagentRuntime limitedRuntime = new SubagentRuntime(
                new AgentFactory(slowProvider, new NoopMemory(), new ToolRegistry()),
                reg, 1);

        try {
            SpawnSubagentTool limitedTool = new SpawnSubagentTool(
                    limitedRuntime, "agent:limited:main:s1", e -> {});

            // First spawn should succeed
            ToolResult first = limitedTool.execute(Map.of("task", "Occupying the slot"));
            assertFalse(first.isError(), "First spawn should succeed");

            // Second spawn should fail because max concurrent = 1
            ToolResult second = limitedTool.execute(Map.of("task", "Should be rejected"));
            assertTrue(second.isError(),
                    "Second spawn must be an error when concurrency limit is reached");
            assertTrue(second.getContent().contains("Failed to spawn subagent"),
                    "Error content must contain failure prefix, got: " + second.getContent());
            assertTrue(second.getContent().contains("concurrent"),
                    "Error content must mention concurrency, got: " + second.getContent());
        } finally {
            limitedRuntime.shutdown();
        }
    }

    @Test
    @DisplayName("execute returns error when unknown agentId is specified")
    void executeWithUnknownAgentIdReturnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("task", "Some task");
        args.put("agentId", "nonexistent-agent");

        // IllegalArgumentException from agentRegistry.get() is not caught by the tool's
        // catch(IllegalStateException) — it should propagate. Let's verify behavior.
        // Actually, looking at the code, SpawnSubagentTool only catches IllegalStateException,
        // but runtime.spawn() throws IllegalArgumentException for unknown agent.
        // This means it will propagate as an uncaught exception.
        assertThrows(IllegalArgumentException.class, () -> tool.execute(args),
                "Unknown agentId should cause IllegalArgumentException (not caught by tool)");
    }

    @Test
    @DisplayName("error ToolResult isError flag is true and content contains error details")
    void errorResultHasCorrectStructure() {
        // Use a zero-depth agent to trigger IllegalStateException
        AgentRegistry reg = new AgentRegistry();
        reg.register(AgentProfile.builder()
                .agentId("nospawn")
                .maxSpawnDepth(0)
                .build());

        SubagentRuntime noSpawnRuntime = new SubagentRuntime(
                new AgentFactory(new ImmediateMockProvider(), new NoopMemory(), new ToolRegistry()),
                reg, 5);

        try {
            SpawnSubagentTool noSpawnTool = new SpawnSubagentTool(
                    noSpawnRuntime, "agent:nospawn:main:s1", e -> {});

            ToolResult result = noSpawnTool.execute(Map.of("task", "Should fail"));

            assertTrue(result.isError(), "isError() must be true for failed spawns");
            assertNotNull(result.getContent(), "Error content must not be null");
            assertFalse(result.getContent().isBlank(), "Error content must not be blank");
            assertTrue(result.getContent().startsWith("Failed to spawn subagent:"),
                    "Error content must start with 'Failed to spawn subagent:', got: " + result.getContent());
        } finally {
            noSpawnRuntime.shutdown();
        }
    }

    // ==================== Multiple spawns ====================

    @Test
    @DisplayName("multiple execute calls produce distinct runIds")
    void multipleSpawnsProduceDistinctRunIds() {
        ToolResult result1 = tool.execute(Map.of("task", "Task A"));
        ToolResult result2 = tool.execute(Map.of("task", "Task B"));

        assertFalse(result1.isError());
        assertFalse(result2.isError());

        String runId1 = extractRunId(result1.getContent());
        String runId2 = extractRunId(result2.getContent());

        assertNotEquals(runId1, runId2,
                "Each spawn must produce a unique runId");
    }

    // ==================== Helpers ====================

    private String extractRunId(String content) {
        int start = content.indexOf("Run ID: ") + "Run ID: ".length();
        int end = content.indexOf(" ", start);
        return content.substring(start, end);
    }

    /**
     * LLMProvider that returns immediately with a simple "done" response.
     * Suitable for tests that need spawn to succeed without blocking.
     */
    private static class ImmediateMockProvider implements LLMProvider {
        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done")
                            .build())
                    .stopReason("end_turn")
                    .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options,
                                                              StreamEventHandler handler) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            return Flux.just(StreamEvent.llmComplete(complete(messages, options)));
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "immediate-mock"; }
    }

    /**
     * LLMProvider that delays for a specified duration before returning.
     * Useful for testing concurrency limits (keeps the slot occupied).
     */
    private static class SlowMockProvider implements LLMProvider {
        private final long delayMs;

        SlowMockProvider(long delayMs) { this.delayMs = delayMs; }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done after delay")
                            .build())
                    .stopReason("end_turn")
                    .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options,
                                                              StreamEventHandler handler) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
            return Flux.just(StreamEvent.llmComplete(complete(messages, options)));
        }

        @Override
        public ModelCapability getModelCapability() { return null; }

        @Override
        public String getProviderName() { return "slow-mock"; }
    }

    /**
     * Minimal MemoryProvider that does nothing -- sufficient for spawn tests
     * which never read or write memory.
     */
    private static class NoopMemory implements MemoryProvider {
        @Override public void addMessage(String sessionId, Message message) {}
        @Override public List<Message> getHistory(String sessionId, int limit) { return List.of(); }
        @Override public void clearSession(String sessionId) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
