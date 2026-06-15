package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Outside-in Acceptance Test: TriggerSource SPI 端到端
 *
 * 之前 Orchestrator 只接受外部 GatewayRequest 入口。本测试证明:订阅一个
 * TriggerSource 后,源 Flux 推过来的每条 request 都会触发完整的 agent 运行。
 *
 * 这给 Heartbeat / Cron / 外部 webhook 等"主动循环"留出统一接入口。
 */
@DisplayName("Acceptance: TriggerSource 驱动 Orchestrator 主动循环")
class TriggerSourceAcceptanceTest {

    private ToolRegistry globalRegistry;
    private List<String> toolInvocations;
    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        globalRegistry = new ToolRegistry();
        toolInvocations = new CopyOnWriteArrayList<>();
        globalRegistry.register(trackingTool("ping"));

        AgentRegistry agents = new AgentRegistry();
        agents.register(AgentProfile.builder().agentId("hb-agent").build());
        agents.setDefault("hb-agent");

        AgentFactory factory = new AgentFactory(llmCallsPing(), new NoopMemory(), globalRegistry);
        orchestrator = new Orchestrator(agents, factory, new MetadataAgentRouter(agents));
    }

    @AfterEach
    void tearDown() {
        orchestrator.shutdown();
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("订阅 TriggerSource → 源里每条 request 触发一次 agent 运行")
    void everyTriggerCausesOneAgentRun() {
        TriggerSource source = new TriggerSource() {
            @Override public String name() { return "test-source"; }
            @Override public Flux<GatewayRequest> requests() {
                return Flux.just(
                        GatewayRequest.builder().sessionId("hb-1").message("tick 1").build(),
                        GatewayRequest.builder().sessionId("hb-2").message("tick 2").build(),
                        GatewayRequest.builder().sessionId("hb-3").message("tick 3").build()
                );
            }
        };

        orchestrator.subscribeTriggerSource(source);

        // 驱动事件循环到完成 — 三次 ping 调用
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (toolInvocations.size() < 3) {
                Thread.sleep(20);
            }
        });

        assertEquals(3, toolInvocations.size(),
                "源推 3 条 request 应触发 3 次 agent 运行,实际: " + toolInvocations);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("unsubscribeTriggerSource 后,后续来自该源的 request 不再触发 agent")
    void unsubscribeStopsConsumingEvents() throws InterruptedException {
        // 用一个手动可控的源:先发 1 条,等订阅取消后再发不应被消费
        reactor.core.publisher.Sinks.Many<GatewayRequest> sink =
                reactor.core.publisher.Sinks.many().multicast().onBackpressureBuffer();

        TriggerSource source = new TriggerSource() {
            @Override public String name() { return "manual-source"; }
            @Override public Flux<GatewayRequest> requests() { return sink.asFlux(); }
        };

        orchestrator.subscribeTriggerSource(source);
        sink.tryEmitNext(GatewayRequest.builder().sessionId("m-1").message("first").build());

        // 等 first 被消费
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            while (toolInvocations.size() < 1) Thread.sleep(20);
        });
        int afterFirst = toolInvocations.size();

        orchestrator.unsubscribeTriggerSource("manual-source");
        // 取消后再发,应不被消费
        sink.tryEmitNext(GatewayRequest.builder().sessionId("m-2").message("second").build());

        // 给 200ms 观察时间窗
        Thread.sleep(200);

        assertEquals(afterFirst, toolInvocations.size(),
                "取消订阅后该源新事件不应触发 agent。实际: " + toolInvocations);
    }

    private LLMProvider llmCallsPing() {
        return new LLMProvider() {
            int call = 0;
            @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
                call++;
                if (call % 2 == 1) {
                    ToolCall tc = new ToolCall("tc_" + call, "ping", Map.of());
                    return Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent("ping").build())
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
            @Override public String getProviderName() { return "trigger-test"; }
        };
    }

    private Tool trackingTool(String name) {
        AtomicInteger seq = new AtomicInteger();
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                toolInvocations.add(name + "#" + seq.incrementAndGet());
                return ToolResult.success("ok");
            }
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
