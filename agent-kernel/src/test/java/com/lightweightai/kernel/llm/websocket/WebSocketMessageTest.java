package com.lightweightai.kernel.llm.websocket;

import com.lightweightai.kernel.llm.websocket.WebSocketMessage.CapabilityData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ChatRequestData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ChatResponseData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ClientManifestData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ClientToolCallData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ClientToolResultData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.DirectiveData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.DirectiveEnvelopeData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.DirectiveHeader;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.DirectiveResultData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.MessageType;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.TextDeltaData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ToolCallData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ToolResultData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.UsageData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketMessage -- WebSocket protocol message types")
class WebSocketMessageTest {

    // ==================== Factory methods ====================

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethodTests {

        @Test
        @DisplayName("chatRequest sets CHAT_REQUEST type, requestId, and data")
        void chatRequestFactory() {
            ChatRequestData data = new ChatRequestData();
            data.setMessages(List.of(Map.of("role", "user", "content", "hello")));
            data.setMaxTokens(1024);

            WebSocketMessage msg = WebSocketMessage.chatRequest("req-1", data);

            assertEquals(MessageType.CHAT_REQUEST, msg.getType());
            assertEquals("req-1", msg.getRequestId());
            assertSame(data, msg.getData());
            assertNull(msg.getError());
        }

        @Test
        @DisplayName("toolResult sets TOOL_RESULT type, requestId, and data")
        void toolResultFactory() {
            ToolResultData data = new ToolResultData();
            data.setToolUseId("tool-42");
            data.setContent("result payload");
            data.setIsError(false);

            WebSocketMessage msg = WebSocketMessage.toolResult("req-2", data);

            assertEquals(MessageType.TOOL_RESULT, msg.getType());
            assertEquals("req-2", msg.getRequestId());
            assertSame(data, msg.getData());
        }

        @Test
        @DisplayName("ping sets PING type with null data")
        void pingFactory() {
            WebSocketMessage msg = WebSocketMessage.ping("ping-1");

            assertEquals(MessageType.PING, msg.getType());
            assertEquals("ping-1", msg.getRequestId());
            assertNull(msg.getData());
        }

        @Test
        @DisplayName("error sets ERROR type and error message, data is null")
        void errorFactory() {
            WebSocketMessage msg = WebSocketMessage.error("req-err", "something broke");

            assertEquals(MessageType.ERROR, msg.getType());
            assertEquals("req-err", msg.getRequestId());
            assertNull(msg.getData());
            assertEquals("something broke", msg.getError());
        }

        @Test
        @DisplayName("clientToolCall sets CLIENT_TOOL_CALL type")
        void clientToolCallFactory() {
            ClientToolCallData data = new ClientToolCallData(
                "call-1", "my_tool", Map.of("key", "val"), 5000L);

            WebSocketMessage msg = WebSocketMessage.clientToolCall("req-3", data);

            assertEquals(MessageType.CLIENT_TOOL_CALL, msg.getType());
            assertEquals("req-3", msg.getRequestId());
            assertSame(data, msg.getData());
        }

        @Test
        @DisplayName("clientToolResult sets CLIENT_TOOL_RESULT type")
        void clientToolResultFactory() {
            ClientToolResultData data = new ClientToolResultData("call-1", "done", false);

            WebSocketMessage msg = WebSocketMessage.clientToolResult("req-4", data);

            assertEquals(MessageType.CLIENT_TOOL_RESULT, msg.getType());
            assertEquals("req-4", msg.getRequestId());
            assertSame(data, msg.getData());
        }

        @Test
        @DisplayName("directive sets DIRECTIVE type")
        void directiveFactory() {
            DirectiveHeader header = new DirectiveHeader("ns", "cmd");
            DirectiveData directive = new DirectiveData(header, Map.of("p", "v"));
            DirectiveEnvelopeData envelope = new DirectiveEnvelopeData("dir-1", List.of(directive));

            WebSocketMessage msg = WebSocketMessage.directive("req-5", envelope);

            assertEquals(MessageType.DIRECTIVE, msg.getType());
            assertEquals("req-5", msg.getRequestId());
            assertSame(envelope, msg.getData());
        }

        @Test
        @DisplayName("directiveResult sets DIRECTIVE_RESULT type")
        void directiveResultFactory() {
            DirectiveResultData data = new DirectiveResultData(
                "dir-1", true, "ok", Map.of("meta", "data"));

            WebSocketMessage msg = WebSocketMessage.directiveResult("req-6", data);

            assertEquals(MessageType.DIRECTIVE_RESULT, msg.getType());
            assertEquals("req-6", msg.getRequestId());
            assertSame(data, msg.getData());
        }

        @Test
        @DisplayName("clientManifest sets CLIENT_MANIFEST type")
        void clientManifestFactory() {
            ClientManifestData data = new ClientManifestData();
            data.setClientType("android");
            data.setClientVersion("1.0.0");

            WebSocketMessage msg = WebSocketMessage.clientManifest("req-7", data);

            assertEquals(MessageType.CLIENT_MANIFEST, msg.getType());
            assertEquals("req-7", msg.getRequestId());
            assertSame(data, msg.getData());
        }
    }

