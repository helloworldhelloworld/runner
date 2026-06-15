package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.prompt.Skill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in E2E:让 AgentLoop 真实跑一次,验证 SkillGeneratorObserver 把成功轨迹
 * 自动萃取并热注册到 PromptEngine,下次 build prompt 时新 Skill 已可见。
 */
@DisplayName("E2E: 自我改进 — 一次成功运行后新 Skill 进入注册表")
class SelfImprovingE2ETest {

    @Test
    @DisplayName("AgentLoop 跑完 → PromptEngine 收到自动生成的 Skill")
    void successfulRunRegistersSkillIntoEngine() {
        // 1. 工具注册
        ToolRegistry registry = new ToolRegistry();
        registry.register(weatherTool());

        // 2. PromptEngine — 用作 SkillGenerator 注册目标
        PromptEngine engine = PromptEngine.builder().build();
        AtomicReference<Skill> capturedSkill = new AtomicReference<>();

        SkillGeneratorObserver observer = new SkillGeneratorObserver(
                new HeuristicSkillExtractor(),
                skill -> {
                    capturedSkill.set(skill);
                    engine.registerSkill(skill);
                });

        // 3. AgentLoop:fake LLM 走一次"weather 调用 → 收尾"
        AgentLoop agent = AgentLoop.builder()
                .llmProvider(llmCallsWeather())
                .toolRegistry(registry)
                .memoryProvider(new NoopMemory())
                .addObserver(observer)
                .build();

        agent.runReactive("Tokyo 现在天气如何?", "sess-e2e")
                .collectList().block();

        // 4. 验证:Skill 萃取成功
        Skill skill = capturedSkill.get();
        assertNotNull(skill, "成功运行应产出 Skill");

        // 5. PromptEngine 已收到该 Skill
        assertTrue(engine.hasSkill(skill.getName()),
                "PromptEngine 应注册新 Skill: " + skill.getName());

        // 6. body 是 lazy 的,首次取应含工具痕迹
        String body = skill.getSystemPrompt();
        assertTrue(body.contains("weather"), "body 应含工具名: " + body);
    }

    private LLMProvider llmCallsWeather() {
        return new LLMProvider() {
            int call = 0;
            @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
                call++;
                if (call == 1) {
                    ToolCall tc = new ToolCall("tc-w", "weather", Map.of("city", "Tokyo"));
                    return Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent("checking").build())
                            .toolCalls(List.of(tc))
                            .stopReason("tool_use").build()));
                }
                return Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                        .message(ConversationMessage.builder()
                                .role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("Tokyo 25 度晴").build())
                        .stopReason("end_turn").build()));
            }
            @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                return LLMResponse.builder().stopReason("end_turn").message(
                        ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("done").build()).build();
            }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, LLMProvider.StreamEventHandler h) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "self-improve-test"; }
        };
    }

    private Tool weatherTool() {
        return new Tool() {
            @Override public String getName() { return "weather"; }
            @Override public String getDescription() { return "weather lookup"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("Sunny 25C");
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
