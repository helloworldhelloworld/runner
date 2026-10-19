package com.lightweightai.kernel.llm.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketMessage - protocol message serialization")
class WebSocketMessageTest {

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("chatRequest creates correct message type")
        void chatRequest() {
            var data = new WebSocketMessage.ChatRequestData();
            data.setMessages(List.of(Map.of("role", "user", "content", "hello")));
            data.setMaxTokens(1024);
            data.setStream(true);

            WebSocketMessage msg = WebSocketMessage.chatRequest("req-1", data);
            assertEquals(WebSocketMessage.MessageType.CHAT_REQUEST, msg.getType());
            assertEquals("req-1", msg.getRequestId());
            assertNotNull(msg.getData());
        }

        @Test
        @DisplayName("ping creates PING message with null data")
        void ping() {
            WebSocketMessage msg = WebSocketMessage.ping("req-2");
            assertEquals(WebSocketMessage.MessageType.PING, msg.getType());
            assertEquals("req-2", msg.getRequestId());
            assertNull(msg.getData());
        }

        @Test
        @DisplayName("error creates ERROR message with error field")
        void error() {
            WebSocketMessage msg = WebSocketMessage.error("req-3", "something went wrong");
            assertEquals(WebSocketMessage.MessageType.ERROR, msg.getType());
            assertEquals("req-3", msg.getRequestId());
            assertEquals("something went wrong", msg.getError());
            assertNull(msg.getData());
        }

