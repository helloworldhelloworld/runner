package com.lightweightai.mcp.transport;

import com.lightweightai.mcp.McpToolClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 走真实 MCP SDK {@code initialize()} 往返的端到端诊断测试 —— 对接 {@link MiniWebSocketServer}。
 *
 * <p>目的：复现 / 排除 "Client failed to initialize by explicit API call" 的第二类成因。
 * connect/onOpen 竞态修复后，剩余怀疑点是 initialize 的「请求-响应」往返：客户端发出
 * initialize 请求 → 服务端经 WebSocket 回 initialize 响应 → transport 的 onText 反序列化后
 * 必须正确交回 SDK，SDK 才能解除 initialize().block() 的阻塞。
 *
 * <p>若本测试通过：我们的 transport + SDK 往返链路完好，线上失败应归因于服务端/URL/协议版本。
 * 若本测试卡住/抛错：第二个 bug 在我们代码侧，stack trace 即可定位。
 */
@DisplayName("WebSocket transport + SDK initialize 往返")
class WebSocketMcpInitializeRoundTripTest {

    @Test
    @DisplayName("McpToolClient.initialize() 经 WebSocket 完成握手不抛错")
    void initializeCompletesOverWebSocket() throws Exception {
        try (MiniWebSocketServer server = new MiniWebSocketServer()) {
            // 服务端：收到 initialize 请求就回一个合法的 initialize 结果（echo id + protocolVersion）
            server.autoAnswerInitialize();

            WebSocketMcpClientTransport transport = WebSocketMcpClientTransport.builder(server.url("/mcp"))
                .pingInterval(Duration.ZERO)
                .build();

            McpToolClient client = McpToolClient.builder()
                .serverName("mini")
                .transport(transport)
                .requestTimeout(Duration.ofSeconds(5))
                .build();

            // 修复前：SDK 在握手完成前 fire-and-forget connect 后立即 sendRequest(initialize)，
            // sendMessage 因 connected=false 直接报 "WebSocket not connected"，被包成
            // "Client failed to initialize by explicit API call"。
            // 修复后：sendMessage 会等 openFuture（onOpen）完成再发，往返成功。
            assertDoesNotThrow(client::initialize,
                "initialize() 应在 WebSocket 往返后正常完成，而非抛 'failed to initialize'");
            assertTrue(client.isConnected(), "初始化完成后 transport 应处于连接状态");

            client.close();
        }
    }
}
