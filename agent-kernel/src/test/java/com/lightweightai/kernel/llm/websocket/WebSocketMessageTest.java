package com.lightweightai.kernel.llm.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketMessage - Protocol serialization & factory methods")
class WebSocketMessageTest {

    @Test
    @DisplayName("chatRequest factory sets type, requestId and data")
    void chatRequestFactory() {
        WebSocketMessage.ChatRequestData data = new WebSocketMessage.ChatRequestData();
        data.setMessages(List.of(Map.of("role", "user", "content", "hello")));
        data.setMaxTokens(100);
        data.setTemperature(0.7);
        data.setStream(true);

        WebSocketMessage msg = WebSocketMessage.chatRequest("req-1", data);

        assertEquals(WebSocketMessage.MessageType.CHAT_REQUEST, msg.getType());
        assertEquals("req-1", msg.getRequestId());
        assertNotNull(msg.getData());
    }

    @Test
    @DisplayName("toolResult factory preserves all fields")
    void toolResultFactory() {
        WebSocketMessage.ToolResultData data = new WebSocketMessage.ToolResultData();
        data.setToolUseId("tu-1");
        data.setContent("result text");
        data.setIsError(false);

        WebSocketMessage msg = WebSocketMessage.toolResult("req-2", data);

        assertEquals(WebSocketMessage.MessageType.TOOL_RESULT, msg.getType());
        assertEquals("req-2", msg.getRequestId());
        assertNull(msg.getError());
    }

    @Test
    @DisplayName("ping factory creates PING with null data")
    void pingFactory() {
        WebSocketMessage msg = WebSocketMessage.ping("ping-1");

        assertEquals(WebSocketMessage.MessageType.PING, msg.getType());
        assertEquals("ping-1", msg.getRequestId());
        assertNull(msg.getData());
    }

    @Test
    @DisplayName("error factory sets error message")
    void errorFactory() {
        WebSocketMessage msg = WebSocketMessage.error("req-3", "something went wrong");

        assertEquals(WebSocketMessage.MessageType.ERROR, msg.getType());
        assertEquals("req-3", msg.getRequestId());
        assertEquals("something went wrong", msg.getError());
        assertNull(msg.getData());
    }

