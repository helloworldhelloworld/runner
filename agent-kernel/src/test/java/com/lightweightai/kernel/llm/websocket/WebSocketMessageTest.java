package com.lightweightai.kernel.llm.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketMessage — protocol serialization and factory methods")
class WebSocketMessageTest {

    @Test
    @DisplayName("JSON round-trip preserves type and requestId")
    void jsonRoundTrip() throws IOException {
        WebSocketMessage original = new WebSocketMessage(
                WebSocketMessage.MessageType.TEXT_DELTA, "req-1", "hello");

        String json = original.toJson();
        WebSocketMessage parsed = WebSocketMessage.fromJson(json);

        assertEquals(WebSocketMessage.MessageType.TEXT_DELTA, parsed.getType());
        assertEquals("req-1", parsed.getRequestId());
    }

    @Test
    @DisplayName("ping factory creates PING message")
    void pingFactory() throws IOException {
        WebSocketMessage ping = WebSocketMessage.ping("p-1");

        assertEquals(WebSocketMessage.MessageType.PING, ping.getType());
        assertEquals("p-1", ping.getRequestId());
        assertNull(ping.getData());

        String json = ping.toJson();
        assertTrue(json.contains("PING"));
    }

    @Test
    @DisplayName("error factory creates ERROR message with error field")
    void errorFactory() throws IOException {
        WebSocketMessage err = WebSocketMessage.error("r-1", "Something went wrong");

        assertEquals(WebSocketMessage.MessageType.ERROR, err.getType());
        assertEquals("Something went wrong", err.getError());
        assertEquals("r-1", err.getRequestId());

        String json = err.toJson();
        assertTrue(json.contains("Something went wrong"));
    }

