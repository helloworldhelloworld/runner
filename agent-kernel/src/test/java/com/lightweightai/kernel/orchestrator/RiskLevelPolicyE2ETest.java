package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in E2E: byRiskLevel 策略端到端
 *
 * 穿越 Orchestrator → AgentFactory → ScopedToolRegistry(ToolPolicy.byRiskLevel) → ToolCallingLoop
 * 验证 SYSTEM 级工具在 max=WRITE 的 agent 下真的不会被执行。
 */
@DisplayName("E2E: byRiskLevel 策略端到端拦截 SYSTEM 工具")
class RiskLevelPolicyE2ETest {

    private ToolRegistry globalRegistry;
    private List<String> toolInvocations;

    @BeforeEach
    void setUp() {
        globalRegistry = new ToolRegistry();
        toolInvocations = new CopyOnWriteArrayList<>();
        globalRegistry.register(tool("read_file", RiskLevel.SAFE));
        globalRegistry.register(tool("write_file", RiskLevel.WRITE));
        globalRegistry.register(tool("shell_exec", RiskLevel.SYSTEM));
    }

    @Test
    @DisplayName("byRiskLevel(WRITE):LLM 尝试 shell_exec → 全链路拦截,execute 未调用")
    void systemToolBlockedEndToEnd() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("bounded")
                .systemPrompt("你是受限助手")
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.WRITE))
                .build());
        registry.setDefault("bounded");

        AgentFactory factory = new AgentFactory(llmRequestsTool("shell_exec"), new NoopMemory(), globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        List<StreamEvent> events = orchestrator.chatStreamReactive(
                GatewayRequest.builder().sessionId("s").message("跑命令").build()
        ).collectList().block();

        assertNotNull(events);
        assertFalse(toolInvocations.contains("shell_exec"),
                "byRiskLevel(WRITE) 下 SYSTEM 工具应被拦截 — invocations: " + toolInvocations);
    }

    @Test
    @DisplayName("byRiskLevel(WRITE):LLM 调 write_file → 放行,execute 真实执行")
    void writeToolAllowedEndToEnd() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("writer")
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.WRITE))
                .build());
        registry.setDefault("writer");

        AgentFactory factory = new AgentFactory(llmRequestsTool("write_file"), new NoopMemory(), globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        orchestrator.chatStreamReactive(
                GatewayRequest.builder().sessionId("s").message("写").build()
        ).collectList().block();

        assertTrue(toolInvocations.contains("write_file"),
                "WRITE 工具应被放行 — invocations: " + toolInvocations);
    }

    // LLM 第一轮请求调用指定工具,第二轮收尾
    private LLMProvider llmRequestsTool(String toolName) {
        return new LLMProvider() {
            int call = 0;
            @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
                call++;
                if (call == 1) {
                    ToolCall tc = new ToolCall("tc_1", toolName, Map.of());
                    return Flux.just(
                            StreamEvent.textDelta("calling " + toolName),
                            StreamEvent.llmComplete(LLMResponse.builder()
                                    .message(ConversationMessage.builder()
                                            .role(ConversationMessage.MessageRole.ASSISTANT)
                                            .textContent("calling " + toolName).build())
                                    .toolCalls(List.of(tc))
                                    .stopReason("tool_use").build()));
                }
                return Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                        .message(ConversationMessage.builder()
                                .role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("done").build())
                        .stopReason("end_turn").build()));
            }
            @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                return LLMResponse.builder().stopReason("end_turn")
                        .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("done").build()).build();
            }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, LLMProvider.StreamEventHandler h) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "test"; }
        };
    }

    private Tool tool(String name, RiskLevel level) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                toolInvocations.add(name);
                return ToolResult.success(name + " ran");
            }
            @Override public RiskLevel riskLevel() { return level; }
        };
    }

    private static class NoopMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
