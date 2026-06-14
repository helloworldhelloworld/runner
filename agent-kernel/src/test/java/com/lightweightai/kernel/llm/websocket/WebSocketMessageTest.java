package com.lightweightai.kernel.llm.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketMessage — 协议消息序列化/反序列化")
class WebSocketMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 序列化 round-trip ====================

    @Test
    @DisplayName("chatRequest 序列化/反序列化 round-trip")
    void chatRequestRoundTrip() throws IOException {
        WebSocketMessage.ChatRequestData data = new WebSocketMessage.ChatRequestData();
        data.setMessages(List.of(Map.of("role", "user", "content", "Hello")));
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
    @DisplayName("ping 消息序列化/反序列化")
    void pingRoundTrip() throws IOException {
        WebSocketMessage msg = WebSocketMessage.ping("ping_123");
        String json = msg.toJson();
        WebSocketMessage parsed = WebSocketMessage.fromJson(json);

        assertEquals(WebSocketMessage.MessageType.PING, parsed.getType());
        assertEquals("ping_123", parsed.getRequestId());
        assertNull(parsed.getData());
    }

    @Test
    @DisplayName("error 消息携带 error 字段")
    void errorMessageCarriesErrorField() throws IOException {
        WebSocketMessage msg = WebSocketMessage.error("req_fail", "Connection timeout");
        String json = msg.toJson();
        WebSocketMessage parsed = WebSocketMessage.fromJson(json);

        assertEquals(WebSocketMessage.MessageType.ERROR, parsed.getType());
        assertEquals("req_fail", parsed.getRequestId());
        assertEquals("Connection timeout", parsed.getError());
    }

    @Test
    @DisplayName("toolResult 消息序列化")
    void toolResultRoundTrip() throws IOException {
        WebSocketMessage.ToolResultData data = new WebSocketMessage.ToolResultData();
        data.setToolUseId("tool_1");
        data.setContent("Result content");
        data.setIsError(false);

        WebSocketMessage msg = WebSocketMessage.toolResult("req_2", data);
        String json = msg.toJson();
        WebSocketMessage parsed = WebSocketMessage.fromJson(json);

        assertEquals(WebSocketMessage.MessageType.TOOL_RESULT, parsed.getType());
        assertEquals("req_2", parsed.getRequestId());
    }

    @Test
    @DisplayName("clientToolCall 消息序列化 — payload 完整传输")
    void clientToolCallPayloadIntact() throws IOException {
        WebSocketMessage.ClientToolCallData data = new WebSocketMessage.ClientToolCallData(
            "call_1", "get_position", Map.of("accuracy", "high"), 5000L
        );

        WebSocketMessage msg = WebSocketMessage.clientToolCall("req_3", data);
        String json = msg.toJson();
        WebSocketMessage parsed = WebSocketMessage.fromJson(json);

        assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_CALL, parsed.getType());

        @SuppressWarnings("unchecked")
        Map<String, Object> parsedData = objectMapper.convertValue(parsed.getData(), Map.class);
        assertEquals("call_1", parsedData.get("call_id"));
        assertEquals("get_position", parsedData.get("tool_name"));
        assertEquals(5000, parsedData.get("timeout_ms"));
    }

    @Test
    @DisplayName("clientToolResult 消息序列化")
    void clientToolResultRoundTrip() throws IOException {
        WebSocketMessage.ClientToolResultData data = new WebSocketMessage.ClientToolResultData(
            "call_2", "Success result", false
        );

        WebSocketMessage msg = WebSocketMessage.clientToolResult("req_4", data);
        String json = msg.toJson();
        WebSocketMessage parsed = WebSocketMessage.fromJson(json);

        assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_RESULT, parsed.getType());

        @SuppressWarnings("unchecked")
        Map<String, Object> parsedData = objectMapper.convertValue(parsed.getData(), Map.class);
        assertEquals("call_2", parsedData.get("call_id"));
        assertEquals("Success result", parsedData.get("content"));
        assertEquals(false, parsedData.get("is_error"));
    }

    // ==================== Directive 协议 ====================

    @Test
    @DisplayName("directive 消息序列化 round-trip")
    void directiveRoundTrip() throws IOException {
        WebSocketMessage.DirectiveHeader header = new WebSocketMessage.DirectiveHeader("Camera", "TakePhoto");
        WebSocketMessage.DirectiveData directiveData = new WebSocketMessage.DirectiveData(
            header, Map.of("resolution", "1080p")
        );
        WebSocketMessage.DirectiveEnvelopeData envelope = new WebSocketMessage.DirectiveEnvelopeData(
            "dir_1", List.of(directiveData)
        );

        WebSocketMessage msg = WebSocketMessage.directive("req_5", envelope);
        String json = msg.toJson();
        WebSocketMessage parsed = WebSocketMessage.fromJson(json);

        assertEquals(WebSocketMessage.MessageType.DIRECTIVE, parsed.getType());
        assertEquals("req_5", parsed.getRequestId());
    }

    @Test
    @DisplayName("directiveResult 消息序列化")
    void directiveResultRoundTrip() throws IOException {
        WebSocketMessage.DirectiveResultData data = new WebSocketMessage.DirectiveResultData(
            "dir_1", true, "Photo taken", Map.of("path", "/tmp/photo.jpg")
        );

        WebSocketMessage msg = WebSocketMessage.directiveResult("req_6", data);
        String json = msg.toJson();
        WebSocketMessage parsed = WebSocketMessage.fromJson(json);

        assertEquals(WebSocketMessage.MessageType.DIRECTIVE_RESULT, parsed.getType());
    }

    @Test
    @DisplayName("clientManifest 消息序列化")
    void clientManifestRoundTrip() throws IOException {
        WebSocketMessage.CapabilityData cap = new WebSocketMessage.CapabilityData();
        cap.setNamespace("Camera");
        cap.setName("TakePhoto");
        cap.setDescription("Take a photo");
        cap.setDefaultTimeoutMs(30000L);

        WebSocketMessage.ClientManifestData manifest = new WebSocketMessage.ClientManifestData();
        manifest.setClientType("android");
        manifest.setClientVersion("2.0.0");
        manifest.setCapabilities(List.of(cap));

        WebSocketMessage msg = WebSocketMessage.clientManifest("req_7", manifest);
        String json = msg.toJson();
        WebSocketMessage parsed = WebSocketMessage.fromJson(json);

        assertEquals(WebSocketMessage.MessageType.CLIENT_MANIFEST, parsed.getType());
    }

    // ==================== 构造器和 getter/setter ====================

    @Test
    @DisplayName("默认构造器创建空消息")
    void defaultConstructor() {
        WebSocketMessage msg = new WebSocketMessage();
        assertNull(msg.getType());
        assertNull(msg.getRequestId());
        assertNull(msg.getData());
        assertNull(msg.getError());
    }

    @Test
    @DisplayName("setter/getter 完整性")
    void setterGetter() {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(WebSocketMessage.MessageType.PONG);
        msg.setRequestId("pong_1");
        msg.setData("some data");
        msg.setError("some error");

        assertEquals(WebSocketMessage.MessageType.PONG, msg.getType());
        assertEquals("pong_1", msg.getRequestId());
        assertEquals("some data", msg.getData());
        assertEquals("some error", msg.getError());
    }

    @Test
    @DisplayName("toString 包含关键字段")
    void toStringContainsFields() {
        WebSocketMessage msg = WebSocketMessage.ping("p1");
        String str = msg.toString();
        assertTrue(str.contains("PING"));
        assertTrue(str.contains("p1"));
    }

    // ==================== 内部 Data 类测试 ====================

    @Test
    @DisplayName("TextDeltaData getter/setter")
    void textDeltaData() {
        WebSocketMessage.TextDeltaData data = new WebSocketMessage.TextDeltaData();
        data.setText("Hello ");
        data.setIndex(0);

        assertEquals("Hello ", data.getText());
        assertEquals(0, data.getIndex());
    }

    @Test
    @DisplayName("ToolCallData getter/setter")
    void toolCallData() {
        WebSocketMessage.ToolCallData data = new WebSocketMessage.ToolCallData();
        data.setId("tc_1");
        data.setName("get_weather");
        data.setInput(Map.of("city", "Beijing"));

        assertEquals("tc_1", data.getId());
        assertEquals("get_weather", data.getName());
        assertEquals("Beijing", data.getInput().get("city"));
    }

    @Test
    @DisplayName("ChatResponseData getter/setter — 包含 usage 验证")
    void chatResponseData() {
        WebSocketMessage.UsageData usage = new WebSocketMessage.UsageData();
        usage.setInputTokens(100);
        usage.setOutputTokens(50);

        WebSocketMessage.ChatResponseData data = new WebSocketMessage.ChatResponseData();
        data.setContent("Response text");
        data.setStopReason("end_turn");
        data.setUsage(usage);

        assertEquals("Response text", data.getContent());
        assertEquals("end_turn", data.getStopReason());
        assertEquals(100, data.getUsage().getInputTokens());
        assertEquals(50, data.getUsage().getOutputTokens());
    }

    @Test
    @DisplayName("DirectiveHeader 构造器和 getter")
    void directiveHeader() {
        WebSocketMessage.DirectiveHeader h = new WebSocketMessage.DirectiveHeader("NS", "ACT");
        assertEquals("NS", h.getNamespace());
        assertEquals("ACT", h.getName());
    }

    @Test
    @DisplayName("CapabilityData inputSchema 传输")
    void capabilityDataSchema() {
        WebSocketMessage.CapabilityData cap = new WebSocketMessage.CapabilityData();
        Map<String, Object> schema = Map.of("type", "object", "properties", Map.of());
        cap.setInputSchema(schema);

        assertEquals("object", cap.getInputSchema().get("type"));
    }
}
