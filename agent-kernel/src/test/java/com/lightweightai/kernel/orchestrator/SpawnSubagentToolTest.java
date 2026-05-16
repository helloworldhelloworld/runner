package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpawnSubagentTool - spawn_subagent 工具的单元测试")
class SpawnSubagentToolTest {

    private SubagentRuntime runtime;
    private List<StreamEvent> capturedEvents;
    private static final String PARENT_SESSION_KEY = "agent:worker:main:s1";

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("worker")
                .systemPrompt("I am worker")
                .maxSpawnDepth(1)
                .build());
        registry.register(AgentProfile.builder()
                .agentId("researcher")
                .systemPrompt("I research")
                .maxSpawnDepth(2)
                .build());
        registry.register(AgentProfile.builder()
                .agentId("no-spawn")
                .systemPrompt("I cannot spawn")
                .maxSpawnDepth(0)
                .build());

        TestMemory memory = new TestMemory();
        ToolRegistry tools = new ToolRegistry();
        LLMProvider provider = new FastMockProvider();
        AgentFactory factory = new AgentFactory(provider, memory, tools);
        runtime = new SubagentRuntime(factory, registry, 5);
        capturedEvents = Collections.synchronizedList(new ArrayList<>());
    }

    // ==================== getName ====================

    @Test
    @DisplayName("getName 返回 'spawn_subagent'")
    void getNameReturnsSpawnSubagent() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, e -> {});

        assertEquals("spawn_subagent", tool.getName(),
                "工具名称必须是 spawn_subagent，用于 LLM tool_use 匹配");
    }

    // ==================== getDescription ====================

    @Test
    @DisplayName("getDescription 返回非空描述")
    void getDescriptionReturnsNonEmpty() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, e -> {});

        String description = tool.getDescription();
        assertNotNull(description);
        assertFalse(description.isEmpty(), "描述不能为空，LLM 需要它来理解工具用途");
        assertTrue(description.toLowerCase().contains("sub-agent") || description.toLowerCase().contains("subagent"),
                "描述应提及 subagent，实际为: " + description);
    }

    // ==================== getSchema ====================

    @Test
    @DisplayName("getSchema 包含 'task' 作为必填属性")
    void getSchemaContainsTaskAsRequired() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, e -> {});

        ToolSchema schema = tool.getSchema();
        Map<String, Object> schemaMap = schema.toMap();

        // schema 应含 "task" 属性
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
        assertTrue(properties.containsKey("task"),
                "Schema properties 应包含 'task'，实际 keys: " + properties.keySet());

        // "task" 应在 required 列表中
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schemaMap.get("required");
        assertNotNull(required, "Schema 应有 required 列表");
        assertTrue(required.contains("task"),
                "task 应在 required 列表中，实际 required: " + required);
    }

    @Test
    @DisplayName("getSchema 包含 'agentId' 和 'model' 作为可选属性")
    void getSchemaContainsOptionalProperties() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, e -> {});

        Map<String, Object> schemaMap = tool.getSchema().toMap();

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
        assertTrue(properties.containsKey("agentId"),
                "Schema properties 应包含 'agentId'");
        assertTrue(properties.containsKey("model"),
                "Schema properties 应包含 'model'");

        // agentId 和 model 不应在 required 列表中
        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schemaMap.get("required");
        assertFalse(required.contains("agentId"),
                "agentId 不应在 required 列表中（它是可选的）");
        assertFalse(required.contains("model"),
                "model 不应在 required 列表中（它是可选的）");
    }

    // ==================== execute — 成功场景 ====================

    @Test
    @DisplayName("execute 传入有效 task 时返回成功结果，内容包含 Run ID")
    void executeWithValidTaskReturnsSuccessWithRunId() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, capturedEvents::add);

        ToolResult result = tool.execute(Map.of("task", "搜索文档并总结"));

        assertFalse(result.isError(), "execute 应返回成功结果，实际: " + result);
        assertTrue(result.getContent().contains("Run ID:"),
                "成功结果应包含 'Run ID:'，实际内容: " + result.getContent());

        // 提取并验证 runId 非空
        String content = result.getContent();
        String runIdPart = content.substring(content.indexOf("Run ID:") + "Run ID:".length()).trim();
        assertFalse(runIdPart.isEmpty(), "Run ID 值不应为空");
    }

    @Test
    @DisplayName("execute 返回的 runId 对应 runtime 中已注册的 run")
    void executeRunIdIsRegisteredInRuntime() throws InterruptedException {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, capturedEvents::add);

        ToolResult result = tool.execute(Map.of("task", "任务A"));
        assertFalse(result.isError());

        // 从内容中提取 runId
        String content = result.getContent();
        // 格式: "Subagent spawned successfully. Run ID: <id> (non-blocking, ...)"
        String runId = content.split("Run ID: ")[1].split(" ")[0];

        // runId 对应的 run 应可在 runtime 中找到（active 或 completed）
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS),
                "Spawned subagent should complete within timeout");
        SubagentRun run = runtime.getRunOrCompleted(runId);
        assertNotNull(run, "runtime 应能通过 runId 找到对应的 SubagentRun");
        assertEquals(runId, run.getRunId(),
                "找到的 SubagentRun 的 runId 应匹配");
    }

    // ==================== execute — 传递 agentId ====================

    @Test
    @DisplayName("execute 传入 task + agentId 时，SpawnRequest 携带正确的 agentId")
    void executeWithAgentIdPassesItToSpawnRequest() throws InterruptedException {
        // 使用捕获型 runtime 来验证 SpawnRequest 的 agentId
        AtomicReference<SpawnRequest> capturedRequest = new AtomicReference<>();
        SubagentRuntime capturingRuntime = new CapturingSubagentRuntime(runtime, capturedRequest);

        SpawnSubagentTool tool = new SpawnSubagentTool(capturingRuntime, PARENT_SESSION_KEY, capturedEvents::add);

        ToolResult result = tool.execute(Map.of("task", "分析数据", "agentId", "researcher"));
        assertFalse(result.isError(), "execute 应成功，实际: " + result);

        // 验证 runtime.spawn() 收到的 SpawnRequest 携带正确 agentId
        // 由于 CapturingSubagentRuntime 是代理模式，我们需要通过检查 spawned run 来验证
        // 直接通过 result 内容提取 runId 验证
        String runId = extractRunId(result);
        assertTrue(capturingRuntime.waitForCompletion(runId, 5, TimeUnit.SECONDS));

        SpawnRequest captured = capturedRequest.get();
        assertNotNull(captured, "应捕获到 SpawnRequest");
        assertEquals("researcher", captured.getAgentId(),
                "SpawnRequest.agentId 应为传入的 'researcher'");
        assertEquals("分析数据", captured.getTask(),
                "SpawnRequest.task 应为传入的 '分析数据'");
    }

    // ==================== execute — 传递 model ====================

    @Test
    @DisplayName("execute 传入 task + model 时，SpawnRequest 携带正确的 modelOverride")
    void executeWithModelPassesItToSpawnRequest() throws InterruptedException {
        AtomicReference<SpawnRequest> capturedRequest = new AtomicReference<>();
        SubagentRuntime capturingRuntime = new CapturingSubagentRuntime(runtime, capturedRequest);

        SpawnSubagentTool tool = new SpawnSubagentTool(capturingRuntime, PARENT_SESSION_KEY, capturedEvents::add);

        ToolResult result = tool.execute(Map.of("task", "翻译文档", "model", "claude-sonnet-4-20250514"));
        assertFalse(result.isError(), "execute 应成功，实际: " + result);

        String runId = extractRunId(result);
        assertTrue(capturingRuntime.waitForCompletion(runId, 5, TimeUnit.SECONDS));

        SpawnRequest captured = capturedRequest.get();
        assertNotNull(captured, "应捕获到 SpawnRequest");
        assertEquals("claude-sonnet-4-20250514", captured.getModelOverride(),
                "SpawnRequest.modelOverride 应为传入的 model");
    }

    // ==================== execute — 同时传递 agentId + model ====================

    @Test
    @DisplayName("execute 同时传入 task + agentId + model 时，三个字段都正确传递")
    void executeWithAllArgsPassesAllToSpawnRequest() throws InterruptedException {
        AtomicReference<SpawnRequest> capturedRequest = new AtomicReference<>();
        SubagentRuntime capturingRuntime = new CapturingSubagentRuntime(runtime, capturedRequest);

        SpawnSubagentTool tool = new SpawnSubagentTool(capturingRuntime, PARENT_SESSION_KEY, capturedEvents::add);

        ToolResult result = tool.execute(Map.of(
                "task", "复杂分析任务",
                "agentId", "researcher",
                "model", "claude-opus-4-20250514"
        ));
        assertFalse(result.isError());

        String runId = extractRunId(result);
        assertTrue(capturingRuntime.waitForCompletion(runId, 5, TimeUnit.SECONDS));

        SpawnRequest captured = capturedRequest.get();
        assertNotNull(captured);
        assertEquals("复杂分析任务", captured.getTask());
        assertEquals("researcher", captured.getAgentId());
        assertEquals("claude-opus-4-20250514", captured.getModelOverride());
    }

    // ==================== execute — parentSessionKey 传递 ====================

    @Test
    @DisplayName("execute 构建的 SpawnRequest 携带构造器中的 parentSessionKey")
    void executePassesParentSessionKeyFromConstructor() throws InterruptedException {
        String specificSessionKey = "agent:researcher:main:sess-42";
        AtomicReference<SpawnRequest> capturedRequest = new AtomicReference<>();
        SubagentRuntime capturingRuntime = new CapturingSubagentRuntime(runtime, capturedRequest);

        SpawnSubagentTool tool = new SpawnSubagentTool(capturingRuntime, specificSessionKey, capturedEvents::add);

        ToolResult result = tool.execute(Map.of("task", "一个任务"));
        assertFalse(result.isError());

        String runId = extractRunId(result);
        assertTrue(capturingRuntime.waitForCompletion(runId, 5, TimeUnit.SECONDS));

        SpawnRequest captured = capturedRequest.get();
        assertNotNull(captured);
        assertEquals(specificSessionKey, captured.getParentSessionKey(),
                "SpawnRequest.parentSessionKey 应为构造器传入的值");
    }

    // ==================== execute — 错误场景 ====================

    @Test
    @DisplayName("runtime.spawn 抛出 IllegalStateException 时返回错误 ToolResult")
    void executeReturnsErrorWhenRuntimeThrows() {
        // no-spawn agent 的 maxSpawnDepth=0，spawn 会抛 IllegalStateException
        SpawnSubagentTool tool = new SpawnSubagentTool(
                runtime, "agent:no-spawn:main:s1", capturedEvents::add);

        ToolResult result = tool.execute(Map.of("task", "任务", "agentId", "no-spawn"));

        assertTrue(result.isError(),
                "runtime.spawn 抛异常时应返回错误 ToolResult");
        assertTrue(result.getContent().contains("Failed to spawn subagent"),
                "错误内容应包含 'Failed to spawn subagent'，实际: " + result.getContent());
    }

    @Test
    @DisplayName("runtime.spawn 抛出 IllegalStateException 时，错误消息包含异常原因")
    void executeErrorContainsExceptionMessage() {
        SpawnSubagentTool tool = new SpawnSubagentTool(
                runtime, "agent:no-spawn:main:s1", capturedEvents::add);

        ToolResult result = tool.execute(Map.of("task", "任务", "agentId", "no-spawn"));

        assertTrue(result.isError());
        // 异常消息应包含 maxSpawnDepth=0 的相关信息
        assertTrue(result.getContent().contains("maxSpawnDepth=0")
                        || result.getContent().contains("cannot spawn"),
                "错误消息应包含深度限制信息，实际: " + result.getContent());
    }

    // ==================== announcer 传递验证 ====================

    @Test
    @DisplayName("announcer 回调被传递到 runtime.spawn 并收到 SUBAGENT_SPAWN 事件")
    void announcerReceivesSpawnEvent() throws InterruptedException {
        List<StreamEvent> announcerEvents = Collections.synchronizedList(new ArrayList<>());
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, announcerEvents::add);

        ToolResult result = tool.execute(Map.of("task", "测试 announcer"));
        assertFalse(result.isError());

        String runId = extractRunId(result);
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);  // 等待异步事件回调完成

        // 验证 announcer 收到了 SUBAGENT_SPAWN 事件
        boolean hasSpawnEvent = announcerEvents.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.SUBAGENT_SPAWN
                        && runId.equals(e.getData().get("runId")));
        assertTrue(hasSpawnEvent,
                "announcer 应收到 SUBAGENT_SPAWN 事件，实际事件: " + announcerEvents);
    }

    @Test
    @DisplayName("announcer 回调被传递到 runtime.spawn 并在完成后收到 SUBAGENT_COMPLETE 事件")
    void announcerReceivesCompleteEvent() throws InterruptedException {
        List<StreamEvent> announcerEvents = Collections.synchronizedList(new ArrayList<>());
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, announcerEvents::add);

        ToolResult result = tool.execute(Map.of("task", "测试完成事件"));
        assertFalse(result.isError());

        String runId = extractRunId(result);
        assertTrue(runtime.waitForCompletion(runId, 5, TimeUnit.SECONDS));
        Thread.sleep(100);

        // 验证 announcer 收到了 SUBAGENT_COMPLETE 事件
        boolean hasCompleteEvent = announcerEvents.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.SUBAGENT_COMPLETE
                        && runId.equals(e.getData().get("runId")));
        assertTrue(hasCompleteEvent,
                "announcer 应收到 SUBAGENT_COMPLETE 事件，实际事件: " + announcerEvents);
    }

    @Test
    @DisplayName("不同的 SpawnSubagentTool 实例使用不同的 announcer，事件互不干扰")
    void differentAnnouncersAreIsolated() throws InterruptedException {
        List<StreamEvent> announcerA = Collections.synchronizedList(new ArrayList<>());
        List<StreamEvent> announcerB = Collections.synchronizedList(new ArrayList<>());

        SpawnSubagentTool toolA = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, announcerA::add);
        SpawnSubagentTool toolB = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, announcerB::add);

        ToolResult resultA = toolA.execute(Map.of("task", "任务A"));
        ToolResult resultB = toolB.execute(Map.of("task", "任务B"));

        String runIdA = extractRunId(resultA);
        String runIdB = extractRunId(resultB);

        assertTrue(runtime.waitForCompletion(runIdA, 5, TimeUnit.SECONDS));
        assertTrue(runtime.waitForCompletion(runIdB, 5, TimeUnit.SECONDS));
        Thread.sleep(100);

        // announcerA 应只有 runIdA 的事件
        boolean aHasB = announcerA.stream()
                .anyMatch(e -> runIdB.equals(e.getData().get("runId")));
        assertFalse(aHasB, "announcerA 不应收到 runIdB 的事件");

        // announcerB 应只有 runIdB 的事件
        boolean bHasA = announcerB.stream()
                .anyMatch(e -> runIdA.equals(e.getData().get("runId")));
        assertFalse(bHasA, "announcerB 不应收到 runIdA 的事件");
    }

    // ==================== Tool 接口契约 ====================

    @Test
    @DisplayName("SpawnSubagentTool 实现 Tool 接口")
    void implementsToolInterface() {
        SpawnSubagentTool tool = new SpawnSubagentTool(runtime, PARENT_SESSION_KEY, e -> {});

        assertTrue(tool instanceof Tool,
                "SpawnSubagentTool 必须实现 Tool 接口");
    }

    @Test
    @DisplayName("execute 是非阻塞的 — 立即返回而非等待子 agent 完成")
    void executeIsNonBlocking() {
        // 使用慢 provider 验证 execute 不会阻塞
        AgentRegistry slowRegistry = new AgentRegistry();
        slowRegistry.register(AgentProfile.builder()
                .agentId("slow-worker")
                .systemPrompt("I am slow")
                .maxSpawnDepth(1)
                .build());
        LLMProvider slowProvider = new SlowMockProvider(2000);
        AgentFactory slowFactory = new AgentFactory(slowProvider, new TestMemory(), new ToolRegistry());
        SubagentRuntime slowRuntime = new SubagentRuntime(slowFactory, slowRegistry, 5);

        SpawnSubagentTool tool = new SpawnSubagentTool(
                slowRuntime, "agent:slow-worker:main:s1", e -> {});

        long start = System.currentTimeMillis();
        ToolResult result = tool.execute(Map.of("task", "慢任务", "agentId", "slow-worker"));
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(result.isError());
        assertTrue(elapsed < 500,
                "execute 应立即返回（非阻塞），但耗时 " + elapsed + "ms");

        slowRuntime.stopAll();
    }

    // ==================== Helper: extractRunId ====================

    private String extractRunId(ToolResult result) {
        String content = result.getContent();
        // 格式: "Subagent spawned successfully. Run ID: <id> (non-blocking, ...)"
        String afterRunId = content.substring(content.indexOf("Run ID:") + "Run ID:".length()).trim();
        return afterRunId.split("\\s")[0];
    }

    // ==================== Helper: CapturingSubagentRuntime ====================

    /**
     * 代理模式的 SubagentRuntime，捕获 spawn() 收到的 SpawnRequest。
     *
     * 遵循 CLAUDE.md 规则：「Mocks MUST capture, not swallow」
     * 使用 AtomicReference 记录输入，测试后断言其内容。
     */
    private static class CapturingSubagentRuntime extends SubagentRuntime {
        private final SubagentRuntime delegate;
        private final AtomicReference<SpawnRequest> capturedRequest;

        CapturingSubagentRuntime(SubagentRuntime delegate,
                                 AtomicReference<SpawnRequest> capturedRequest) {
            // 调用父构造器满足编译要求（不会真正使用父类功能）
            super(new AgentFactory(new FastMockProvider(), new TestMemory(), new ToolRegistry()),
                    createMinimalRegistry(), 1);
            this.delegate = delegate;
            this.capturedRequest = capturedRequest;
        }

        @Override
        public String spawn(SpawnRequest request, Consumer<StreamEvent> announcer) {
            capturedRequest.set(request);
            return delegate.spawn(request, announcer);
        }

        @Override
        public boolean waitForCompletion(String runId, long timeout, TimeUnit unit)
                throws InterruptedException {
            return delegate.waitForCompletion(runId, timeout, unit);
        }

        @Override
        public SubagentRun getRunOrCompleted(String runId) {
            return delegate.getRunOrCompleted(runId);
        }

        private static AgentRegistry createMinimalRegistry() {
            AgentRegistry reg = new AgentRegistry();
            reg.register(AgentProfile.builder()
                    .agentId("dummy")
                    .systemPrompt("dummy")
                    .maxSpawnDepth(1)
                    .build());
            return reg;
        }
    }

    // ==================== Helper classes ====================

    private static class FastMockProvider implements LLMProvider {
        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(
                    StreamEvent.textDelta("done"),
                    StreamEvent.llmComplete(buildResponse()));
        }
        private LLMResponse buildResponse() {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent("done").build())
                    .stopReason("end_turn").build();
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            return buildResponse();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "fast-mock"; }
    }

    private static class SlowMockProvider implements LLMProvider {
        private final long delayMs;
        SlowMockProvider(long delayMs) { this.delayMs = delayMs; }
        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.textDelta("working..."))
                    .delayElements(java.time.Duration.ofMillis(delayMs))
                    .concatWith(Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent("done").build())
                            .stopReason("end_turn").build())));
        }
        @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            return LLMResponse.builder().stopReason("end_turn").build();
        }
        @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            return CompletableFuture.completedFuture(complete(m, o));
        }
        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return "slow-mock"; }
    }

    private static class TestMemory implements MemoryProvider {
        private final List<Message> messages = new ArrayList<>();
        @Override public void addMessage(String sessionId, Message message) { messages.add(message); }
        @Override public List<Message> getHistory(String sessionId, int limit) {
            int start = Math.max(0, messages.size() - limit);
            return new ArrayList<>(messages.subList(start, messages.size()));
        }
        @Override public void clearSession(String sessionId) { messages.clear(); }
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
