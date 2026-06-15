package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.RiskLevel;
import com.lightweightai.kernel.agent.ScopedToolRegistry;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R5（agent-web 侧）outside-in acceptance test：
 * 验证 {@link OrchestratorConfig} 把 "minion" 作为一个 persona AgentProfile 装配进 AgentRegistry——
 * persona 系统提示就位，且 {@code ToolPolicy.byRiskLevel(SYSTEM)} 风险闸真的放行 minion 设备工具
 * （SYSTEM 风险的运动工具）。这是运行时 Pi MCP 设备工具接入后能被 LLM 看见的前提。
 *
 * <p>内核侧的风险闸接线由 {@code MinionToolWiringAcceptanceTest} 守护；本测试只守护 agent-web 的装配。
 */
@DisplayName("Acceptance: agent-web 装配 minion persona + 风险闸")
class MinionPersonaAssemblyTest {

    private AgentRegistry buildRegistry() {
        return new OrchestratorConfig().agentRegistry();
    }

    @Test
    @DisplayName("minion persona profile 已注册，systemPrompt 为 MINION_PERSONA，不可 spawn 子 agent")
    void minionPersonaRegistered() {
        AgentRegistry registry = buildRegistry();

        Optional<AgentProfile> minionOpt = registry.get("minion");
        assertTrue(minionOpt.isPresent(), "应注册 minion persona");

        AgentProfile minion = minionOpt.get();
        assertEquals(OrchestratorConfig.MINION_PERSONA, minion.getSystemPrompt(),
                "minion 的 systemPrompt 必须是 MINION_PERSONA persona（断言载荷，非仅存在）");
        assertEquals(0, minion.getMaxSpawnDepth(),
                "具身小黄人是单体，不 spawn 子 agent");
    }

    @Test
    @DisplayName("既有 default/worker persona 不受影响（无回归）")
    void existingAgentsIntact() {
        AgentRegistry registry = buildRegistry();
        assertTrue(registry.get("default").isPresent(), "default 仍在");
        assertTrue(registry.get("worker").isPresent(), "worker 仍在");
        assertEquals("default", registry.getDefault().getAgentId(), "默认仍是 default");
    }

    @Test
    @DisplayName("minion 的 ToolPolicy 按风险闸放行 SYSTEM 设备工具与 SAFE 工具")
    void minionPolicyGatesDeviceToolsByRisk() {
        AgentProfile minion = buildRegistry().get("minion").orElseThrow();

        ToolRegistry global = new ToolRegistry();
        global.register(safeTool("look"));     // SAFE：看一眼/抓帧
        global.register(systemTool("move"));    // SYSTEM：运动

        ScopedToolRegistry scoped = new ScopedToolRegistry(global, minion);

        assertTrue(scoped.isEnabled("look"), "SAFE 的 look 应放行");
        assertTrue(scoped.isEnabled("move"),
                "SYSTEM 的 move 在 minion 的 byRiskLevel(SYSTEM) 上限下应放行");
    }

    // ---- helpers（与 MinionToolWiringAcceptanceTest 同形）----

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
}
