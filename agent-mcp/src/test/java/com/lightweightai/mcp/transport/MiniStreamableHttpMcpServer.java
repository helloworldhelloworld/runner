package com.lightweightai.mcp.transport;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 依赖无关的最小 <b>Streamable HTTP</b> MCP 测试服务端（MCP spec 2025-03-26 的 HTTP 传输）。
 *
 * <p>HTTP 平面对标 {@link MiniWebSocketServer} 之于 WebSocket：用 JDK 自带的
 * {@link com.sun.net.httpserver.HttpServer} 立一个<b>真实网络对端</b>，让真实 MCP SDK
 * （{@code HttpClientStreamableHttpTransport}）端到端驱动我们的 transport 创建路径
 * （{@code ToolClient.Builder.createTransport}）。NEVER mock SDK/transport 本身（CLAUDE.md 集成-seam 规则 2）。
 *
 * <p>协议契约（从 {@code HttpClientStreamableHttpTransport} 0.17.0 源码逐条对出，规则 3）：
 * <ul>
 *   <li>client 对 endpoint 发 {@code POST}，body 是 JSON-RPC，
 *       {@code Accept: application/json, text/event-stream}；</li>
 *   <li>带 {@code id} 的请求（initialize / tools/list / tools/call）→ 回 {@code 200}
 *       + {@code Content-Type: application/json} + JSON-RPC 响应（echo {@code id}）；</li>
 *   <li>通知（{@code notifications/*}，无 {@code id}）→ 回 {@code 202} 空体（transport 见空 content-type 即视为无响应）；</li>
 *   <li>standalone SSE 流的 {@code GET} → 回 {@code 405}（无独立流；transport 对 405 优雅降级）。</li>
 * </ul>
 *
 * <p>响应 JSON 形状直接沿用 {@link MiniWebSocketServer} 里已被同版本 SDK 验证可用的写法。
 */
class MiniStreamableHttpMcpServer implements AutoCloseable {

    private static final Pattern METHOD = Pattern.compile("\"method\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\"[^\"]*\"|\\d+)");
    private static final Pattern PROTO = Pattern.compile("\"protocolVersion\"\\s*:\\s*\"([^\"]*)\"");

    private final HttpServer server;
    /** tools/list 返回的工具条目（原始 JSON 对象字符串）。*/
    private final CopyOnWriteArrayList<String> toolEntries = new CopyOnWriteArrayList<>();
    private volatile String callResultText = "ok";

    MiniStreamableHttpMcpServer() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/mcp", this::handle);
        this.server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "mini-http-mcp");
            t.setDaemon(true);
            return t;
        }));
        this.server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    String url(String path) {
        return "http://127.0.0.1:" + port() + path;
    }

    /** 注册一个 tools/list 工具；{@code annotationsJson} 为 null 表示不带 annotations。*/
    MiniStreamableHttpMcpServer withTool(String name, String annotationsJson) {
        String ann = annotationsJson == null ? "" : ",\"annotations\":" + annotationsJson;
        toolEntries.add("{\"name\":\"" + name + "\",\"description\":\"d\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}" + ann + "}");
        return this;
    }

    /** 设置 tools/call 返回的文本内容。*/
    MiniStreamableHttpMcpServer callResultText(String text) {
        this.callResultText = text;
        return this;
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1); // standalone SSE 流不支持；SDK 对 405 降级
                return;
            }
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String method = group(METHOD, body);
            String id = group(ID, body);

            // 通知（无 id）或无法识别 → 202 空体
            if (method == null || id == null) {
                ex.sendResponseHeaders(202, -1);
                return;
            }

            String json;
            switch (method) {
                case "initialize" -> json = initializeResult(id, protoOrDefault(body));
                case "tools/list" -> json = toolsListResult(id);
                case "tools/call" -> json = callResult(id, callResultText);
                default -> {
                    ex.sendResponseHeaders(202, -1); // 其它带 id 的方法本测试不需要
                    return;
                }
            }
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        } catch (RuntimeException e) {
            try {
                ex.sendResponseHeaders(500, -1);
            } catch (IOException ignored) {
                // 测试服务端，吞掉
            }
        } finally {
            ex.close();
        }
    }

    private String toolsListResult(String id) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"tools\":["
                + String.join(",", toolEntries) + "]}}";
    }

    private static String initializeResult(String id, String proto) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{"
                + "\"protocolVersion\":\"" + proto + "\","
                + "\"capabilities\":{\"tools\":{\"listChanged\":true}},"
                + "\"serverInfo\":{\"name\":\"mini-http\",\"version\":\"1.0\"}}}";
    }

    private static String callResult(String id, String text) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{"
                + "\"content\":[{\"type\":\"text\",\"text\":\"" + text + "\"}],\"isError\":false}}";
    }

    private static String protoOrDefault(String body) {
        String p = group(PROTO, body);
        return p != null ? p : "2025-06-18";
    }

    private static String group(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
