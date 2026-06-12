package com.lightweightai.mcp.transport;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.RiskLevel;
import com.lightweightai.kernel.agent.ScopedToolRegistry;
import com.lightweightai.kernel.agent.ToolPolicy;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpConfiguration;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolWrapper;
import com.lightweightai.mcp.ToolClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 手动集成测试：runner 生产同款 MCP 客户端连<b>真实运行的 minion-body Pi</b>（非 fake peer）。
 *
 * <p>默认跳过（CI/普通 {@code mvn test} 不跑）；设环境变量 {@code MINION_PI_URL} 才执行，例如：
 * <pre>
 *   MINION_PI_URL=http://minion.local:8765/mcp mvn test -Dtest=RealPiConnectionIT -pl agent-mcp
 * </pre>
 *
 * <p>走的是与生产完全一致的链路：{@link ToolClient.Builder#createTransport}（streamable_http）→
 * 真 SDK {@link McpToolClient#initialize()} → {@link McpToolClient#discoverMcpTools()} → 风险经
 * MCP annotations 跨界恢复（ADR-007）→ minion persona 的 {@link ToolPolicy#byRiskLevel} 风险闸 →
 * 真实 {@code tools/call} 往返到 Pi。faithful fake 版见 {@link RunnerToMinionBodyMcpSmokeTest}。
 */
@DisplayName("Real Pi: runner MCP 客户端 ↔ 真 minion-body server")
class RealPiConnectionIT {

    @Test
    @DisplayName("连真 Pi → 发现设备工具 → 风险闸 → 调用 move")
    void connectDiscoverGateInvokeAgainstRealPi() {
        String url = System.getenv("MINION_PI_URL");
        assumeTrue(url != null && !url.isBlank(),
                "设 MINION_PI_URL=http://<pi>:8765/mcp 才跑这条真 Pi 集成测试");

        McpConfiguration.ServerConfig config = McpConfiguration.ServerConfig.streamableHttp(url);
        McpClientTransport transport = ToolClient.Builder.createTransport("minion", config, null);

        McpToolClient client = McpToolClient.builder()
                .serverName("minion")
                .transport(transport)
                .requestTimeout(Duration.ofSeconds(8))
                .build()
                .initialize();

        List<McpToolWrapper> tools = client.discoverMcpTools();
        System.out.println("[RealPi] 发现工具: " + tools.stream().map(McpToolWrapper::getName).toList());
        for (McpToolWrapper t : tools) {
            System.out.println("[RealPi]   " + t.getName() + " risk=" + t.riskLevel());
        }
        assertFalse(tools.isEmpty(), "应从真 Pi 发现设备工具");

        // 注册进 ToolRegistry，套 minion persona 的 byRiskLevel(SYSTEM) 风险闸
        ToolRegistry global = new ToolRegistry();
        client.registerTools(global);
        AgentProfile minion = AgentProfile.builder()
                .agentId("minion").systemPrompt("minion")
                .toolPolicy(ToolPolicy.byRiskLevel(RiskLevel.SYSTEM)).maxSpawnDepth(0).build();
        ScopedToolRegistry scope = new ScopedToolRegistry(global, minion);

        assertTrue(scope.isEnabled("look"), "SAFE look 放行");
        assertTrue(scope.isEnabled("move"), "SYSTEM move 在 byRiskLevel(SYSTEM) 下放行");
        assertEquals(RiskLevel.SYSTEM, global.get("move").orElseThrow().riskLevel(),
                "move 的 destructiveHint 经真 tools/list 恢复为 SYSTEM");

        // 真实调用一次 move：tools/call 往返回到真 Pi
        ToolResult result = scope.get("move").orElseThrow().execute(Map.of("gesture", "wave"));
        System.out.println("[RealPi] move(wave) → isError=" + result.isError()
                + " content=" + result.getContent());
        assertNotNull(result, "move 应返回结果");
        assertFalse(result.isError(), "真 Pi 上 move 不应报错");

        client.close();
    }
}