    @Test
    @DisplayName("clientToolCall factory creates CLIENT_TOOL_CALL message")
    void clientToolCallFactory() {
        WebSocketMessage.ClientToolCallData data = new WebSocketMessage.ClientToolCallData(
                "call-1", "take_photo", Map.of("resolution", "1080p"), 5000L);

        WebSocketMessage msg = WebSocketMessage.clientToolCall("r-1", data);

        assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_CALL, msg.getType());
        assertEquals("r-1", msg.getRequestId());
    }

    @Test
    @DisplayName("clientToolResult factory creates CLIENT_TOOL_RESULT message")
    void clientToolResultFactory() {
        WebSocketMessage.ClientToolResultData data = new WebSocketMessage.ClientToolResultData(
                "call-1", "photo.jpg", false);

        WebSocketMessage msg = WebSocketMessage.clientToolResult("r-1", data);

        assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_RESULT, msg.getType());
    }

    @Test
    @DisplayName("directive factory creates DIRECTIVE message")
    void directiveFactory() throws IOException {
        WebSocketMessage.DirectiveHeader header = new WebSocketMessage.DirectiveHeader("Camera", "TakePhoto");
        WebSocketMessage.DirectiveData dd = new WebSocketMessage.DirectiveData(header, Map.of("res", "4K"));
        WebSocketMessage.DirectiveEnvelopeData envelope = new WebSocketMessage.DirectiveEnvelopeData(
                "d-1", List.of(dd));

        WebSocketMessage msg = WebSocketMessage.directive("r-1", envelope);

        assertEquals(WebSocketMessage.MessageType.DIRECTIVE, msg.getType());

        String json = msg.toJson();
        assertTrue(json.contains("Camera"));
    }

    @Test
    @DisplayName("directiveResult factory creates DIRECTIVE_RESULT message")
    void directiveResultFactory() {
        WebSocketMessage.DirectiveResultData data = new WebSocketMessage.DirectiveResultData(
                "d-1", true, "photo taken", Map.of("url", "img.jpg"));

        WebSocketMessage msg = WebSocketMessage.directiveResult("r-1", data);

        assertEquals(WebSocketMessage.MessageType.DIRECTIVE_RESULT, msg.getType());
    }

    @Test
    @DisplayName("clientManifest factory creates CLIENT_MANIFEST message")
    void clientManifestFactory() {
        WebSocketMessage.ClientManifestData manifest = new WebSocketMessage.ClientManifestData();
        manifest.setClientType("android");
        manifest.setClientVersion("2.0");

        WebSocketMessage msg = WebSocketMessage.clientManifest("r-1", manifest);

        assertEquals(WebSocketMessage.MessageType.CLIENT_MANIFEST, msg.getType());
    }

    @Test
    @DisplayName("chatRequest factory creates CHAT_REQUEST message")
    void chatRequestFactory() {
        WebSocketMessage.ChatRequestData data = new WebSocketMessage.ChatRequestData();
        data.setMessages(List.of(Map.of("role", "user", "content", "hello")));
        data.setMaxTokens(1024);
        data.setTemperature(0.7);
        data.setStream(true);

        WebSocketMessage msg = WebSocketMessage.chatRequest("r-1", data);

        assertEquals(WebSocketMessage.MessageType.CHAT_REQUEST, msg.getType());
    }

    @Test
    @DisplayName("toolResult factory creates TOOL_RESULT message")
    void toolResultFactory() {
        WebSocketMessage.ToolResultData data = new WebSocketMessage.ToolResultData();
        data.setToolUseId("tu-1");
        data.setContent("42");
        data.setIsError(false);

        WebSocketMessage msg = WebSocketMessage.toolResult("r-1", data);

        assertEquals(WebSocketMessage.MessageType.TOOL_RESULT, msg.getType());
    }

    @Test
    @DisplayName("setters update message fields")
    void settersWork() {
        WebSocketMessage msg = new WebSocketMessage();
        msg.setType(WebSocketMessage.MessageType.PONG);
        msg.setRequestId("r-2");
        msg.setData("pong data");
        msg.setError(null);

        assertEquals(WebSocketMessage.MessageType.PONG, msg.getType());
        assertEquals("r-2", msg.getRequestId());
        assertEquals("pong data", msg.getData());
    }

    @Test
    @DisplayName("MessageType enum covers all expected types")
    void messageTypeEnum() {
        WebSocketMessage.MessageType[] types = WebSocketMessage.MessageType.values();
        assertTrue(types.length >= 12);

        assertNotNull(WebSocketMessage.MessageType.valueOf("CHAT_REQUEST"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("TEXT_DELTA"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("CLIENT_TOOL_CALL"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("CLIENT_TOOL_RESULT"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("DIRECTIVE"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("DIRECTIVE_RESULT"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("CLIENT_MANIFEST"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("PING"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("PONG"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("ERROR"));
    }

    @Test
    @DisplayName("toString includes type and requestId")
    void toStringContent() {
        WebSocketMessage msg = WebSocketMessage.ping("p-1");
        String s = msg.toString();
        assertTrue(s.contains("PING"));
        assertTrue(s.contains("p-1"));
    }

    @Test
    @DisplayName("ClientToolCallData getters/setters round-trip")
    void clientToolCallDataGettersSetters() {
        WebSocketMessage.ClientToolCallData data = new WebSocketMessage.ClientToolCallData();
        data.setCallId("c-1");
        data.setToolName("gps");
        data.setArguments(Map.of("accuracy", "high"));
        data.setTimeoutMs(10000L);

        assertEquals("c-1", data.getCallId());
        assertEquals("gps", data.getToolName());
        assertEquals("high", data.getArguments().get("accuracy"));
        assertEquals(10000L, data.getTimeoutMs());
    }

    @Test
    @DisplayName("DirectiveHeader getters/setters round-trip")
    void directiveHeaderGettersSetters() {
        WebSocketMessage.DirectiveHeader header = new WebSocketMessage.DirectiveHeader();
        header.setNamespace("GPS");
        header.setName("GetPosition");

        assertEquals("GPS", header.getNamespace());
        assertEquals("GetPosition", header.getName());
    }
}
