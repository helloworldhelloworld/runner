package com.lightweightai.mcp.transport;

import com.lightweightai.mcp.McpToolClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 让<b>真实 MCP SDK</b> 驱动 {@code initialize()}，验证「WebSocket 打不开」时快速失败并带真实原因，
 * 而不是挂到 SDK 的 initialize 超时（约 20s）后报 "Client failed to initialize by explicit API call"。
 *
 * <p>复现 issue #195（ws per-query upstream health）：日志停在 "connecting" 无后续，20s 后
 * initialize 失败、真实原因被吞。这类 bug 只在<b>让框架驱动</b>（connect 被 SDK fire-and-forget
 * 订阅 + sendMessage 等 openFuture）时现形；直接 {@code transport.connect().block()} 的用例看不到
 * ——因为直接 block 能看到 connect 自身的错误，而 SDK 路径把它丢弃、只等 openFuture（见 CLAUDE.md
 * Integration-seam 规则 1/5）。
 */
@DisplayName("WebSocket 打不开时 SDK initialize 快速失败带真实原因（issue #195）")
class WebSocketMcpInitializeFailFastTest {

    /** 完成 TCP，但用 HTTP 400 拒绝 WebSocket 升级 → buildAsync 在任何 listener 回调之前就异常。 */
    private static final class UpgradeRejectingServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread acceptThread;

        UpgradeRejectingServer() throws IOException {
            this.serverSocket = new ServerSocket(0);
            this.acceptThread = new Thread(this::run, "ws-upgrade-reject");
            this.acceptThread.setDaemon(true);
            this.acceptThread.start();
        }

        String url(String path) {
            return "ws://localhost:" + serverSocket.getLocalPort() + path;
        }

        private void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try (Socket socket = serverSocket.accept()) {
                    InputStream in = socket.getInputStream();
                    StringBuilder sb = new StringBuilder();
                    int c;
                    while ((c = in.read()) != -1) {
                        sb.append((char) c);
                        if (sb.length() >= 4 && sb.substring(sb.length() - 4).equals("\r\n\r\n")) {
                            break; // 读完请求头即可
                        }
                    }
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 400 Bad Request\r\n"
                        + "Connection: close\r\n"
                        + "Content-Length: 0\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                } catch (IOException e) {
                    return; // serverSocket 关闭
                }
            }
        }

        @Override
        public void close() {
            acceptThread.interrupt();
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    @DisplayName("升级被拒 → initialize 快速失败（远早于 20s init 超时），真实原因不被吞")
    void initializeFailsFastWhenWebSocketNeverOpens() throws Exception {
        try (UpgradeRejectingServer server = new UpgradeRejectingServer()) {
            WebSocketMcpClientTransport transport =
                WebSocketMcpClientTransport.builder(server.url("/mcp"))
                    .connectTimeout(Duration.ofSeconds(30))
                    .pingInterval(Duration.ZERO)
                    .build();

            McpToolClient client = McpToolClient.builder()
                .serverName("ws-health-mcp-upstream")
                .transport(transport)
                .requestTimeout(Duration.ofSeconds(5))
                .build();

            long start = System.currentTimeMillis();
            Throwable ex = assertThrows(Throwable.class, client::initialize,
                "WebSocket 打不开时 initialize 应抛异常");
            long elapsed = System.currentTimeMillis() - start;

            assertTrue(elapsed < 3000,
                "initialize 应在握手失败时快速失败（握手一被拒即返回），而非挂到 requestTimeout/"
                    + "SDK init 超时，实际耗时 " + elapsed + "ms");

            boolean hasRealCause = false;
            for (Throwable t = ex; t != null; t = t.getCause()) {
                if (t instanceof IOException) {
                    hasRealCause = true;
                    break;
                }
            }
            assertTrue(hasRealCause,
                "cause 链应包含真实握手错误 (IOException/WebSocketHandshakeException)，而非被吞成超时，实际: " + ex);

            client.close();
        }
    }
}
