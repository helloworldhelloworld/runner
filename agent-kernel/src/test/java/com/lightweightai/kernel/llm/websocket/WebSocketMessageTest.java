package com.lightweightai.kernel.llm.websocket;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketMessage - WebSocket 消息协议")
class WebSocketMessageTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("chatRequest 创建聊天请求消息")
        void shouldCreateChatRequest() {
            WebSocketMessage.ChatRequestData data = new WebSocketMessage.ChatRequestData();
            data.setMaxTokens(1024);
            data.setTemperature(0.7);
            data.setStream(true);

            WebSocketMessage msg = WebSocketMessage.chatRequest("req-1", data);

            assertEquals(WebSocketMessage.MessageType.CHAT_REQUEST, msg.getType());
            assertEquals("req-1", msg.getRequestId());
            assertNotNull(msg.getData());
        }

        @Test
        @DisplayName("toolResult 创建工具结果消息")
        void shouldCreateToolResult() {
            WebSocketMessage.ToolResultData data = new WebSocketMessage.ToolResultData();
            data.setToolUseId("tu-1");
            data.setContent("result");
            data.setIsError(false);

            WebSocketMessage msg = WebSocketMessage.toolResult("req-2", data);

            assertEquals(WebSocketMessage.MessageType.TOOL_RESULT, msg.getType());
            assertEquals("req-2", msg.getRequestId());
        }

        @Test
        @DisplayName("ping 创建心跳消息")
        void shouldCreatePing() {
            WebSocketMessage msg = WebSocketMessage.ping("req-3");

            assertEquals(WebSocketMessage.MessageType.PING, msg.getType());
            assertNull(msg.getData());
        }

        @Test
        @DisplayName("error 创建错误消息")
        void shouldCreateError() {
            WebSocketMessage msg = WebSocketMessage.error("req-4", "Connection lost");

            assertEquals(WebSocketMessage.MessageType.ERROR, msg.getType());
            assertEquals("Connection lost", msg.getError());
        }

        @Test
        @DisplayName("clientToolCall 创建客户端工具调用")
        void shouldCreateClientToolCall() {
            WebSocketMessage.ClientToolCallData data = new WebSocketMessage.ClientToolCallData(
                    "call-1", "getLocation", Map.of("lat", 39.9), 5000L);

            WebSocketMessage msg = WebSocketMessage.clientToolCall("req-5", data);

            assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_CALL, msg.getType());
        }

        @Test
        @DisplayName("clientToolResult 创建客户端工具结果")
        void shouldCreateClientToolResult() {
            WebSocketMessage.ClientToolResultData data = new WebSocketMessage.ClientToolResultData(
                    "call-1", "Beijing", false);

            WebSocketMessage msg = WebSocketMessage.clientToolResult("req-6", data);

            assertEquals(WebSocketMessage.MessageType.CLIENT_TOOL_RESULT, msg.getType());
        }

        @Test
        @DisplayName("directive 创建指令消息")
        void shouldCreateDirective() {
            WebSocketMessage.DirectiveEnvelopeData data = new WebSocketMessage.DirectiveEnvelopeData(
                    "dir-1", null);

            WebSocketMessage msg = WebSocketMessage.directive("req-7", data);

            assertEquals(WebSocketMessage.MessageType.DIRECTIVE, msg.getType());
        }

        @Test
        @DisplayName("directiveResult 创建指令结果消息")
        void shouldCreateDirectiveResult() {
            WebSocketMessage.DirectiveResultData data = new WebSocketMessage.DirectiveResultData(
                    "dir-1", true, "done", null);

            WebSocketMessage msg = WebSocketMessage.directiveResult("req-8", data);

            assertEquals(WebSocketMessage.MessageType.DIRECTIVE_RESULT, msg.getType());
        }

        @Test
        @DisplayName("clientManifest 创建能力清单消息")
        void shouldCreateClientManifest() {
            WebSocketMessage.ClientManifestData data = new WebSocketMessage.ClientManifestData();
            data.setClientType("ios");
            data.setClientVersion("2.0");

            WebSocketMessage msg = WebSocketMessage.clientManifest("req-9", data);

            assertEquals(WebSocketMessage.MessageType.CLIENT_MANIFEST, msg.getType());
        }
    }

    @Nested
    @DisplayName("JSON 序列化/反序列化")
    class Serialization {

        @Test
        @DisplayName("序列化并反序列化保持一致")
        void shouldSerializeAndDeserialize() throws IOException {
            WebSocketMessage original = WebSocketMessage.ping("req-10");
            String json = original.toJson();

            WebSocketMessage restored = WebSocketMessage.fromJson(json);

            assertEquals(original.getType(), restored.getType());
            assertEquals(original.getRequestId(), restored.getRequestId());
        }

        @Test
        @DisplayName("error 消息序列化")
        void shouldSerializeErrorMessage() throws IOException {
            WebSocketMessage msg = WebSocketMessage.error("req-11", "Timeout");
            String json = msg.toJson();

            assertTrue(json.contains("ERROR"));
            assertTrue(json.contains("Timeout"));

            WebSocketMessage restored = WebSocketMessage.fromJson(json);
            assertEquals("Timeout", restored.getError());
        }
    }

    @Nested
    @DisplayName("数据类")
    class DataClasses {

        @Test
        @DisplayName("ChatRequestData getter/setter")
        void shouldSetAndGetChatRequestData() {
            WebSocketMessage.ChatRequestData data = new WebSocketMessage.ChatRequestData();
            data.setMaxTokens(2048);
            data.setTemperature(0.5);
            data.setStream(false);

            assertEquals(2048, data.getMaxTokens());
            assertEquals(0.5, data.getTemperature());
            assertFalse(data.getStream());
        }

        @Test
        @DisplayName("ToolCallData getter/setter")
        void shouldSetAndGetToolCallData() {
            WebSocketMessage.ToolCallData data = new WebSocketMessage.ToolCallData();
            data.setId("tc-1");
            data.setName("search");
            data.setInput(Map.of("q", "test"));

            assertEquals("tc-1", data.getId());
            assertEquals("search", data.getName());
            assertEquals("test", data.getInput().get("q"));
        }

        @Test
        @DisplayName("TextDeltaData getter/setter")
        void shouldSetAndGetTextDeltaData() {
            WebSocketMessage.TextDeltaData data = new WebSocketMessage.TextDeltaData();
            data.setText("Hello");
            data.setIndex(0);

            assertEquals("Hello", data.getText());
            assertEquals(0, data.getIndex());
        }

        @Test
        @DisplayName("DirectiveHeader 构造")
        void shouldCreateDirectiveHeader() {
            WebSocketMessage.DirectiveHeader header = new WebSocketMessage.DirectiveHeader("ns", "action");
            assertEquals("ns", header.getNamespace());
            assertEquals("action", header.getName());
        }

        @Test
        @DisplayName("ClientToolCallData 构造")
        void shouldCreateClientToolCallData() {
            WebSocketMessage.ClientToolCallData data = new WebSocketMessage.ClientToolCallData(
                    "c1", "tool", Map.of("a", 1), 3000L);
            assertEquals("c1", data.getCallId());
            assertEquals("tool", data.getToolName());
            assertEquals(3000L, data.getTimeoutMs());
        }

        @Test
        @DisplayName("CapabilityData getter/setter")
        void shouldSetAndGetCapabilityData() {
            WebSocketMessage.CapabilityData cap = new WebSocketMessage.CapabilityData();
            cap.setNamespace("ns");
            cap.setName("cap-1");
            cap.setDescription("desc");
            cap.setDefaultTimeoutMs(5000L);

            assertEquals("ns", cap.getNamespace());
            assertEquals("cap-1", cap.getName());
            assertEquals("desc", cap.getDescription());
            assertEquals(5000L, cap.getDefaultTimeoutMs());
        }
    }

    @Test
    @DisplayName("toString 包含类型和请求ID")
    void shouldContainKeyInfoInToString() {
        WebSocketMessage msg = WebSocketMessage.ping("req-x");
        String str = msg.toString();
        assertTrue(str.contains("PING"));
        assertTrue(str.contains("req-x"));
    }

    @Test
    @DisplayName("所有消息类型枚举值存在")
    void shouldHaveAllMessageTypes() {
        assertEquals(13, WebSocketMessage.MessageType.values().length);
    }
}
