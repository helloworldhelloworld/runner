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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in Acceptance Test: 工具权限隔离
 *
 * 验证：Orchestrator → AgentFactory → ScopedToolRegistry → ToolCallingLoop 的完整链路。
 * restricted agent 的 deny list 中的工具不应被调用。
 */
@DisplayName("Acceptance: 工具权限隔离")
class ToolIsolationAcceptanceTest {

    private ToolRegistry globalRegistry;
    private List<String> toolInvocations;

    @BeforeEach
    void setUp() {
        globalRegistry = new ToolRegistry();
        toolInvocations = new CopyOnWriteArrayList<>();

        // 全局注册 3 个工具
        globalRegistry.register(trackingTool("safe_search"));
        globalRegistry.register(trackingTool("shell_exec"));
        globalRegistry.register(trackingTool("read_file"));
    }

    @Test
    @DisplayName("restricted agent 只能调用 allow list 中的工具，deny list 中的工具被阻止")
    void restrictedAgentCannotCallDeniedTools() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("restricted")
                .systemPrompt("你是受限助手")
                .toolDenyList(Set.of("shell_exec"))
                .build());
        registry.setDefault("restricted");

        // LLM 尝试调用 shell_exec（被 deny 的工具）
        AtomicReference<String> toolCalledByLLM = new AtomicReference<>();
        LLMProvider tryDeniedTool = new LLMProvider() {
            int call = 0;
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
                call++;
                if (call == 1) {
                    // 第一轮：尝试调用 shell_exec
                    ToolCall tc = new ToolCall("tc_1", "shell_exec", Map.of("cmd", "rm -rf /"));
                    toolCalledByLLM.set("shell_exec");
                    return Flux.just(
                            StreamEvent.textDelta("let me run a command"),
                            StreamEvent.llmComplete(LLMResponse.builder()
                                    .message(ConversationMessage.builder()
                                            .role(ConversationMessage.MessageRole.ASSISTANT)
                                            .textContent("let me run a command").build())
                                    .toolCalls(List.of(tc))
                                    .stopReason("tool_use").build()));
                } else {
                    // 后续轮：不调用工具
                    return Flux.just(
                            StreamEvent.textDelta("done"),
                            StreamEvent.llmComplete(LLMResponse.builder()
                                    .message(ConversationMessage.builder()
                                            .role(ConversationMessage.MessageRole.ASSISTANT)
                                            .textContent("done").build())
                                    .stopReason("end_turn").build()));
                }
            }
            @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                return LLMResponse.builder().stopReason("end_turn")
                        .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("done").build()).build();
            }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "try-denied"; }
        };

        TestMemory memory = new TestMemory();
        AgentFactory factory = new AgentFactory(tryDeniedTool, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-restricted")
                .message("执行一个命令")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();

        // shell_exec 不应被实际执行（不在 toolInvocations 中）
        assertFalse(toolInvocations.contains("shell_exec"),
                "Denied tool 'shell_exec' should NOT be invoked. Invocations: " + toolInvocations);
    }

    @Test
    @DisplayName("open agent 可以调用所有工具")
    void openAgentCanCallAllTools() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("open")
                .systemPrompt("你是开放助手")
                .build());  // 无 allow/deny → 全部放行
        registry.setDefault("open");

        // LLM 调用 safe_search
        LLMProvider callSafeTool = new LLMProvider() {
            int call = 0;
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
                call++;
                if (call == 1) {
                    ToolCall tc = new ToolCall("tc_1", "safe_search", Map.of("q", "hello"));
                    return Flux.just(
                            StreamEvent.textDelta("searching"),
                            StreamEvent.llmComplete(LLMResponse.builder()
                                    .message(ConversationMessage.builder()
                                            .role(ConversationMessage.MessageRole.ASSISTANT)
                                            .textContent("searching").build())
                                    .toolCalls(List.of(tc))
                                    .stopReason("tool_use").build()));
                } else {
                    return Flux.just(
                            StreamEvent.textDelta("found results"),
                            StreamEvent.llmComplete(LLMResponse.builder()
                                    .message(ConversationMessage.builder()
                                            .role(ConversationMessage.MessageRole.ASSISTANT)
                                            .textContent("found results").build())
                                    .stopReason("end_turn").build()));
                }
            }
            @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                return LLMResponse.builder().stopReason("end_turn")
                        .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("done").build()).build();
            }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "call-safe"; }
        };

        TestMemory memory = new TestMemory();
        AgentFactory factory = new AgentFactory(callSafeTool, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-open")
                .message("搜索一下")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();

        // Debug: dump error event details
        List<String> errorMsgs = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TOOL_ERROR)
                .map(e -> e.getChunk() != null ? e.getChunk().getMessage() : "null")
                .toList();

        assertTrue(toolInvocations.contains("safe_search"),
                "Open agent should call safe_search. TOOL_ERRORs: " + errorMsgs
                + " Invocations: " + toolInvocations);
    }

    private Tool trackingTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                toolInvocations.add(name);
                return ToolResult.success(name + " result");
            }
        };
    }

    private static class TestMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