    // ==================== JSON round-trip ====================

    @Nested
    @DisplayName("JSON round-trip serialization")
    class JsonRoundTripTests {

        @Test
        @DisplayName("chatRequest round-trips through JSON preserving type and requestId")
        void chatRequestRoundTrip() throws IOException {
            ChatRequestData data = new ChatRequestData();
            data.setMessages(List.of(Map.of("role", "user", "content", "hi")));
            data.setMaxTokens(2048);
            data.setTemperature(0.7);
            data.setStream(true);

            WebSocketMessage original = WebSocketMessage.chatRequest("req-rt-1", data);
            String json = original.toJson();
            WebSocketMessage parsed = WebSocketMessage.fromJson(json);

            assertEquals(MessageType.CHAT_REQUEST, parsed.getType());
            assertEquals("req-rt-1", parsed.getRequestId());
            assertNull(parsed.getError());
            assertNotNull(parsed.getData(), "Data should survive round-trip");
        }

        @Test
        @DisplayName("ping round-trips with null data")
        void pingRoundTrip() throws IOException {
            WebSocketMessage original = WebSocketMessage.ping("ping-rt");
            String json = original.toJson();
            WebSocketMessage parsed = WebSocketMessage.fromJson(json);

            assertEquals(MessageType.PING, parsed.getType());
            assertEquals("ping-rt", parsed.getRequestId());
            assertNull(parsed.getData());
        }

        @Test
        @DisplayName("error round-trips preserving error field")
        void errorRoundTrip() throws IOException {
            WebSocketMessage original = WebSocketMessage.error("err-rt", "timeout");
            String json = original.toJson();
            WebSocketMessage parsed = WebSocketMessage.fromJson(json);

            assertEquals(MessageType.ERROR, parsed.getType());
            assertEquals("err-rt", parsed.getRequestId());
            assertEquals("timeout", parsed.getError());
            assertNull(parsed.getData());
        }

        @Test
        @DisplayName("toolResult round-trips preserving type")
        void toolResultRoundTrip() throws IOException {
            ToolResultData data = new ToolResultData();
            data.setToolUseId("tu-1");
            data.setContent("42");
            data.setIsError(false);

            WebSocketMessage original = WebSocketMessage.toolResult("req-tr", data);
            String json = original.toJson();
            WebSocketMessage parsed = WebSocketMessage.fromJson(json);

            assertEquals(MessageType.TOOL_RESULT, parsed.getType());
            assertEquals("req-tr", parsed.getRequestId());
        }

        @Test
        @DisplayName("clientToolResult round-trips preserving type")
        void clientToolResultRoundTrip() throws IOException {
            ClientToolResultData data = new ClientToolResultData("c-1", "done", false);

            WebSocketMessage original = WebSocketMessage.clientToolResult("req-ctr", data);
            String json = original.toJson();
            WebSocketMessage parsed = WebSocketMessage.fromJson(json);

            assertEquals(MessageType.CLIENT_TOOL_RESULT, parsed.getType());
            assertEquals("req-ctr", parsed.getRequestId());
        }

