package com.lightweightai.mcp;

import com.lightweightai.mcp.transport.WebSocketMcpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * McpToolClient sampling handler 接线测试（契约 3.7）。
 *
 * <p>遵循本仓库 TDD 规则：断言载荷而非仪式 —— 验证 sampling 能力被实际声明、
 * 注册的 handler 被实际调用且返回值原样回传，而不仅是"构建未抛异常"。
 */
@DisplayName("McpToolClient - sampling handler 接线（契约 3.7）")
class McpToolClientSamplingTest {

    private static WebSocketMcpClientTransport transport() {
        return WebSocketMcpClientTransport.builder("ws://localhost:1/mcp").build();
    }

    @Test
    @DisplayName("注册 samplingHandler 后，client 向 Server 声明 sampling 能力")
    void declaresSamplingCapabilityWhenHandlerPresent() {
        McpToolClient client = McpToolClient.builder()
            .serverName("ws-test")
            .transport(transport())
            .samplingHandler(req -> null)
            .build();

        McpSchema.ClientCapabilities caps = client.getAsyncClient().getClientCapabilities();
        assertNotNull(caps, "client capabilities must be declared");
        assertNotNull(caps.sampling(), "sampling capability must be declared when handler is present");
    }

    @Test
    @DisplayName("未注册 samplingHandler 时，不声明 sampling 能力（向后兼容）")
    void omitsSamplingCapabilityWhenNoHandler() {
        McpToolClient client = McpToolClient.builder()
            .serverName("ws-test")
            .transport(transport())
            .build();

        McpSchema.ClientCapabilities caps = client.getAsyncClient().getClientCapabilities();
        if (caps != null) {
            assertNull(caps.sampling(),
                "sampling capability must NOT be declared when no handler is registered");
        }
    }

    @Test
    @DisplayName("Server 发来的 createMessage 请求路由到注册的 handler，结果原样回传")
    void routesCreateMessageToHandlerAndReturnsResult() throws Exception {
        AtomicReference<McpSchema.CreateMessageRequest> captured = new AtomicReference<>();
        McpSchema.CreateMessageResult expected = McpSchema.CreateMessageResult.builder()
            .role(McpSchema.Role.ASSISTANT)
            .content(new McpSchema.TextContent("device tool output"))
            .model("device-tool")
            .stopReason(McpSchema.CreateMessageResult.StopReason.END_TURN)
            .build();

        Function<McpSchema.CreateMessageRequest, McpSchema.CreateMessageResult> handler = req -> {
            captured.set(req);
            return expected;
        };

        McpToolClient client = McpToolClient.builder()
            .serverName("ws-test")
            .transport(transport())
            .samplingHandler(handler)
            .build();

        // 取出 SDK 持有的 sampling handler（Mono 适配版），模拟 Server 发起的 createMessage。
        @SuppressWarnings("unchecked")
        Function<McpSchema.CreateMessageRequest, reactor.core.publisher.Mono<McpSchema.CreateMessageResult>>
            wired = extractSamplingHandler(client.getAsyncClient());

        McpSchema.CreateMessageRequest request = McpSchema.CreateMessageRequest.builder()
            .messages(List.of(new McpSchema.SamplingMessage(
                McpSchema.Role.USER,
                new McpSchema.TextContent("execute device tool: ExecCli"))))
            .maxTokens(1000)
            .build();

        McpSchema.CreateMessageResult actual = wired.apply(request).block();

        assertSame(request, captured.get(), "registered handler must receive the Server's request");
        assertSame(expected, actual, "handler's result must be returned unchanged to the Server");
    }

    @SuppressWarnings("unchecked")
    private static Function<McpSchema.CreateMessageRequest,
            reactor.core.publisher.Mono<McpSchema.CreateMessageResult>>
            extractSamplingHandler(Object asyncClient) throws Exception {
        java.lang.reflect.Field f = asyncClient.getClass().getDeclaredField("samplingHandler");
        f.setAccessible(true);
        return (Function<McpSchema.CreateMessageRequest,
            reactor.core.publisher.Mono<McpSchema.CreateMessageResult>>) f.get(asyncClient);
    }
}