        @Test
        @DisplayName("clientToolCall creates CLIENT_TOOL_CALL message")
        void clientToolCall() {
            var data = new WebSocketMessage.ClientToolCallData(
                "call-1", "Camera.TakePhoto", Map.of("resolution", "1080p"), 5000L);
            WebSocketMessage msg = WebSocketMessage.clientToolCall("req-4", data);
            assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_CALL, msg.getType());
        }

        @Test
        @DisplayName("clientToolResult creates CLIENT_TOOL_RESULT message")
        void clientToolResult() {
            var data = new WebSocketMessage.ClientToolResultData("call-1", "photo taken", false);
            WebSocketMessage msg = WebSocketMessage.clientToolResult("req-5", data);
            assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_RESULT, msg.getType());
        }

        @Test
        @DisplayName("toolResult creates TOOL_RESULT message")
        void toolResult() {
            var data = new WebSocketMessage.ToolResultData();
            data.setToolUseId("tu-1");
            data.setContent("result");
            data.setIsError(false);
            WebSocketMessage msg = WebSocketMessage.toolResult("req-6", data);
            assertEquals(WebSocketMessage.MessageType.TOOL_RESULT, msg.getType());
        }

        @Test
        @DisplayName("directive creates DIRECTIVE message")
        void directive() {
            var header = new WebSocketMessage.DirectiveHeader("Eyes", "SetExpression");
            var dirData = new WebSocketMessage.DirectiveData(header, Map.of("expression", "happy"));
            var envelope = new WebSocketMessage.DirectiveEnvelopeData("d-1", List.of(dirData));
            WebSocketMessage msg = WebSocketMessage.directive("req-7", envelope);
            assertEquals(WebSocketMessage.MessageType.DIRECTIVE, msg.getType());
        }

        @Test
        @DisplayName("clientManifest creates CLIENT_MANIFEST message")
        void clientManifest() {
            var manifest = new WebSocketMessage.ClientManifestData();
            manifest.setClientType("android");
            manifest.setClientVersion("2.0");
            WebSocketMessage msg = WebSocketMessage.clientManifest("req-8", manifest);
            assertEquals(WebSocketMessage.MessageType.CLIENT_MANIFEST, msg.getType());
        }
    }

    @Nested
    @DisplayName("JSON serialization round-trip")
    class SerializationTests {

        @Test
        @DisplayName("ping message survives JSON round-trip")
        void pingRoundTrip() throws IOException {
            WebSocketMessage original = WebSocketMessage.ping("req-rt-1");
            String json = original.toJson();
            WebSocketMessage parsed = WebSocketMessage.fromJson(json);

            assertEquals(WebSocketMessage.MessageType.PING, parsed.getType());
            assertEquals("req-rt-1", parsed.getRequestId());
        }

        @Test
        @DisplayName("error message survives JSON round-trip")
        void errorRoundTrip() throws IOException {
            WebSocketMessage original = WebSocketMessage.error("req-rt-2", "timeout");
            String json = original.toJson();
            WebSocketMessage parsed = WebSocketMessage.fromJson(json);

            assertEquals(WebSocketMessage.MessageType.ERROR, parsed.getType());
            assertEquals("req-rt-2", parsed.getRequestId());
            assertEquals("timeout", parsed.getError());
        }

        @Test
        @DisplayName("chat request with tools round-trips")
        void chatRequestRoundTrip() throws IOException {
            var data = new WebSocketMessage.ChatRequestData();
            data.setMessages(List.of(Map.of("role", "user", "content", "hi")));
            data.setMaxTokens(512);
            data.setTemperature(0.7);
            data.setStream(true);

            WebSocketMessage original = WebSocketMessage.chatRequest("req-rt-3", data);
            String json = original.toJson();
            WebSocketMessage parsed = WebSocketMessage.fromJson(json);

            assertEquals(WebSocketMessage.MessageType.CHAT_REQUEST, parsed.getType());
            assertEquals("req-rt-3", parsed.getRequestId());
            assertNotNull(parsed.getData());
        }

        @Test
        @DisplayName("no-arg constructor creates valid default message")
        void defaultConstructor() {
            WebSocketMessage msg = new WebSocketMessage();
            assertNull(msg.getType());
            assertNull(msg.getRequestId());
            assertNull(msg.getData());
            assertNull(msg.getError());
        }
    }

    @Nested
    @DisplayName("Inner data classes")
    class InnerDataClasses {

        @Test
        @DisplayName("ClientToolCallData stores all fields")
        void clientToolCallData() {
            var data = new WebSocketMessage.ClientToolCallData(
                "c1", "GPS.GetLocation", Map.of("accuracy", "high"), 3000L);
            assertEquals("c1", data.getCallId());
            assertEquals("GPS.GetLocation", data.getToolName());
            assertEquals("high", data.getArguments().get("accuracy"));
            assertEquals(3000L, data.getTimeoutMs());
        }

        @Test
        @DisplayName("ClientToolResultData stores all fields")
        void clientToolResultData() {
            var data = new WebSocketMessage.ClientToolResultData("c2", "lat=31.2", false);
            assertEquals("c2", data.getCallId());
            assertEquals("lat=31.2", data.getContent());
            assertFalse(data.getIsError());
        }

        @Test
        @DisplayName("DirectiveResultData stores all fields")
        void directiveResultData() {
            var data = new WebSocketMessage.DirectiveResultData(
                "d-1", true, "ok", Map.of("frame", 1));
            assertEquals("d-1", data.getDirectiveId());
            assertTrue(data.getSuccess());
            assertEquals("ok", data.getContent());
            assertEquals(1, data.getMetadata().get("frame"));
        }

        @Test
        @DisplayName("CapabilityData stores all fields")
        void capabilityData() {
            var cap = new WebSocketMessage.CapabilityData();
            cap.setNamespace("Camera");
            cap.setName("TakePhoto");
            cap.setDescription("Take a photo");
            cap.setInputSchema(Map.of("type", "object"));
            cap.setDefaultTimeoutMs(5000L);

            assertEquals("Camera", cap.getNamespace());
            assertEquals("TakePhoto", cap.getName());
            assertEquals("Take a photo", cap.getDescription());
            assertEquals(5000L, cap.getDefaultTimeoutMs());
        }

        @Test
        @DisplayName("UsageData stores token counts")
        void usageData() {
            var usage = new WebSocketMessage.UsageData();
            usage.setInputTokens(100);
            usage.setOutputTokens(50);
            assertEquals(100, usage.getInputTokens());
            assertEquals(50, usage.getOutputTokens());
        }
    }

    @Test
    @DisplayName("toString includes type and requestId")
    void toStringIncludesFields() {
        WebSocketMessage msg = WebSocketMessage.ping("req-ts");
        String str = msg.toString();
        assertTrue(str.contains("PING"));
        assertTrue(str.contains("req-ts"));
    }

    @Test
    @DisplayName("MessageType enum has all expected values")
    void messageTypeEnumValues() {
        WebSocketMessage.MessageType[] types = WebSocketMessage.MessageType.values();
        assertTrue(types.length >= 12);
        assertNotNull(WebSocketMessage.MessageType.valueOf("CHAT_REQUEST"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("TEXT_DELTA"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("TOOL_CALL"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("CLIENT_TOOL_CALL"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("DIRECTIVE"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("PING"));
        assertNotNull(WebSocketMessage.MessageType.valueOf("PONG"));
    }
}