        @Test
        @DisplayName("directive round-trips preserving type")
        void directiveRoundTrip() throws IOException {
            DirectiveHeader header = new DirectiveHeader("audio", "play");
            DirectiveData dir = new DirectiveData(header, Map.of("url", "http://x"));
            DirectiveEnvelopeData envelope = new DirectiveEnvelopeData("d-1", List.of(dir));

            WebSocketMessage original = WebSocketMessage.directive("req-dir", envelope);
            String json = original.toJson();
            WebSocketMessage parsed = WebSocketMessage.fromJson(json);

            assertEquals(MessageType.DIRECTIVE, parsed.getType());
            assertEquals("req-dir", parsed.getRequestId());
        }

        @Test
        @DisplayName("directiveResult round-trips preserving type")
        void directiveResultRoundTrip() throws IOException {
            DirectiveResultData data = new DirectiveResultData(
                "d-1", true, "played", Map.of("duration", 3));

            WebSocketMessage original = WebSocketMessage.directiveResult("req-dr", data);
            String json = original.toJson();
            WebSocketMessage parsed = WebSocketMessage.fromJson(json);

            assertEquals(MessageType.DIRECTIVE_RESULT, parsed.getType());
            assertEquals("req-dr", parsed.getRequestId());
        }

        @Test
        @DisplayName("clientManifest round-trips preserving type")
        void clientManifestRoundTrip() throws IOException {
            ClientManifestData data = new ClientManifestData();
            data.setClientType("ios");
            data.setClientVersion("2.0");
            data.setCapabilities(List.of());

            WebSocketMessage original = WebSocketMessage.clientManifest("req-cm", data);
            String json = original.toJson();
            WebSocketMessage parsed = WebSocketMessage.fromJson(json);

            assertEquals(MessageType.CLIENT_MANIFEST, parsed.getType());
            assertEquals("req-cm", parsed.getRequestId());
        }
    }

    // ==================== fromJson handles all MessageType values ====================

    @Nested
    @DisplayName("fromJson handles all MessageType enum values")
    class FromJsonMessageTypeTests {

        @Test
        @DisplayName("every MessageType enum value survives JSON round-trip")
        void allMessageTypesRoundTrip() throws IOException {
            for (MessageType type : MessageType.values()) {
                WebSocketMessage msg = new WebSocketMessage(type, "id-" + type.name(), null);
                String json = msg.toJson();
                WebSocketMessage parsed = WebSocketMessage.fromJson(json);

                assertEquals(type, parsed.getType(),
                    "MessageType." + type.name() + " should survive JSON round-trip");
                assertEquals("id-" + type.name(), parsed.getRequestId());
            }
        }
    }

    // ==================== No-arg constructor and setters ====================

    @Nested
    @DisplayName("No-arg constructor and setters")
    class ConstructorAndSetterTests {

        @Test
        @DisplayName("no-arg constructor creates empty message, setters populate fields")
        void noArgConstructorAndSetters() {
            WebSocketMessage msg = new WebSocketMessage();

            assertNull(msg.getType());
            assertNull(msg.getRequestId());
            assertNull(msg.getData());
            assertNull(msg.getError());

            msg.setType(MessageType.PONG);
            msg.setRequestId("pong-1");
            msg.setData(Map.of("ts", 12345));
            msg.setError("none");

            assertEquals(MessageType.PONG, msg.getType());
            assertEquals("pong-1", msg.getRequestId());
            assertNotNull(msg.getData());
            assertEquals("none", msg.getError());
        }
    }

    // ==================== Inner data class fields ====================

    @Nested
    @DisplayName("ChatRequestData fields")
    class ChatRequestDataTests {

        @Test
        @DisplayName("all fields round-trip correctly")
        void allFields() {
            ChatRequestData data = new ChatRequestData();

            List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", "hello"),
                Map.of("role", "assistant", "content", "hi")
            );
            List<Map<String, Object>> tools = List.of(
                Map.of("name", "calculator", "description", "math tool")
            );

