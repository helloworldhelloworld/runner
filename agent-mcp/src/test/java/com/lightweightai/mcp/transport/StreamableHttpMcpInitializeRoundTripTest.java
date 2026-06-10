package com.lightweightai.mcp.transport;

import com.lightweightai.kernel.agent.RiskLevel;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.mcp.McpConfiguration;
import com.lightweightai.mcp.McpToolClient;
import com.lightweightai.mcp.McpToolWrapper;
import com.lightweightai.mcp.ToolClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 🔴-4 修复：{@code streamable_http} transport 的真实 SDK round-trip 集成测试
 * （对标 WebSocket 的 {@link WebSocketMcpInitializeRoundTripTest}，补 ADR-008 生产传输的端到端验证）。
 *
 * <p>ADR-008 D1 定 cloud↔Pi 生产链路走 {@code streamable_http}，但此前 agent-mcp 测试里只有
 * WebSocket 有真 SDK initialize round-trip；{@code streamable_http} 仅有配置解析断言，从未被真 SDK
 * 跑过 initialize/tools/list/tools/call，也无失败时序覆盖 —— 直接违反 CLAUDE.md 集成-seam 规则 1/5。
 *
 * <p>本测试按集成-seam 规则：
 * <ul>
 *   <li>规则 1「让真框架驱动」：经 <b>生产路径</b> {@link ToolClient.Builder#createTransport} 建 transport，
 *       由真实 {@code HttpClientStreamableHttpTransport} + 真 {@code McpAsyncClient} 驱动；</li>
 *   <li>规则 2「fake 远端 peer、其余保持真」：只 fake 网络对端 {@link MiniStreamableHttpMcpServer}，
 *       SDK/transport/JSON-RPC 全真；</li>
 *   <li>规则 5「round-trip + 失败时序」：initialize→tools/list→tools/call 完整往返 +
 *       connect 失败快失败 + 断连后调用快失败不挂起。</li>
 * </ul>
 *
 * <p>顺带覆盖 🟠-3：风险跨 MCP 不只测「注解→风险」纯映射，而是断言 {@code destructiveHint}/{@code readOnlyHint}
 * 经<b>真 tools/list</b> 上线后被 {@link McpToolWrapper#riskLevel()} 正确恢复。
 */
@DisplayName("Streamable HTTP transport + SDK round-trip (ADR-008 生产传输)")
class StreamableHttpMcpInitializeRoundTripTest {

    private static McpClientTransport httpTransport(String url) {
        McpConfiguration.ServerConfig config = McpConfiguration.ServerConfig.streamableHttp(url);
        // 经生产工厂建 transport（含三层 header 合并的 customizeRequest），headerProvider 传 null。
        return ToolClient.Builder.createTransport("minion", config, null);
    }

    @Test
    @DisplayName("initialize() 经 streamable_http 完成握手不抛错")
    void initializeCompletesOverHttp() throws Exception {
        try (MiniStreamableHttpMcpServer server = new MiniStreamableHttpMcpServer()) {
            McpToolClient client = McpToolClient.builder()
                .serverName("minion")
                .transport(httpTransport(server.url("/mcp")))
                .requestTimeout(Duration.ofSeconds(5))
                .build();

            assertDoesNotThrow(client::initialize,
                "initialize() 应在 HTTP round-trip 后正常完成");
            assertTrue(client.isConnected(), "初始化完成后 transport 应处于连接状态");

            client.close();
        }
    }

    @Test
    @DisplayName("initialize → tools/list → tools/call 完整往返；风险经真 tools/list 恢复")
    void fullRoundTripListAndCall() throws Exception {
        try (MiniStreamableHttpMcpServer server = new MiniStreamableHttpMcpServer()
                .withTool("look", "{\"readOnlyHint\":true}")     // SAFE
                .withTool("move", "{\"destructiveHint\":true}")  // SYSTEM
                .callResultText("waved")) {

            McpToolClient client = McpToolClient.builder()
                .serverName("minion")
                .transport(httpTransport(server.url("/mcp")))
                .requestTimeout(Duration.ofSeconds(5))
                .build()
                .initialize();

            // tools/list 回程：带 id 关联的响应被 SDK 正确解析为工具列表
            List<McpToolWrapper> tools = client.discoverMcpTools();
            assertEquals(List.of("look", "move"),
                tools.stream().map(McpToolWrapper::getName).toList(),
                "工具名应来自服务端 tools/list 响应（证明 HTTP 回程正确关联）");

            // 🟠-3：风险经真 MCP annotations 跨界恢复（非纯映射单测）
            McpToolWrapper look = byName(tools, "look");
            McpToolWrapper move = byName(tools, "move");
            assertEquals(RiskLevel.SAFE, look.riskLevel(),
                "readOnlyHint=true 的 look 经真 tools/list 恢复为 SAFE");
            assertEquals(RiskLevel.SYSTEM, move.riskLevel(),
                "destructiveHint=true 的 move 经真 tools/list 恢复为 SYSTEM");

            // tools/call 往返：调用 move → 服务端回 CallToolResult → execute() 解析回结果
            ToolResult result = move.execute(Map.of());
            assertNotNull(result, "tools/call 应返回结果");
            assertFalse(result.isError(), "成功调用不应是错误结果");
            assertTrue(result.getContent() != null && result.getContent().contains("waved"),
                "结果内容应来自服务端 tools/call 响应，实际: " + (result.getContent()));

            client.close();
        }
    }

    @Test
    @DisplayName("connect 失败快失败：连接被拒抛错而非挂起")
    void connectFailsFast() throws Exception {
        int deadPort;
        try (ServerSocket s = new ServerSocket(0)) {
            deadPort = s.getLocalPort(); // 拿一个端口随即释放 → 无人监听
        }
        McpToolClient client = McpToolClient.builder()
            .serverName("dead")
            .transport(httpTransport("http://127.0.0.1:" + deadPort + "/mcp"))
            .requestTimeout(Duration.ofSeconds(3))
            .build();

        // 核心断言：在限定时间内返回（快失败），不挂起；且确实抛错。
        assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
            assertThrows(Exception.class, client::initialize,
                "连接被拒应快失败抛错，而非挂起到超时"));

        client.close();
    }

    @Test
    @DisplayName("断连后调用快失败：server 关停后 tools/call 不挂起")
    void postDisconnectFailsFast() throws Exception {
        MiniStreamableHttpMcpServer server = new MiniStreamableHttpMcpServer()
            .withTool("look", "{\"readOnlyHint\":true}");

        McpToolClient client = McpToolClient.builder()
            .serverName("minion")
            .transport(httpTransport(server.url("/mcp")))
            .requestTimeout(Duration.ofSeconds(3))
            .build()
            .initialize();

        McpToolWrapper look = byName(client.discoverMcpTools(), "look");
        server.close(); // 模拟 Pi 掉线 / 重启

        // 核心断言：限定时间内完成（快失败，不挂起）。错误以抛异常或 error 结果呈现皆可。
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            try {
                ToolResult r = look.execute(Map.of());
                assertTrue(r == null || r.isError(),
                    "server 关停后调用应快失败为错误结果，而非挂起");
            } catch (RuntimeException expected) {
                // 抛异常同样是"快失败"的合法形态
            }
        });

        client.close();
    }

    private static McpToolWrapper byName(List<McpToolWrapper> tools, String name) {
        return tools.stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }
}
