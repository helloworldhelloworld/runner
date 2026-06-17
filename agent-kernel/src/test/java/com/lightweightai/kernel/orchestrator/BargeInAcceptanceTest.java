package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R4（barge-in 接线）outside-in acceptance test。
 *
 * 用户在小黄人正说话时插话 → Orchestrator 打断活跃 run，并发出 {@link EventType#SPEECH_INTERRUPTED}，
 * 让设备/Voice Gateway 立即停播已下发但未念完的音频、丢弃排队的可说块。
 *
 * 验证：打断时除 AGENT_INTERRUPT 外，还发出 SPEECH_INTERRUPTED，且它出现在 AGENT_RESUME 之前；
 * 该信号携带被打断 run 的 runId。无活跃 run 时（普通新会话）不应发 SPEECH_INTERRUPTED。
 */
@DisplayName("Acceptance: barge-in 停播信号")
class BargeInAcceptanceTest {

    private MemoryProvider memory;
    private AgentRegistry registry;
    private ToolRegistry toolRegistry;

    @BeforeEach
    void setUp() {
        memory = new InterruptResumeAcceptanceTest.RecordingMemory();
        registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("default").systemPrompt("你是小黄人").build());
        registry.setDefault("default");
        toolRegistry = new ToolRegistry();
    }

    /**
     * 第 1 轮发首个 delta 后挂住（{@link Flux#never()}，run 稳定停在 RUNNING 直到被打断）；
     * 第 2 轮（resume）秒回 "收到"。取代原 delayElements(100ms)+Thread.sleep(150) 时序（L-3 去 flake）：
     * 测试 await 首个 delta 的 latch（真信号）即知 run 在途，无需猜 sleep 时长。
     */
    private LLMProvider runningThenResume(AtomicInteger callCount) {
        return new LLMProvider() {
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> msgs, LLMOptions opts) {
                int call = callCount.incrementAndGet();
                if (call == 1) {
                    return Flux.<StreamEvent>just(StreamEvent.textDelta("你好"))
                            .concatWith(Flux.never());
                }
                return Flux.just(StreamEvent.textDelta("收到"),
                        StreamEvent.llmComplete(LLMResponse.builder()
                                .message(ConversationMessage.builder()
                                        .role(ConversationMessage.MessageRole.ASSISTANT)
                                        .textContent("收到").build())
                                .stopReason("end_turn").build()));
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
            @Override public String getProviderName() { return "running-then-resume"; }
        };
    }

    /** 单轮就完成（delta + llmComplete）——freshSession 无打断场景用，collectList().block() 能正常返回。 */
    private LLMProvider completingLlm() {
        return new LLMProvider() {
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> msgs, LLMOptions opts) {
                return Flux.just(StreamEvent.textDelta("你好"),
                        StreamEvent.llmComplete(LLMResponse.builder()
                                .message(ConversationMessage.builder()
                                        .role(ConversationMessage.MessageRole.ASSISTANT)
                                        .textContent("你好").build())
                                .stopReason("end_turn").build()));
            }
            @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                return LLMResponse.builder().stopReason("end_turn")
                        .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("你好").build()).build();
            }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "completing"; }
        };
    }

    @Test
    @DisplayName("说话中被插话 → 发出 SPEECH_INTERRUPTED，且在 AGENT_RESUME 之前")
    void bargeInEmitsSpeechInterrupted() throws InterruptedException {
        AtomicInteger callCount = new AtomicInteger(0);
        AgentFactory factory = new AgentFactory(runningThenResume(callCount), memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        CountDownLatch streaming = new CountDownLatch(1);
        orchestrator.chatStreamReactive(GatewayRequest.builder().sessionId("s1").message("讲个笑话").build())
                .doOnNext(e -> { if (e.getType() == EventType.TEXT_DELTA) streaming.countDown(); })
                .subscribe();
        assertTrue(streaming.await(3, TimeUnit.SECONDS), "应先开始流式输出（首个 delta 到=run 确定在 RUNNING）");
        // 不再 Thread.sleep：首个 delta 已到即证明 run 在途，无需猜时序（L-3）

        // 用户插话（barge-in）
        List<StreamEvent> evB = orchestrator.chatStreamReactive(
                GatewayRequest.builder().sessionId("s1").message("等等").build()).collectList().block();

        assertNotNull(evB);
        List<EventType> types = evB.stream().map(StreamEvent::getType).toList();
        assertTrue(types.contains(EventType.AGENT_INTERRUPT), "应打断: " + types);
        assertTrue(types.contains(EventType.SPEECH_INTERRUPTED), "barge-in 应发 SPEECH_INTERRUPTED: " + types);

        int speechIdx = types.indexOf(EventType.SPEECH_INTERRUPTED);
        int resumeIdx = types.indexOf(EventType.AGENT_RESUME);
        assertTrue(resumeIdx >= 0 && speechIdx < resumeIdx,
                "SPEECH_INTERRUPTED 必须在 AGENT_RESUME 之前(先停播再接续): " + types);

        StreamEvent speech = evB.get(speechIdx);
        assertNotNull(speech.getData().get("runId"), "SPEECH_INTERRUPTED 应带 runId");
    }

    @Test
    @DisplayName("普通新会话（无活跃 run）→ 不发 SPEECH_INTERRUPTED")
    void freshSessionDoesNotEmitSpeechInterrupted() {
        AgentFactory factory = new AgentFactory(completingLlm(), memory, toolRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        List<StreamEvent> ev = orchestrator.chatStreamReactive(
                GatewayRequest.builder().sessionId("fresh").message("你好").build()).collectList().block();

        assertNotNull(ev);
        assertFalse(ev.stream().anyMatch(e -> e.getType() == EventType.SPEECH_INTERRUPTED),
                "无活跃 run 时不应发停播信号");
    }
}
