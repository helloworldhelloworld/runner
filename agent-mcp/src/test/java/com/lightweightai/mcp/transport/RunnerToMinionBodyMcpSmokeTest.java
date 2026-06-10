package com.lightweightai.mcp.transport;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.RiskLevel;
import com.lightweightai.kernel.agent.ScopedToolRegistry;
import com.lightweightai.kernel.agent.ToolPolicy;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpConfiguration;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.ToolClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔴-5 修复（runner↔minion-body 这一跨仓 MCP 接缝）：跨边界冒烟测试——只 fake 远端 Pi，其余全真。
 *
 * <p>此前三仓（runner / voice-gateway / minion-body）只靠各自仓内 fake 锁契约，runner 连 Pi 的
 * <b>整条运行时链路从未端到端跑过</b>。本测试按 CLAUDE.md 集成-seam 规则 2（"fake 远端 peer、其余保持真"）
 * 把 runner 侧的生产链路一次跑通，只把树莓派换成 {@link MiniStreamableHttpMcpServer}：
 *
 * <pre>
 * 配置(ServerConfig.streamableHttp)         ← application.yml 的 minion server 条目形状
 *   → ToolClient.Builder.createTransport     ← 生产 transport 创建路径（McpConfig 同款）
 *   → McpToolClient.initialize/registerTools ← 真 SDK 连接 + 工具发现 + 注册进 ToolRegistry
 *   → riskLevel() 经真 MCP annotations 恢复   ← ADR-007
 *   → minion persona 的 ScopedToolRegistry    ← byRiskLevel(SYSTEM) 风险闸（OrchestratorConfig 同款策略）
 *   → 经 scoped registry 真实调用一次工具      ← tools/call 往返回到 fake Pi
 * </pre>
 *
 * <p>覆盖 minion 设备工具契约（见 docs/modules/minion-body.md）：物理动作 {@code move}
 * （{@code destructiveHint}→SYSTEM）、{@code set_eyes}（有副作用非破坏→WRITE）、{@code look}
 * （{@code readOnlyHint}→SAFE）。这是 B4 真接真 Pi 之前，把"脑在云连 NAT 后的 Pi"整条 runner 侧
 * 接线提前打通的探针；真 Pi/overlay 的连接生命周期仍按 ADR-008 留待 B4。
 */
@DisplayName("Cross-repo smoke: runner → (fake) minion-body MCP 设备工具 + 风险闸 + 调用")
class RunnerToMinionBodyMcpSmokeTest {

    private static McpClientTransport minionTransport(String url) {
        // application.yml 里 minion server 条目会绑定成同样的 ServerConfig，再走同一 createTransport。
        McpConfiguration.ServerConfig config = McpConfiguration.ServerConfig.streamableHttp(url);
        return ToolClient.Builder.createTransport("minion", config, null);
    }

    /** minion persona 的工具策略：byRiskLevel(SYSTEM)，与 OrchestratorConfig.MINION_PERSONA 一致。*/
    private static AgentProfile minionProfile() {
        return AgentProfile.builder()
            .agentId("minion")
            .systemPrompt("minion")
            .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.SYSTEM))
            .maxSpawnDepth(0)
            .build();
    }

    @Test
    @DisplayName("发现 Pi 设备工具 → 风险跨 MCP 恢复 → minion 闸放行 → 真实调用 move 往返")
    void discoverGateAndInvokeMinionDeviceTools() throws Exception {
        try (MiniStreamableHttpMcpServer pi = new MiniStreamableHttpMcpServer()
                .withTool("look", "{\"readOnlyHint\":true}")                         // SAFE：抓帧
                .withTool("set_eyes", "{\"readOnlyHint\":false,\"destructiveHint\":false}") // WRITE：表情
                .withTool("move", "{\"destructiveHint\":true}")                      // SYSTEM：物理运动
                .callResultText("waved")) {

            // ── runner 侧生产链路：connect → discover → register（McpConfig.connectMcpServer 同款）──
            McpToolClient client = McpToolClient.builder()
                .serverName("minion")
                .transport(minionTransport(pi.url("/mcp")))
                .requestTimeout(Duration.ofSeconds(5))
                .build()
                .initialize();

            ToolRegistry global = new ToolRegistry();
            int registered = client.registerTools(global);
            assertEquals(3, registered, "三个 Pi 设备工具应注册进 ToolRegistry");

            // ── 风险经真 MCP annotations 跨界恢复（ADR-007）──
            assertEquals(RiskLevel.SAFE, global.get("look").orElseThrow().riskLevel());
            assertEquals(RiskLevel.WRITE, global.get("set_eyes").orElseThrow().riskLevel());
            assertEquals(RiskLevel.SYSTEM, global.get("move").orElseThrow().riskLevel());

            // ── minion persona 的风险闸：byRiskLevel(SYSTEM) 全放行 ──
            ScopedToolRegistry minionScope = new ScopedToolRegistry(global, minionProfile());
            assertTrue(minionScope.isEnabled("look"), "SAFE 的 look 放行");
            assertTrue(minionScope.isEnabled("set_eyes"), "WRITE 的 set_eyes 放行");
            assertTrue(minionScope.isEnabled("move"), "SYSTEM 的 move 在 byRiskLevel(SYSTEM) 上限下放行");

            // ── 闸真的会区分：更严的 persona(byRiskLevel SAFE) 应挡住 WRITE/SYSTEM 设备工具 ──
            AgentProfile safeOnly = AgentProfile.builder()
                .agentId("safe-only").systemPrompt("x")
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.SAFE)).maxSpawnDepth(0).build();
            ScopedToolRegistry safeScope = new ScopedToolRegistry(global, safeOnly);
            assertTrue(safeScope.isEnabled("look"), "SAFE 仍放行");
            assertFalse(safeScope.isEnabled("move"), "SYSTEM 的 move 被 SAFE 上限挡住（证明闸真在区分）");
            assertFalse(safeScope.isEnabled("set_eyes"), "WRITE 的 set_eyes 被 SAFE 上限挡住");

            // ── 经 minion scoped registry 真实调用 move：tools/call 往返回到 fake Pi ──
            ToolResult result = minionScope.get("move").orElseThrow().execute(Map.of());
            assertNotNull(result, "move 调用应返回结果");
            assertFalse(result.isError(), "成功调用不应是错误");
            assertTrue(result.getContent() != null && result.getContent().contains("waved"),
                "结果应来自 fake Pi 的 tools/call 响应，实际: " + result.getContent());

            client.close();
        }
    }
}
