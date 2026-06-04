package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.StreamEvent.EventType;
import com.lightweightai.kernel.core.postprocess.EmotionClassifier;
import com.lightweightai.kernel.core.postprocess.SpeakableChunkProcessor;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M0 — 全仿真闭环 acceptance test（无硬件、无云）。
 *
 * 把 R1–R5 串成小黄人一次对话：
 *   假唤醒(TriggerSource→GatewayRequest) → 假转写(message)
 *     → LLM 第 1 轮：调 look(假摄像头返回固定"图") + move(假舵机，记日志)
 *     → 工具结果回灌 → LLM 第 2 轮：流式多句应答
 *     → SpeakableChunkProcessor 切出带情绪的可说块（Voice Gateway 侧会这么接）
 *
 * 验证契约（真 Pi body / Voice Gateway 待建，这里用 fakes 锁定接口）：
 *  - minion 路由 + SYSTEM 运动工具经风险闸放行并真正执行（舵机日志被写）
 *  - look 的"固定图"结果回流（摄像头→LLM 路径）
 *  - 多句应答被切成有序、带 emotion 的 SPEAKABLE_CHUNK，且都在 LLM_COMPLETE 之前
 */
@DisplayName("M0 Acceptance: 小黄人全仿真闭环")
class MinionLoopM0AcceptanceTest {

    /** 假舵机动作日志（FakeDevice 的 actuator 侧）。*/
    private final List<String> servoLog = new CopyOnWriteArrayList<>();
    private ToolRegistry global;

    private static final String FIXED_IMAGE = "[image:minion-sees-a-cat]";

    @BeforeEach
    void setUp() {
        servoLog.clear();
        global = new ToolRegistry();
        // look：假摄像头，按需抓帧返回固定"图"（SAFE）
        global.register(new Tool() {
            @Override public String getName() { return "look"; }
            @Override public String getDescription() { return "看一眼/抓帧"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> a) { return ToolResult.success(FIXED_IMAGE); }
        });
        // move：按设备分发到假舵机（SYSTEM）
        DispatchingTool move = new DispatchingTool("move");
        move.addBinding(new DeviceToolBinding("minion", VersionRange.all(), new Tool() {
            @Override public String getName() { return "move"; }
            @Override public String getDescription() { return "挥手/转头"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> a) {
                servoLog.add("wave:" + a.getOrDefault("gesture", "hand"));
                return ToolResult.success("waved");
            }
            @Override public RiskLevel riskLevel() { return RiskLevel.SYSTEM; }
        }));
        global.register(move);
    }

    @Test
    @DisplayName("唤醒→看→动→多句应答→带情绪可说块，全链路绿")
    void fullSimulatedLoop() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("minion").systemPrompt("你是小黄人")
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.SYSTEM))
                .maxToolIterations(5)
                .build());
        registry.setDefault("minion");

        AgentFactory factory = new AgentFactory(scriptedMinionLlm(), new NoMemory(), global);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        EmotionClassifier classifier = t -> t.contains("！") ? "excited" : "neutral";

        // 假唤醒后送入假转写文本；Voice Gateway 侧会 .transform(SpeakableChunkProcessor)
        List<StreamEvent> events = orchestrator
                .chatStreamReactive(GatewayRequest.builder()
                        .sessionId("minion-1").message("你看到什么了？").build())
                .transform(new SpeakableChunkProcessor(classifier))
                .collectList().block();

        assertNotNull(events);
        List<EventType> types = events.stream().map(StreamEvent::getType).toList();

        // 1) 路由到 minion
        assertTrue(types.contains(EventType.AGENT_ROUTE), "应路由: " + types);

        // 2) look 的固定图结果回流（摄像头→LLM）
        boolean sawImage = events.stream()
                .filter(e -> e.getType() == EventType.TOOL_RESULT && e.getChunk() != null)
                .anyMatch(e -> FIXED_IMAGE.equals(e.getChunk().getResult() != null
                        ? e.getChunk().getResult().getContent() : null));
        assertTrue(sawImage, "look 应返回固定图并回流: " + events);

        // 3) move 真正驱动了假舵机（SYSTEM 工具经风险闸放行 + 执行）
        assertEquals(List.of("wave:hand"), servoLog, "假舵机应被挥手一次");

        // 4) 多句应答 → 有序、带 emotion 的 SPEAKABLE_CHUNK
        List<StreamEvent> chunks = events.stream()
                .filter(e -> e.getType() == EventType.SPEAKABLE_CHUNK).toList();
        List<String> texts = chunks.stream().map(c -> (String) c.getData().get("text")).toList();
        assertEquals(List.of("我看到一只猫。", "我来挥挥手！"), texts, "应按句切块");
        assertEquals("neutral", chunks.get(0).getData().get("emotion"));
        assertEquals("excited", chunks.get(1).getData().get("emotion"));

        // 5) 所有可说块在 LLM_COMPLETE 之前（先说完才算这轮结束）
        int lastChunk = types.lastIndexOf(EventType.SPEAKABLE_CHUNK);
        int complete = types.lastIndexOf(EventType.LLM_COMPLETE);
        assertTrue(lastChunk >= 0 && complete >= 0 && lastChunk < complete,
                "SPEAKABLE_CHUNK 应在 LLM_COMPLETE 前: " + types);
    }

    /** 第 1 轮调 look+move；拿到工具结果后第 2 轮流式多句应答。*/
    private LLMProvider scriptedMinionLlm() {
        AtomicInteger round = new AtomicInteger(0);
        return new LLMProvider() {
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> msgs, LLMOptions opts) {
                int r = round.incrementAndGet();
                if (r == 1) {
                    ToolCall look = new ToolCall("c1", "look", Map.of());
                    ToolCall move = new ToolCall("c2", "move", Map.of("gesture", "hand"));
                    return Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent("让我看看并挥个手").build())
                            .toolCalls(List.of(look, move))
                            .stopReason("tool_use").build()));
                }
                return Flux.just("我看到", "一只猫。", "我来", "挥挥手！")
                        .map(StreamEvent::textDelta)
                        .concatWith(Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                                .message(ConversationMessage.builder()
                                        .role(ConversationMessage.MessageRole.ASSISTANT)
                                        .textContent("我看到一只猫。我来挥挥手！").build())
                                .stopReason("end_turn").build())));
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
            @Override public String getProviderName() { return "scripted-minion"; }
        };
    }

    private static class NoMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
