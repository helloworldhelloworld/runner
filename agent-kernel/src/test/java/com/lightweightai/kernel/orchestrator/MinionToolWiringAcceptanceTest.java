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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R5（minion Profile + 工具接入）outside-in acceptance test。
 *
 * 验证 Minion 的"设备分发工具 + 风险分级权限"组合接线：
 * - 运动工具以 {@link DispatchingTool}(deviceType="minion") 形态注册，底层是 SYSTEM 风险实现；
 * - minion AgentProfile 用 {@code ToolPolicy.byRiskLevel(SYSTEM)} 放行运动，看/读(SAFE)也放行；
 * - 收紧到 {@code byRiskLevel(WRITE)} 时，SYSTEM 的运动工具被拒（DispatchingTool 必须按其
 *   最高风险变体上报 riskLevel，否则风险闸被旁路 —— 本测试守护这条接线）；
 * - 经 Orchestrator 路由到 minion 后，放行的工具定义确实传到 LLM。
 */
@DisplayName("Acceptance: Minion 设备工具 + 风险闸接线")
class MinionToolWiringAcceptanceTest {

    private ToolRegistry global;

    @BeforeEach
    void setUp() {
        global = new ToolRegistry();
        global.register(safeTool("look"));                 // SAFE：看一眼/抓帧
        DispatchingTool move = new DispatchingTool("move"); // 运动：按设备分发
        move.addBinding(new DeviceToolBinding("minion", VersionRange.all(), systemTool("move")));
        global.register(move);
    }

    @Test
    @DisplayName("minion(byRiskLevel SYSTEM) → look 与 move 都放行")
    void minionAllowsMotionAtSystemRisk() {
        AgentProfile minion = AgentProfile.builder()
                .agentId("minion").systemPrompt("你是小黄人")
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.SYSTEM))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(global, minion);

        assertTrue(scoped.isEnabled("look"), "SAFE 的 look 应放行");
        assertTrue(scoped.isEnabled("move"), "SYSTEM 的 move 在 SYSTEM 上限下应放行");
    }

    @Test
    @DisplayName("收紧到 byRiskLevel(WRITE) → SYSTEM 的 move 被拒，look 仍放行")
    void tighteningToWriteDeniesMotion() {
        AgentProfile restricted = AgentProfile.builder()
                .agentId("minion-safe").systemPrompt("受限小黄人")
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.WRITE))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(global, restricted);

        assertTrue(scoped.isEnabled("look"), "SAFE 的 look 仍应放行");
        assertFalse(scoped.isEnabled("move"),
                "SYSTEM 的 move 在 WRITE 上限下应被拒 —— DispatchingTool 必须按最高风险变体上报 riskLevel");
    }

    @Test
    @DisplayName("DispatchingTool 按其绑定的最高风险变体上报 riskLevel")
    void dispatchingToolReportsMaxRisk() {
        DispatchingTool move = new DispatchingTool("move");
        move.addBinding(new DeviceToolBinding("minion", VersionRange.all(), systemTool("move")));
        assertEquals(RiskLevel.SYSTEM, move.riskLevel());
    }

    @Test
    @DisplayName("路由到 minion 后，放行的工具定义传给 LLM")
    void minionToolsReachLlm() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("minion").systemPrompt("你是小黄人")
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.SYSTEM))
                .build());
        registry.setDefault("minion");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        AgentFactory factory = new AgentFactory(spy, new NoMemory(), global);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        orchestrator.chatStreamReactive(
                GatewayRequest.builder().sessionId("m1").message("挥个手").build()).collectList().block();

        LLMOptions opts = spy.lastOptions();
        assertNotNull(opts);
        List<String> names = opts.getToolDefinitions().stream()
                .map(d -> (String) d.get("name")).toList();
        assertTrue(names.contains("look"), "look 应到达 LLM: " + names);
        assertTrue(names.contains("move"), "move 应到达 LLM: " + names);
    }

    // ---- helpers ----

    private static Tool safeTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
        };
    }

    private static Tool systemTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("moved"); }
            @Override public Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
                return Flux.just(ToolResultChunk.complete(name, ToolResult.success("moved")));
            }
            @Override public RiskLevel riskLevel() { return RiskLevel.SYSTEM; }
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
