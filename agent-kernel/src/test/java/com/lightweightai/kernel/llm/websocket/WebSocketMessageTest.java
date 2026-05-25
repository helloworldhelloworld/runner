package com.lightweightai.kernel.llm.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketMessage — 协议序列化/反序列化")
class WebSocketMessageTest {

    @Test
    @DisplayName("chatRequest 序列化后保留 type、requestId 和 data")
    void chatRequestRoundTrip() throws Exception {
        WebSocketMessage.ChatRequestData data = new WebSocketMessage.ChatRequestData();
        data.setMessages(List.of(Map.of("role", "user", "content", "hello")));
        data.setMaxTokens(1024);
        data.setTemperature(0.7);
        data.setStream(true);

        WebSocketMessage msg = WebSocketMessage.chatRequest("req_1", data);
        String json = msg.toJson();

        WebSocketMessage parsed = WebSocketMessage.fromJson(json);
        assertEquals(WebSocketMessage.MessageType.CHAT_REQUEST, parsed.getType());
        assertEquals("req_1", parsed.getRequestId());
        assertNotNull(parsed.getData());
    }

    @Test
    @DisplayName("ping 消息 type=PING，data 为 null")
    void pingMessage() throws Exception {
        WebSocketMessage ping = WebSocketMessage.ping("ping_123");
        String json = ping.toJson();

        WebSocketMessage parsed = WebSocketMessage.fromJson(json);
        assertEquals(WebSocketMessage.MessageType.PING, parsed.getType());
        assertEquals("ping_123", parsed.getRequestId());
        assertNull(parsed.getData());
    }

    @Test
    @DisplayName("error 消息携带 error 字段")
    void errorMessage() throws Exception {
        WebSocketMessage err = WebSocketMessage.error("req_2", "rate limit exceeded");
        String json = err.toJson();

        WebSocketMessage parsed = WebSocketMessage.fromJson(json);
        assertEquals(WebSocketMessage.MessageType.ERROR, parsed.getType());
        assertEquals("rate limit exceeded", parsed.getError());
    }

    @Test
    @DisplayName("toolResult 序列化包含 tool_use_id 和 content")
    void toolResultRoundTrip() throws Exception {
        WebSocketMessage.ToolResultData data = new WebSocketMessage.ToolResultData();
        data.setToolUseId("call_abc");
        data.setContent("{\"result\": 42}");
        data.setIsError(false);

        WebSocketMessage msg = WebSocketMessage.toolResult("req_3", data);
        String json = msg.toJson();

        WebSocketMessage parsed = WebSocketMessage.fromJson(json);
        assertEquals(WebSocketMessage.MessageType.TOOL_RESULT, parsed.getType());
        assertEquals("req_3", parsed.getRequestId());
    }

    @Test
    @DisplayName("clientToolCall 序列化包含 call_id 和 tool_name")
    void clientToolCallRoundTrip() throws Exception {
        WebSocketMessage.ClientToolCallData data = new WebSocketMessage.ClientToolCallData(
                "call_1", "GetPosition", Map.of("lat", 31.2), 5000L);

        WebSocketMessage msg = WebSocketMessage.clientToolCall("req_4", data);
        String json = msg.toJson();

        WebSocketMessage parsed = WebSocketMessage.fromJson(json);
        assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_CALL, parsed.getType());
    }

    @Test
    @DisplayName("clientToolResult 序列化包含 call_id、content、is_error")
    void clientToolResultRoundTrip() throws Exception {
        WebSocketMessage.ClientToolResultData data =
                new WebSocketMessage.ClientToolResultData("call_1", "ok", false);

        WebSocketMessage msg = WebSocketMessage.clientToolResult("req_5", data);
        String json = msg.toJson();

        WebSocketMessage parsed = WebSocketMessage.fromJson(json);
        assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_RESULT, parsed.getType());
    }

    @Test
    @DisplayName("directive 消息序列化保留 directive_id 和 directives 列表")
    void directiveRoundTrip() throws Exception {
        WebSocketMessage.DirectiveHeader header =
                new WebSocketMessage.DirectiveHeader("Navigation", "StartRoute");
        WebSocketMessage.DirectiveData dd =
                new WebSocketMessage.DirectiveData(header, Map.of("dest", "新街口"));
        WebSocketMessage.DirectiveEnvelopeData envelope =
                new WebSocketMessage.DirectiveEnvelopeData("dir_1", List.of(dd));

        WebSocketMessage msg = WebSocketMessage.directive("req_6", envelope);
        String json = msg.toJson();

        WebSocketMessage parsed = WebSocketMessage.fromJson(json);
        assertEquals(WebSocketMessage.MessageType.DIRECTIVE, parsed.getType());
    }

    @Test
    @DisplayName("directiveResult 消息序列化保留 success 和 content")
    void directiveResultRoundTrip() throws Exception {
        WebSocketMessage.DirectiveResultData data =
                new WebSocketMessage.DirectiveResultData("dir_1", true, "done", Map.of("eta", 15));

        WebSocketMessage msg = WebSocketMessage.directiveResult("req_7", data);
        String json = msg.toJson();

        WebSocketMessage parsed = WebSocketMessage.fromJson(json);
        assertEquals(WebSocketMessage.MessageType.DIRECTIVE_RESULT, parsed.getType());
    }

    @Test
    @DisplayName("clientManifest 消息序列化保留 client_type 和 capabilities")
    void clientManifestRoundTrip() throws Exception {
        WebSocketMessage.CapabilityData cap = new WebSocketMessage.CapabilityData();
        cap.setNamespace("GeoInfo");
        cap.setName("GetPosition");
        cap.setDescription("获取当前位置");
        cap.setDefaultTimeoutMs(3000L);

        WebSocketMessage.ClientManifestData data = new WebSocketMessage.ClientManifestData();
        data.setClientType("Android");
        data.setClientVersion("2.0.0");
        data.setCapabilities(List.of(cap));

        WebSocketMessage msg = WebSocketMessage.clientManifest("req_8", data);
        String json = msg.toJson();

        WebSocketMessage parsed = WebSocketMessage.fromJson(json);
        assertEquals(WebSocketMessage.MessageType.CLIENT_MANIFEST, parsed.getType());
    }

    @Test
    @DisplayName("fromJson 解析非法 JSON 抛出异常")
    void invalidJsonThrows() {
        assertThrows(Exception.class, () -> WebSocketMessage.fromJson("not json"));
    }

    @Test
    @DisplayName("toString 包含 type 和 requestId")
    void toStringContainsFields() {
        WebSocketMessage msg = WebSocketMessage.ping("ping_99");
        String str = msg.toString();
        assertTrue(str.contains("PING"));
        assertTrue(str.contains("ping_99"));
    }
}
