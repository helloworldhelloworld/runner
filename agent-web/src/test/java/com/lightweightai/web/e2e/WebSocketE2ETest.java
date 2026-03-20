package com.lightweightai.web.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end WebSocket integration test using raw sockets (bypasses SOCKS proxy).
 *
 * Run with: mvn test -Dtest=WebSocketE2ETest -De2e=true
 */
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("WebSocket E2E Integration Tests")
class WebSocketE2ETest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger REQ_COUNTER = new AtomicInteger(0);

    private static Socket rawSocket;
    private static InputStream in;
    private static OutputStream out;
    private static String testUserId;

    @BeforeAll
    static void setup() throws Exception {
        if (!"true".equals(System.getProperty("e2e"))) return;

        // Direct socket connection (bypasses any system SOCKS proxy)
        rawSocket = new Socket();
        rawSocket.connect(new InetSocketAddress("127.0.0.1", 8081), 5000);
        rawSocket.setSoTimeout(10000);
        in = rawSocket.getInputStream();
        out = rawSocket.getOutputStream();

        // WebSocket handshake
        String key = Base64.getEncoder().encodeToString("e2e-test-key-1234".getBytes());
        String handshake = "GET /ws/chat HTTP/1.1\r\n" +
            "Host: 127.0.0.1:8081\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: " + key + "\r\n" +
            "Sec-WebSocket-Version: 13\r\n\r\n";
        out.write(handshake.getBytes(StandardCharsets.UTF_8));
        out.flush();

        // Read HTTP response line-by-line from raw stream (no BufferedReader to avoid stealing bytes)
        String statusLine = readLine(in);
        assertNotNull(statusLine, "No HTTP response");
        assertTrue(statusLine.contains("101"), "Expected 101 Switching Protocols, got: " + statusLine);
        // Read remaining headers until empty line
        while (true) {
            String line = readLine(in);
            if (line == null || line.isEmpty()) break;
        }
        System.out.println("[WS] Connected (raw socket, bypassed proxy)");
    }

    @AfterAll
    static void teardown() throws Exception {
        if (rawSocket != null && !rawSocket.isClosed()) {
            // Send close frame
            sendWsFrame(out, (byte) 0x88, new byte[]{0x03, (byte) 0xE8}); // close code 1000
            rawSocket.close();
        }
    }

    static boolean isE2E() {
        return "true".equals(System.getProperty("e2e"));
    }

    // ==================== HTTP line reading (byte-at-a-time, no buffering) ====================

    static String readLine(InputStream stream) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = stream.read();
            if (b == -1) return buf.size() > 0 ? buf.toString(StandardCharsets.UTF_8) : null;
            if (b == '\n') {
                // strip trailing \r if present
                String s = buf.toString(StandardCharsets.UTF_8);
                if (s.endsWith("\r")) s = s.substring(0, s.length() - 1);
                return s;
            }
            buf.write(b);
        }
    }

    // ==================== Raw WebSocket Frame I/O ====================

    static void sendWsFrame(OutputStream out, byte opcode, byte[] payload) throws IOException {
        out.write(0x80 | opcode); // FIN + opcode
        int len = payload.length;
        byte[] mask = {0x12, 0x34, 0x56, 0x78};

        if (len < 126) {
            out.write(0x80 | len); // masked + length
        } else if (len < 65536) {
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) out.write((len >> (8 * i)) & 0xFF);
        }
        out.write(mask);
        for (int i = 0; i < payload.length; i++) {
            out.write(payload[i] ^ mask[i % 4]);
        }
        out.flush();
    }

    static void sendText(String text) throws IOException {
        sendWsFrame(out, (byte) 0x01, text.getBytes(StandardCharsets.UTF_8));
    }

    static String readWsTextFrame() throws IOException {
        int b1 = in.read();
        if (b1 == -1) throw new IOException("Connection closed");
        // boolean fin = (b1 & 0x80) != 0;
        int opcode = b1 & 0x0F;
        if (opcode == 0x08) throw new IOException("Close frame received");
        if (opcode == 0x09) { // ping
            sendWsFrame(out, (byte) 0x0A, new byte[0]); // pong
            return readWsTextFrame(); // read next
        }

        int b2 = in.read();
        boolean masked = (b2 & 0x80) != 0;
        long len = b2 & 0x7F;
        if (len == 126) {
            len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | (in.read() & 0xFF);
        }

        byte[] maskKey = null;
        if (masked) {
            maskKey = new byte[4];
            in.readNBytes(maskKey, 0, 4);
        }

        byte[] payload = in.readNBytes((int) len);
        if (masked) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= maskKey[i % 4];
        }

        return new String(payload, StandardCharsets.UTF_8);
    }

    // ==================== Helpers ====================

    private String nextRequestId() {
        return "req-" + REQ_COUNTER.incrementAndGet();
    }

    private JsonNode sendRequest(String type, Map<String, Object> data) throws Exception {
        String rid = nextRequestId();
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("type", type);
        payload.put("requestId", rid);
        if (data != null) {
            data.forEach((k, v) -> {
                if (v instanceof String s) payload.put(k, s);
                else if (v instanceof Integer i) payload.put(k, i);
                else if (v instanceof Boolean b) payload.put(k, b);
                else if (v instanceof List) payload.set(k, MAPPER.valueToTree(v));
                else payload.put(k, String.valueOf(v));
            });
        }
        sendText(MAPPER.writeValueAsString(payload));

        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            try {
                String raw = readWsTextFrame();
                JsonNode msg = MAPPER.readTree(raw);
                if (rid.equals(msg.path("requestId").asText())) {
                    return msg;
                }
            } catch (java.net.SocketTimeoutException e) {
                break;
            }
        }
        fail("Timeout waiting for response to " + type);
        return null;
    }

    private List<JsonNode> sendChatAndCollect(String message, String sessionId) throws Exception {
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put("type", "chat");
        payload.put("sessionId", sessionId);
        payload.put("message", message);
        payload.put("reactive", true);
        sendText(MAPPER.writeValueAsString(payload));

        List<JsonNode> events = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            try {
                String raw = readWsTextFrame();
                JsonNode msg = MAPPER.readTree(raw);
                events.add(msg);
                String t = msg.path("type").asText();
                if ("stream_end".equals(t) || "error".equals(t) || "crisis_alert".equals(t)) break;
            } catch (java.net.SocketTimeoutException e) {
                break;
            }
        }
        return events;
    }

    // ==================== Tests ====================

    @Test @Order(1) @DisplayName("1. Health Check")
    void healthCheck() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("health", null);
        assertEquals("UP", resp.path("data").path("status").asText());
        assertTrue(resp.path("data").path("wsConnections").asInt() > 0);
        System.out.println("  ✅ Health: UP, wsConnections=" + resp.path("data").path("wsConnections"));
    }

    @Test @Order(2) @DisplayName("2. Get Skills")
    void getSkills() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("get_skills", null);
        assertTrue(resp.path("data").size() > 0);
        System.out.println("  ✅ Skills: " + resp.path("data").size());
    }

    @Test @Order(3) @DisplayName("3. Get Tools")
    void getTools() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("get_tools", null);
        assertTrue(resp.path("data").size() > 0);
        System.out.println("  ✅ Tools: " + resp.path("data").size());
    }

    @Test @Order(4) @DisplayName("4. Get Tools Detail")
    void getToolsDetail() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("get_tools_detail", null);
        assertTrue(resp.path("data").path("total").asInt() > 0);
        assertTrue(resp.path("data").path("tools").get(0).has("source"));
        System.out.println("  ✅ Tools Detail: total=" + resp.path("data").path("total"));
    }

    @Test @Order(5) @DisplayName("5. Get Scales")
    void getScales() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("get_scales", null);
        assertTrue(resp.path("data").size() > 0);
        System.out.println("  ✅ Scales: " + resp.path("data").size());
    }

    @Test @Order(6) @DisplayName("6. Create User")
    void createUser() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("create_user", null);
        testUserId = resp.path("data").path("userId").asText(resp.path("data").path("id").asText(""));
        assertFalse(testUserId.isEmpty());
        System.out.println("  ✅ User: " + testUserId);
    }

    @Test @Order(7) @DisplayName("7. Emotion Check-in")
    void emotionCheckin() throws Exception {
        Assumptions.assumeTrue(isE2E());
        if (testUserId == null || testUserId.isEmpty()) createUser();
        JsonNode resp = sendRequest("checkin", Map.of("userId", testUserId, "emotion", "开心", "note", "e2e测试"));
        assertEquals("checkin_result", resp.path("type").asText());
        System.out.println("  ✅ Check-in done");
    }

    @Test @Order(8) @DisplayName("8. Get Emotions")
    void getEmotions() throws Exception {
        Assumptions.assumeTrue(isE2E());
        if (testUserId == null || testUserId.isEmpty()) { createUser(); emotionCheckin(); }
        JsonNode resp = sendRequest("get_emotions", Map.of("userId", testUserId, "days", 7));
        assertTrue(resp.path("data").size() > 0);
        System.out.println("  ✅ Emotions: " + resp.path("data").size());
    }

    @Test @Order(9) @DisplayName("9. Get Stats")
    void getStats() throws Exception {
        Assumptions.assumeTrue(isE2E());
        if (testUserId == null || testUserId.isEmpty()) createUser();
        JsonNode resp = sendRequest("get_stats", Map.of("userId", testUserId));
        assertEquals("stats", resp.path("type").asText());
        System.out.println("  ✅ Stats received");
    }

    @Test @Order(10) @DisplayName("10. Chat Streaming")
    void chatStreaming() throws Exception {
        Assumptions.assumeTrue(isE2E());
        List<JsonNode> events = sendChatAndCollect("你好", "e2e-test");
        List<String> types = events.stream().map(e -> e.path("type").asText()).toList();
        assertTrue(types.contains("token"), "Should have token events, got: " + types);
        assertTrue(types.contains("stream_end"), "Should have stream_end");
        String text = events.stream()
            .filter(e -> "token".equals(e.path("type").asText()))
            .map(e -> e.path("data").asText()).reduce("", String::concat);
        System.out.println("  ✅ Chat: " + text.substring(0, Math.min(80, text.length())));
    }

    @Test @Order(11) @DisplayName("11. Session History")
    void sessionHistory() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("get_history", Map.of("sessionId", "e2e-test"));
        assertTrue(resp.path("data").size() > 0);
        System.out.println("  ✅ History: " + resp.path("data").size() + " entries");
    }

    @Test @Order(12) @DisplayName("12. Session Summary")
    void sessionSummary() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("get_summary", Map.of("sessionId", "e2e-test"));
        assertNotNull(resp.path("data"));
        System.out.println("  ✅ Summary received");
    }

    @Test @Order(13) @DisplayName("13. Crisis Detection")
    void crisisDetection() throws Exception {
        Assumptions.assumeTrue(isE2E());
        List<JsonNode> events = sendChatAndCollect("我不想活了", "e2e-crisis");
        List<String> types = events.stream().map(e -> e.path("type").asText()).toList();
        assertTrue(types.contains("crisis_alert"), "Should trigger crisis, got: " + types);
        System.out.println("  ✅ Crisis alert triggered");
    }

    @Test @Order(14) @DisplayName("14. Memory Search")
    void memorySearch() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("search_memory", Map.of("query", "心情", "topK", 5));
        assertTrue(resp.path("data").isArray());
        System.out.println("  ✅ Memory results: " + resp.path("data").size());
    }

    @Test @Order(15) @DisplayName("15. Remember Durable")
    void rememberDurable() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("remember_durable", Map.of("section", "e2e测试", "content", "端到端测试记忆"));
        assertEquals("saved", resp.path("data").path("status").asText());
        System.out.println("  ✅ Saved to durable memory");
    }

    @Test @Order(16) @DisplayName("16. Clear Session")
    void clearSession() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("clear_session", Map.of("sessionId", "e2e-test"));
        assertEquals("cleared", resp.path("data").path("status").asText());
        System.out.println("  ✅ Session cleared");
    }

    @Test @Order(17) @DisplayName("17. MCP Servers")
    void mcpServers() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("get_mcp_servers", null);
        assertTrue(resp.path("data").has("enabled"));
        System.out.println("  ✅ MCP enabled: " + resp.path("data").path("enabled"));
    }

    @Test @Order(18) @DisplayName("18. Tool Definitions")
    void toolDefinitions() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("get_tool_definitions", null);
        assertTrue(resp.path("data").path("count").asInt() > 0);
        System.out.println("  ✅ Tool definitions: " + resp.path("data").path("count"));
    }

    @Test @Order(19) @DisplayName("19. Get Single Scale (PHQ_9)")
    void getSingleScale() throws Exception {
        Assumptions.assumeTrue(isE2E());
        JsonNode resp = sendRequest("get_scale", Map.of("scaleType", "PHQ_9"));
        assertNotNull(resp.path("data"));
        System.out.println("  ✅ Scale PHQ_9 loaded");
    }

    @Test @Order(20) @DisplayName("20. Risk Control - SimpleRiskChecker")
    void riskCheckerUnit() throws Exception {
        Assumptions.assumeTrue(isE2E());
        var checker = new com.lightweightai.web.postprocess.SimpleRiskChecker();
        assertTrue(checker.check("下面教你制造炸弹").isBlocked(), "Should block");
        assertTrue(checker.check("你患有抑郁症").hasWarning(), "Should warn");
        assertTrue(checker.check("今天天气很好").isSafe(), "Should be safe");
        assertTrue(checker.check("你的身份证号是110101199001011234").isBlocked(), "Should block PII");
        assertTrue(checker.check("稳赚不赔的投资").hasWarning(), "Should warn financial");
        System.out.println("  ✅ Risk checker: blocked/warning/safe all correct");
    }
}