            data.setMessages(messages);
            data.setTools(tools);
            data.setMaxTokens(4096);
            data.setTemperature(0.5);
            data.setStream(true);

            assertEquals(2, data.getMessages().size());
            assertEquals("user", data.getMessages().get(0).get("role"));
            assertEquals(1, data.getTools().size());
            assertEquals("calculator", data.getTools().get(0).get("name"));
            assertEquals(4096, data.getMaxTokens());
            assertEquals(0.5, data.getTemperature(), 0.001);
            assertTrue(data.getStream());
        }

        @Test
        @DisplayName("null fields remain null when not set")
        void nullDefaults() {
            ChatRequestData data = new ChatRequestData();

            assertNull(data.getMessages());
            assertNull(data.getTools());
            assertNull(data.getMaxTokens());
            assertNull(data.getTemperature());
            assertNull(data.getStream());
        }
    }

    @Nested
    @DisplayName("TextDeltaData fields")
    class TextDeltaDataTests {

        @Test
        @DisplayName("text and index set correctly")
        void textAndIndex() {
            TextDeltaData data = new TextDeltaData();
            data.setText("Hello ");
            data.setIndex(0);

            assertEquals("Hello ", data.getText());
            assertEquals(0, data.getIndex());
        }

        @Test
        @DisplayName("defaults are null")
        void defaults() {
            TextDeltaData data = new TextDeltaData();
            assertNull(data.getText());
            assertNull(data.getIndex());
        }
    }

    @Nested
    @DisplayName("ToolCallData fields")
    class ToolCallDataTests {

        @Test
        @DisplayName("id, name, and input map set correctly")
        void allFields() {
            ToolCallData data = new ToolCallData();
            data.setId("tc-1");
            data.setName("search");
            data.setInput(Map.of("query", "weather", "limit", 10));

            assertEquals("tc-1", data.getId());
            assertEquals("search", data.getName());
            assertEquals("weather", data.getInput().get("query"));
            assertEquals(10, data.getInput().get("limit"));
        }
    }

    @Nested
    @DisplayName("ChatResponseData fields")
    class ChatResponseDataTests {

        @Test
        @DisplayName("content, toolCalls, stopReason, and usage set correctly")
        void allFields() {
            ChatResponseData data = new ChatResponseData();

            ToolCallData tc = new ToolCallData();
            tc.setId("tc-resp");
            tc.setName("lookup");
            tc.setInput(Map.of("key", "value"));

            UsageData usage = new UsageData();
            usage.setInputTokens(100);
            usage.setOutputTokens(50);

            data.setContent("response text");
            data.setToolCalls(List.of(tc));
            data.setStopReason("end_turn");
            data.setUsage(usage);

            assertEquals("response text", data.getContent());
            assertEquals(1, data.getToolCalls().size());
            assertEquals("tc-resp", data.getToolCalls().get(0).getId());
            assertEquals("lookup", data.getToolCalls().get(0).getName());
            assertEquals("end_turn", data.getStopReason());
            assertEquals(100, data.getUsage().getInputTokens());
            assertEquals(50, data.getUsage().getOutputTokens());
        }
    }

    // ==================== Directive inner classes ====================

    @Nested
    @DisplayName("Directive inner classes")
    class DirectiveInnerClassTests {

        @Test
        @DisplayName("DirectiveHeader stores namespace and name")
        void directiveHeader() {
            DirectiveHeader header = new DirectiveHeader("audio", "play");
            assertEquals("audio", header.getNamespace());
            assertEquals("play", header.getName());
        }

        @Test
        @DisplayName("DirectiveData stores header and payload")
        void directiveData() {
            DirectiveHeader header = new DirectiveHeader("ns", "cmd");
            Map<String, Object> payload = Map.of("volume", 80);
            DirectiveData data = new DirectiveData(header, payload);

            assertSame(header, data.getHeader());
            assertEquals(80, data.getPayload().get("volume"));
        }

        @Test
        @DisplayName("DirectiveEnvelopeData stores directiveId and directives list")
        void directiveEnvelope() {
            DirectiveData d = new DirectiveData(
                new DirectiveHeader("ns", "cmd"), Map.of());
            DirectiveEnvelopeData envelope = new DirectiveEnvelopeData("env-1", List.of(d));

            assertEquals("env-1", envelope.getDirectiveId());
            assertEquals(1, envelope.getDirectives().size());
        }

        @Test
        @DisplayName("DirectiveResultData stores all fields")
        void directiveResult() {
            DirectiveResultData data = new DirectiveResultData(
                "dir-1", true, "ok", Map.of("key", "val"));

            assertEquals("dir-1", data.getDirectiveId());
            assertTrue(data.getSuccess());
            assertEquals("ok", data.getContent());
            assertEquals("val", data.getMetadata().get("key"));
        }
    }

    // ==================== Client classes ====================

    @Nested
    @DisplayName("Client inner classes")
    class ClientInnerClassTests {

        @Test
        @DisplayName("ClientToolCallData stores callId, toolName, arguments, timeoutMs")
        void clientToolCallData() {
            ClientToolCallData data = new ClientToolCallData(
                "c-1", "my_tool", Map.of("arg", "v"), 3000L);

            assertEquals("c-1", data.getCallId());
            assertEquals("my_tool", data.getToolName());
            assertEquals("v", data.getArguments().get("arg"));
            assertEquals(3000L, data.getTimeoutMs());
        }

        @Test
        @DisplayName("ClientToolResultData stores callId, content, isError")
        void clientToolResultData() {
            ClientToolResultData data = new ClientToolResultData("c-1", "result", false);

            assertEquals("c-1", data.getCallId());
            assertEquals("result", data.getContent());
            assertFalse(data.getIsError());
        }

        @Test
        @DisplayName("ClientManifestData stores clientType, clientVersion, capabilities")
        void clientManifestData() {
            ClientManifestData data = new ClientManifestData();
            data.setClientType("android");
            data.setClientVersion("3.0");

            CapabilityData cap = new CapabilityData();
            cap.setNamespace("audio");
            cap.setName("play");
            cap.setDescription("Play audio");
            cap.setInputSchema(Map.of("type", "object"));
            cap.setDefaultTimeoutMs(5000L);
            data.setCapabilities(List.of(cap));

            assertEquals("android", data.getClientType());
            assertEquals("3.0", data.getClientVersion());
            assertEquals(1, data.getCapabilities().size());

            CapabilityData parsed = data.getCapabilities().get(0);
            assertEquals("audio", parsed.getNamespace());
            assertEquals("play", parsed.getName());
            assertEquals("Play audio", parsed.getDescription());
            assertEquals("object", parsed.getInputSchema().get("type"));
            assertEquals(5000L, parsed.getDefaultTimeoutMs());
        }
    }

    // ==================== ToolResultData ====================

    @Nested
    @DisplayName("ToolResultData fields")
    class ToolResultDataTests {

        @Test
        @DisplayName("stores toolUseId, content, and isError")
        void allFields() {
            ToolResultData data = new ToolResultData();
            data.setToolUseId("tu-99");
            data.setContent("error output");
            data.setIsError(true);

            assertEquals("tu-99", data.getToolUseId());
            assertEquals("error output", data.getContent());
            assertTrue(data.getIsError());
        }
    }

    // ==================== UsageData ====================

    @Nested
    @DisplayName("UsageData fields")
    class UsageDataTests {

        @Test
        @DisplayName("stores inputTokens and outputTokens")
        void allFields() {
            UsageData data = new UsageData();
            data.setInputTokens(200);
            data.setOutputTokens(100);

            assertEquals(200, data.getInputTokens());
            assertEquals(100, data.getOutputTokens());
        }
    }

    // ==================== toString ====================

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString contains type and requestId")
        void containsFields() {
            WebSocketMessage msg = WebSocketMessage.ping("p-1");
            String str = msg.toString();

            assertTrue(str.contains("PING"), "toString should contain message type");
            assertTrue(str.contains("p-1"), "toString should contain requestId");
        }
    }
}