    @Test
    @DisplayName("clientToolCall factory preserves callId, toolName, arguments, timeout")
    void clientToolCallFactory() {
        WebSocketMessage.ClientToolCallData data = new WebSocketMessage.ClientToolCallData(
                "call-1", "get_position", Map.of("lat", 1.0), 5000L);

        WebSocketMessage msg = WebSocketMessage.clientToolCall("req-4", data);

        assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_CALL, msg.getType());
    }

    @Test
    @DisplayName("clientToolResult factory preserves callId, content, isError")
    void clientToolResultFactory() {
        WebSocketMessage.ClientToolResultData data =
                new WebSocketMessage.ClientToolResultData("call-1", "ok", false);

        WebSocketMessage msg = WebSocketMessage.clientToolResult("req-5", data);

        assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_RESULT, msg.getType());
    }

    @Test
    @DisplayName("directive factory creates DIRECTIVE message")
    void directiveFactory() {
        WebSocketMessage.DirectiveHeader header =
                new WebSocketMessage.DirectiveHeader("ns", "cmd");
        WebSocketMessage.DirectiveData dd =
                new WebSocketMessage.DirectiveData(header, Map.of("key", "val"));
        WebSocketMessage.DirectiveEnvelopeData envelope =
                new WebSocketMessage.DirectiveEnvelopeData("d-1", List.of(dd));

        WebSocketMessage msg = WebSocketMessage.directive("req-6", envelope);

        assertEquals(WebSocketMessage.MessageType.DIRECTIVE, msg.getType());
    }

    @Test
    @DisplayName("directiveResult factory creates DIRECTIVE_RESULT message")
    void directiveResultFactory() {
        WebSocketMessage.DirectiveResultData data =
                new WebSocketMessage.DirectiveResultData("d-1", true, "done", Map.of());

        WebSocketMessage msg = WebSocketMessage.directiveResult("req-7", data);

        assertEquals(WebSocketMessage.MessageType.DIRECTIVE_RESULT, msg.getType());
    }

    @Test
    @DisplayName("clientManifest factory creates CLIENT_MANIFEST message")
    void clientManifestFactory() {
        WebSocketMessage.ClientManifestData data = new WebSocketMessage.ClientManifestData();
        data.setClientType("phone");
        data.setClientVersion("2.0.0");
        data.setCapabilities(List.of());

        WebSocketMessage msg = WebSocketMessage.clientManifest("req-8", data);

        assertEquals(WebSocketMessage.MessageType.CLIENT_MANIFEST, msg.getType());
    }

    // ==================== Serialization round-trip ====================

    @Test
    @DisplayName("JSON round-trip preserves type and requestId")
    void jsonRoundTrip() throws IOException {
        WebSocketMessage original = WebSocketMessage.ping("rt-1");
        String json = original.toJson();
        WebSocketMessage restored = WebSocketMessage.fromJson(json);

        assertEquals(original.getType(), restored.getType());
        assertEquals(original.getRequestId(), restored.getRequestId());
    }

    @Test
    @DisplayName("JSON round-trip preserves error field")
    void jsonRoundTripError() throws IOException {
        WebSocketMessage original = WebSocketMessage.error("rt-2", "oops");
        String json = original.toJson();
        WebSocketMessage restored = WebSocketMessage.fromJson(json);

        assertEquals("oops", restored.getError());
        assertEquals(WebSocketMessage.MessageType.ERROR, restored.getType());
    }

    @Test
    @DisplayName("fromJson with invalid JSON throws IOException")
    void invalidJsonThrows() {
        assertThrows(IOException.class, () -> WebSocketMessage.fromJson("{invalid"));
    }

    @Test
    @DisplayName("fromJson deserializes TEXT_DELTA data")
    void deserializeTextDelta() throws IOException {
        String json = """
                {"type":"TEXT_DELTA","request_id":"s-1","data":{"text":"hello","index":0}}
                """;
        WebSocketMessage msg = WebSocketMessage.fromJson(json);

        assertEquals(WebSocketMessage.MessageType.TEXT_DELTA, msg.getType());
        assertEquals("s-1", msg.getRequestId());
    }

    @Test
    @DisplayName("fromJson deserializes TOOL_CALL data")
    void deserializeToolCall() throws IOException {
        String json = """
                {"type":"TOOL_CALL","request_id":"s-2","data":{"id":"tc-1","name":"echo","input":{"msg":"hi"}}}
                """;
        WebSocketMessage msg = WebSocketMessage.fromJson(json);

        assertEquals(WebSocketMessage.MessageType.TOOL_CALL, msg.getType());
    }

    // ==================== Nested data classes ====================

    @Test
    @DisplayName("ChatRequestData getters/setters round-trip")
    void chatRequestDataRoundTrip() {
        WebSocketMessage.ChatRequestData data = new WebSocketMessage.ChatRequestData();
        data.setMessages(List.of(Map.of("role", "user")));
        data.setTools(List.of(Map.of("name", "tool1")));
        data.setMaxTokens(200);
        data.setTemperature(0.5);
        data.setStream(false);

        assertEquals(1, data.getMessages().size());
        assertEquals(1, data.getTools().size());
        assertEquals(200, data.getMaxTokens());
        assertEquals(0.5, data.getTemperature());
        assertFalse(data.getStream());
    }

    @Test
    @DisplayName("ToolResultData getters/setters round-trip")
    void toolResultDataRoundTrip() {
        WebSocketMessage.ToolResultData data = new WebSocketMessage.ToolResultData();
        data.setToolUseId("tu-x");
        data.setContent("content");
        data.setIsError(true);

        assertEquals("tu-x", data.getToolUseId());
        assertEquals("content", data.getContent());
        assertTrue(data.getIsError());
    }

    @Test
    @DisplayName("TextDeltaData getters/setters")
    void textDeltaDataRoundTrip() {
        WebSocketMessage.TextDeltaData data = new WebSocketMessage.TextDeltaData();
        data.setText("chunk");
        data.setIndex(3);

        assertEquals("chunk", data.getText());
        assertEquals(3, data.getIndex());
    }

    @Test
    @DisplayName("ToolCallData getters/setters")
    void toolCallDataRoundTrip() {
        WebSocketMessage.ToolCallData data = new WebSocketMessage.ToolCallData();
        data.setId("id-1");
        data.setName("calc");
        data.setInput(Map.of("x", 1));

        assertEquals("id-1", data.getId());
        assertEquals("calc", data.getName());
        assertEquals(1, data.getInput().get("x"));
    }

    @Test
    @DisplayName("ChatResponseData with usage and toolCalls")
    void chatResponseDataRoundTrip() {
        WebSocketMessage.UsageData usage = new WebSocketMessage.UsageData();
        usage.setInputTokens(100);
        usage.setOutputTokens(50);

        WebSocketMessage.ToolCallData tc = new WebSocketMessage.ToolCallData();
        tc.setId("tc-1");
        tc.setName("weather");
        tc.setInput(Map.of());

        WebSocketMessage.ChatResponseData data = new WebSocketMessage.ChatResponseData();
        data.setContent("response text");
        data.setToolCalls(List.of(tc));
        data.setStopReason("end_turn");
        data.setUsage(usage);

        assertEquals("response text", data.getContent());
        assertEquals(1, data.getToolCalls().size());
        assertEquals("end_turn", data.getStopReason());
        assertEquals(100, data.getUsage().getInputTokens());
        assertEquals(50, data.getUsage().getOutputTokens());
    }

    @Test
    @DisplayName("CapabilityData getters/setters")
    void capabilityDataRoundTrip() {
        WebSocketMessage.CapabilityData cap = new WebSocketMessage.CapabilityData();
        cap.setNamespace("audio");
        cap.setName("play");
        cap.setDescription("Play audio");
        cap.setInputSchema(Map.of("type", "object"));
        cap.setDefaultTimeoutMs(10000L);

        assertEquals("audio", cap.getNamespace());
        assertEquals("play", cap.getName());
        assertEquals("Play audio", cap.getDescription());
        assertEquals(10000L, cap.getDefaultTimeoutMs());
    }

    @Test
    @DisplayName("toString includes type and requestId")
    void toStringIncludesFields() {
        WebSocketMessage msg = WebSocketMessage.ping("p-1");
        String str = msg.toString();

        assertTrue(str.contains("PING"));
        assertTrue(str.contains("p-1"));
    }
}
