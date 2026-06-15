package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in 验收：minion 的 {@code set_eyes} **工具调用**端到端落到设备。
 *
 * 守护"眼睛会动"这条链——不靠真 LLM、不靠人嘴试：
 *  1. minion(byRiskLevel SYSTEM) 路由后，{@code set_eyes}(WRITE) 确实进了 LLM 的 toolDefinitions；
 *  2. 当 LLM 回一个 {@code set_eyes(emotion=...)} 工具调用时，经
 *     Orchestrator→ScopedToolRegistry→ToolCallingLoop→执行，**设备收到该 emotion**。
 *
 * 真机"眼睛不动"曾发生在 LLM(qwen)压根没发这个 tool_call——那是模型行为，本测试把**接线**钉死：
 * 只要 LLM 发了，眼睛必动。
 */
@DisplayName("Acceptance: minion set_eyes 工具调用端到端到设备")
class MinionSetEyesToolCallAcceptanceTest {

    @Test
    @DisplayName("LLM 发 set_eyes(emotion=sad) → 设备收到 sad，且 set_eyes 在 LLM 工具表里")
    void setEyesToolCallReachesDevice() {
        AtomicReference<String> emotionSeen = new AtomicReference<>();
        ToolRegistry global = new ToolRegistry();
        global.register(setEyesTool(emotionSeen));

        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("minion").systemPrompt("你是小黄人")
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.SYSTEM))
                .build());
        registry.setDefault("minion");

        // 脚本化 spy：第一轮发 set_eyes(emotion=sad) 工具调用，第二轮 end_turn。
        CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                "set_eyes", Map.of("emotion", "sad"), "好啦我不开心啦～");
        AgentFactory factory = new AgentFactory(spy, new NoMemory(), global);
        Orchestrator orchestrator =
                new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        orchestrator.chatStreamReactive(GatewayRequest.builder()
                .sessionId("eyes-1").message("做个不开心的表情").build())
                .collectList().block();

        // (1) set_eyes 确实到达 LLM（payload-capture，第一轮的工具表）
        List<String> names = spy.optionsHistory().get(0).getToolDefinitions().stream()
                .map(d -> (String) d.get("name")).toList();
        assertTrue(names.contains("set_eyes"), "set_eyes 应进 LLM 工具表: " + names);

        // (2) 端到端断言：工具调用真的执行了，设备拿到了 emotion=sad（不是只走流程）
        assertEquals("sad", emotionSeen.get(),
                "LLM 发了 set_eyes(sad) → 设备应收到 sad（这条链断了眼睛就不动）");
    }

    /** 仿真 set_eyes 设备工具：记录被调用的 emotion；WRITE 风险（与真 MCP set_eyes 一致）。 */
    private static Tool setEyesTool(AtomicReference<String> emotionSeen) {
        return new Tool() {
            @Override public String getName() { return "set_eyes"; }
            @Override public String getDescription() { return "把眼睛设成某种情绪表情"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                emotionSeen.set((String) args.get("emotion"));
                return ToolResult.success("eyes:" + args.get("emotion"));
            }
            @Override public Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
                ToolResult r = execute(args);
                return Flux.just(ToolResultChunk.complete("set_eyes", r));
            }
            @Override public RiskLevel riskLevel() { return RiskLevel.WRITE; }
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
