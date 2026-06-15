package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 入站 barge-in（ADR-012）：{@link Orchestrator#interrupt(String)} 独立打断在途 run。
 *
 * 与 {@link BargeInAcceptanceTest}（新消息到达时隐式打断）不同，本测试验证**不需要新消息**的
 * 独立打断入口——供 Voice Gateway 在用户一开口（STT 未完成）时立即停播。
 *
 * <b>确定性</b>：用一个发首个 delta 后 {@link Flux#never()} 挂住的 LLM，使 run 稳定处于 RUNNING；
 * 测试 await 首个 delta 的 latch（真信号，非定时 sleep）后再打断——不踩 {@code Thread.sleep} 的 flake。
 */
@DisplayName("Acceptance: 入站 barge-in — Orchestrator.interrupt(sessionId) 独立打断")
class OrchestratorInterruptAcceptanceTest {

    private MemoryProvider memory;
    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        memory = new InterruptResumeAcceptanceTest.RecordingMemory();
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("default").systemPrompt("你是小黄人").build());
        registry.setDefault("default");
        AgentFactory factory = new AgentFactory(gatedLlm(), memory, new ToolRegistry());
        orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));
    }

    /** 发一个 delta 后挂住（永不完成）——run 稳定停在 RUNNING，直到被打断。 */
    private LLMProvider gatedLlm() {
        return new LLMProvider() {
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> msgs, LLMOptions opts) {
                return Flux.<StreamEvent>just(StreamEvent.textDelta("我正在说话"))
                        .concatWith(Flux.never());
            }
            @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                return LLMResponse.builder().stopReason("end_turn")
                        .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("sync").build()).build();
            }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "gated"; }
        };
    }

    @Test
    @DisplayName("在途说话中独立打断 → 返回 SPEECH_INTERRUPTED(reason=barge-in, 带 runId)")
    void interruptActiveRunReturnsSpeechInterrupted() throws InterruptedException {
        CountDownLatch speaking = new CountDownLatch(1);
        Disposable sub = orchestrator.chatStreamReactive(
                        GatewayRequest.builder().sessionId("s1").message("讲个笑话").build())
                .doOnNext(e -> { if (e.getType() == EventType.TEXT_DELTA) speaking.countDown(); })
                .subscribe();
        try {
            assertTrue(speaking.await(3, TimeUnit.SECONDS), "run 应已开始流式输出（进入 RUNNING）");

            StreamEvent interrupted = orchestrator.interrupt("s1");

            assertNotNull(interrupted, "在途 run 应被打断并返回停播信号");
            assertEquals(EventType.SPEECH_INTERRUPTED, interrupted.getType());
            assertEquals("barge-in", interrupted.getData().get("reason"));
            assertNotNull(interrupted.getData().get("runId"), "应带被打断 run 的 runId");
            assertFalse(String.valueOf(interrupted.getData().get("runId")).isEmpty());
        } finally {
            sub.dispose();
        }
    }

    @Test
    @DisplayName("无在途 run / 未知会话 / 重复打断 → 返回 null（安静）")
    void interruptWithoutActiveRunReturnsNull() throws InterruptedException {
        assertNull(orchestrator.interrupt("never-existed"), "未知会话应返回 null");
        assertNull(orchestrator.interrupt(null), "null sessionId 应返回 null");

        CountDownLatch speaking = new CountDownLatch(1);
        Disposable sub = orchestrator.chatStreamReactive(
                        GatewayRequest.builder().sessionId("s2").message("hi").build())
                .doOnNext(e -> { if (e.getType() == EventType.TEXT_DELTA) speaking.countDown(); })
                .subscribe();
        try {
            assertTrue(speaking.await(3, TimeUnit.SECONDS));
            assertNotNull(orchestrator.interrupt("s2"), "首次打断成立");
            assertNull(orchestrator.interrupt("s2"), "已打断的 run 再次打断应返回 null（不重复发停播）");
        } finally {
            sub.dispose();
        }
    }
}
