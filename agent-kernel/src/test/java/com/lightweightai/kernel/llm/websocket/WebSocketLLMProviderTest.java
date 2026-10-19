package com.lightweightai.kernel.llm.websocket;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ModelCapability.ModelFeature;
import com.lightweightai.kernel.llm.ToolCall;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketLLMProvider -- WebSocket-based LLM provider")
class WebSocketLLMProviderTest {

    private WebSocketLLMProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WebSocketLLMProvider("ws://localhost:9999/ws", "test-model");
    }

    @AfterEach
    void tearDown() {
        provider.shutdown();
    }

    // ==================== Provider identity ====================

    @Nested
    @DisplayName("Provider identity")
    class ProviderIdentityTests {

        @Test
        @DisplayName("getProviderName returns 'websocket'")
        void providerName() {
            assertEquals("websocket", provider.getProviderName());
        }
    }

    // ==================== Model capability ====================

    @Nested
    @DisplayName("Model capability")
    class ModelCapabilityTests {

        @Test
        @DisplayName("modelId matches constructor argument")
        void modelId() {
            ModelCapability cap = provider.getModelCapability();
            assertEquals("test-model", cap.getModelId());
        }

        @Test
        @DisplayName("maxContextTokens is 200000")
        void maxContext() {
            ModelCapability cap = provider.getModelCapability();
            assertEquals(200000, cap.getMaxContextTokens());
        }

        @Test
        @DisplayName("maxOutputTokens is 8192")
        void maxOutput() {
            ModelCapability cap = provider.getModelCapability();
            assertEquals(8192, cap.getMaxOutputTokens());
        }

        @Test
        @DisplayName("supports TOOL_CALLING, STREAMING, SYSTEM_MESSAGE, FUNCTION_CALLING")
        void supportedFeatures() {
            ModelCapability cap = provider.getModelCapability();
            Set<ModelFeature> features = cap.getSupportedFeatures();

            assertTrue(features.contains(ModelFeature.TOOL_CALLING),
                "Should support TOOL_CALLING");
            assertTrue(features.contains(ModelFeature.STREAMING),
                "Should support STREAMING");
            assertTrue(features.contains(ModelFeature.SYSTEM_MESSAGE),
                "Should support SYSTEM_MESSAGE");
            assertTrue(features.contains(ModelFeature.FUNCTION_CALLING),
                "Should support FUNCTION_CALLING");
            assertEquals(4, features.size(),
                "Should support exactly 4 features");
        }

        @Test
        @DisplayName("messageFormatter is null")
        void messageFormatter() {
            assertNull(provider.getModelCapability().getMessageFormatter());
        }

        @Test
        @DisplayName("tokenCounter is null")
        void tokenCounter() {
            assertNull(provider.getModelCapability().getTokenCounter());
        }
    }

    // ==================== Connection state ====================

    @Nested
    @DisplayName("Connection state")
    class ConnectionStateTests {

        @Test
        @DisplayName("isConnected returns false initially")
        void notConnectedInitially() {
            assertFalse(provider.isConnected());
        }

        @Test
        @DisplayName("disconnect on already-disconnected provider is safe (no NPE)")
        void disconnectWhenAlreadyDisconnected() {
            assertFalse(provider.isConnected());
            assertDoesNotThrow(() -> provider.disconnect(),
                "Calling disconnect when not connected should not throw");
            assertFalse(provider.isConnected());
        }

        @Test
        @DisplayName("double disconnect is safe")
        void doubleDisconnect() {
            assertDoesNotThrow(() -> {
                provider.disconnect();
                provider.disconnect();
            }, "Calling disconnect twice should not throw");
        }

        @Test
        @DisplayName("shutdown calls disconnect without error")
        void shutdownSafe() {
            assertDoesNotThrow(() -> provider.shutdown(),
                "Shutdown on disconnected provider should not throw");
        }
    }

    // ==================== convertRole via reflection ====================

    @Nested
    @DisplayName("convertRole (private method)")
    class ConvertRoleTests {

        private Method convertRoleMethod;

        @BeforeEach
        void setUp() throws Exception {
            convertRoleMethod = WebSocketLLMProvider.class.getDeclaredMethod(
                "convertRole", MessageRole.class);
            convertRoleMethod.setAccessible(true);
        }

        @Test
        @DisplayName("USER -> 'user'")
        void userRole() throws Exception {
            String result = (String) convertRoleMethod.invoke(provider, MessageRole.USER);
            assertEquals("user", result);
        }

        @Test
        @DisplayName("ASSISTANT -> 'assistant'")
        void assistantRole() throws Exception {
            String result = (String) convertRoleMethod.invoke(provider, MessageRole.ASSISTANT);
            assertEquals("assistant", result);
        }

        @Test
        @DisplayName("SYSTEM -> 'system'")
        void systemRole() throws Exception {
            String result = (String) convertRoleMethod.invoke(provider, MessageRole.SYSTEM);
            assertEquals("system", result);
        }

        @Test
        @DisplayName("TOOL -> 'user' (mapped to user role)")
        void toolRole() throws Exception {
            String result = (String) convertRoleMethod.invoke(provider, MessageRole.TOOL);
            assertEquals("user", result);
        }
    }

    // ==================== buildChatRequest via reflection ====================

    @Nested
    @DisplayName("buildChatRequest (private method)")
    class BuildChatRequestTests {

        private Method buildChatRequestMethod;

        @BeforeEach
        void setUp() throws Exception {
            buildChatRequestMethod = WebSocketLLMProvider.class.getDeclaredMethod(
                "buildChatRequest", List.class, LLMOptions.class, boolean.class);
            buildChatRequestMethod.setAccessible(true);
        }

        @Test
        @DisplayName("converts messages with correct roles and content")
        void messagesConversion() throws Exception {
            List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                    .role(MessageRole.SYSTEM).textContent("system prompt").build(),
                ConversationMessage.builder()
                    .role(MessageRole.USER).textContent("hello").build(),
                ConversationMessage.builder()
                    .role(MessageRole.ASSISTANT).textContent("hi there").build()
            );

            WebSocketMessage.ChatRequestData result =
                (WebSocketMessage.ChatRequestData) buildChatRequestMethod.invoke(
                    provider, messages, LLMOptions.builder().build(), false);

            assertNotNull(result.getMessages());
            assertEquals(3, result.getMessages().size());

            assertEquals("system", result.getMessages().get(0).get("role"));
            assertEquals("system prompt", result.getMessages().get(0).get("content"));

            assertEquals("user", result.getMessages().get(1).get("role"));
            assertEquals("hello", result.getMessages().get(1).get("content"));

            assertEquals("assistant", result.getMessages().get(2).get("role"));
            assertEquals("hi there", result.getMessages().get(2).get("content"));
        }

        @Test
        @DisplayName("includes tool definitions from options")
        void toolDefinitions() throws Exception {
            List<Map<String, Object>> toolDefs = List.of(
                Map.of("name", "search", "description", "search tool",
                    "input_schema", Map.of("type", "object"))
            );
            LLMOptions options = LLMOptions.builder()
                .toolDefinitions(toolDefs)
                .build();

            List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                    .role(MessageRole.USER).textContent("find it").build()
            );

            WebSocketMessage.ChatRequestData result =
                (WebSocketMessage.ChatRequestData) buildChatRequestMethod.invoke(
                    provider, messages, options, false);

            assertNotNull(result.getTools());
            assertEquals(1, result.getTools().size());
            assertEquals("search", result.getTools().get(0).get("name"));
        }

        @Test
        @DisplayName("sets maxTokens and temperature from options")
        void maxTokensAndTemperature() throws Exception {
            LLMOptions options = LLMOptions.builder()
                .maxTokens(2048)
                .temperature(0.3)
                .build();

            List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                    .role(MessageRole.USER).textContent("test").build()
            );

            WebSocketMessage.ChatRequestData result =
                (WebSocketMessage.ChatRequestData) buildChatRequestMethod.invoke(
                    provider, messages, options, false);

            assertEquals(2048, result.getMaxTokens());
            assertEquals(0.3, result.getTemperature(), 0.001);
        }

        @Test
        @DisplayName("stream flag propagated correctly")
        void streamFlag() throws Exception {
            LLMOptions options = LLMOptions.builder().build();
            List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                    .role(MessageRole.USER).textContent("test").build()
            );

            WebSocketMessage.ChatRequestData resultNoStream =
                (WebSocketMessage.ChatRequestData) buildChatRequestMethod.invoke(
                    provider, messages, options, false);
            assertFalse(resultNoStream.getStream());

            WebSocketMessage.ChatRequestData resultStream =
                (WebSocketMessage.ChatRequestData) buildChatRequestMethod.invoke(
                    provider, messages, options, true);
            assertTrue(resultStream.getStream());
        }

        @Test
        @DisplayName("null options produces no tools, null maxTokens and temperature")
        void nullOptions() throws Exception {
            List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                    .role(MessageRole.USER).textContent("hi").build()
            );

            WebSocketMessage.ChatRequestData result =
                (WebSocketMessage.ChatRequestData) buildChatRequestMethod.invoke(
                    provider, messages, (LLMOptions) null, false);

            assertNull(result.getTools());
            assertNull(result.getMaxTokens());
            assertNull(result.getTemperature());
            assertFalse(result.getStream());
        }

        @Test
        @DisplayName("TOOL role messages mapped to 'user' in output")
        void toolRoleInMessages() throws Exception {
            List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                    .role(MessageRole.TOOL).textContent("tool result").build()
            );

            WebSocketMessage.ChatRequestData result =
                (WebSocketMessage.ChatRequestData) buildChatRequestMethod.invoke(
                    provider, messages, LLMOptions.builder().build(), false);

            assertEquals("user", result.getMessages().get(0).get("role"),
                "TOOL role should be converted to 'user'");
            assertEquals("tool result", result.getMessages().get(0).get("content"));
        }
    }

    // ==================== buildLLMResponse via reflection ====================

    @Nested
    @DisplayName("buildLLMResponse (private method)")
    class BuildLLMResponseTests {

        private Method buildLLMResponseMethod;

        @BeforeEach
        void setUp() throws Exception {
            buildLLMResponseMethod = WebSocketLLMProvider.class.getDeclaredMethod(
                "buildLLMResponse", WebSocketMessage.ChatResponseData.class);
            buildLLMResponseMethod.setAccessible(true);
        }

        @Test
        @DisplayName("text content and stopReason parsed correctly")
        void textContentAndStopReason() throws Exception {
            WebSocketMessage.ChatResponseData data = new WebSocketMessage.ChatResponseData();
            data.setContent("Hello world");
            data.setStopReason("end_turn");

            LLMResponse response = (LLMResponse) buildLLMResponseMethod.invoke(provider, data);

            assertEquals("Hello world", response.getMessage().getTextContent());
            assertEquals(MessageRole.ASSISTANT, response.getMessage().getRole());
            assertEquals("end_turn", response.getStopReason());
            assertFalse(response.hasToolCalls());
        }

        @Test
        @DisplayName("tool calls parsed correctly")
        void toolCalls() throws Exception {
            WebSocketMessage.ToolCallData tc1 = new WebSocketMessage.ToolCallData();
            tc1.setId("tc-1");
            tc1.setName("search");
            tc1.setInput(Map.of("query", "weather"));

            WebSocketMessage.ToolCallData tc2 = new WebSocketMessage.ToolCallData();
            tc2.setId("tc-2");
            tc2.setName("calculator");
            tc2.setInput(Map.of("expression", "2+2"));

            WebSocketMessage.ChatResponseData data = new WebSocketMessage.ChatResponseData();
            data.setContent("Using tools");
            data.setToolCalls(List.of(tc1, tc2));
            data.setStopReason("tool_use");

            LLMResponse response = (LLMResponse) buildLLMResponseMethod.invoke(provider, data);

            assertTrue(response.hasToolCalls());
            assertEquals(2, response.getToolCalls().size());

            ToolCall firstCall = response.getToolCalls().get(0);
            assertEquals("tc-1", firstCall.getId());
            assertEquals("search", firstCall.getName());
            assertEquals("weather", firstCall.getArguments().get("query"));

            ToolCall secondCall = response.getToolCalls().get(1);
            assertEquals("tc-2", secondCall.getId());
            assertEquals("calculator", secondCall.getName());
            assertEquals("2+2", secondCall.getArguments().get("expression"));
        }

        @Test
        @DisplayName("usage info parsed correctly")
        void usageInfo() throws Exception {
            WebSocketMessage.UsageData usage = new WebSocketMessage.UsageData();
            usage.setInputTokens(150);
            usage.setOutputTokens(75);

            WebSocketMessage.ChatResponseData data = new WebSocketMessage.ChatResponseData();
            data.setContent("OK");
            data.setStopReason("end_turn");
            data.setUsage(usage);

            LLMResponse response = (LLMResponse) buildLLMResponseMethod.invoke(provider, data);

            assertNotNull(response.getUsage());
            assertEquals(150, response.getUsage().getInputTokens());
            assertEquals(75, response.getUsage().getOutputTokens());
            assertEquals(225, response.getUsage().getTotalTokens());
        }

        @Test
        @DisplayName("null content becomes empty string")
        void nullContent() throws Exception {
            WebSocketMessage.ChatResponseData data = new WebSocketMessage.ChatResponseData();
            data.setContent(null);
            data.setStopReason("end_turn");

            LLMResponse response = (LLMResponse) buildLLMResponseMethod.invoke(provider, data);

            assertEquals("", response.getMessage().getTextContent());
        }

        @Test
        @DisplayName("null toolCalls produces empty list")
        void nullToolCalls() throws Exception {
            WebSocketMessage.ChatResponseData data = new WebSocketMessage.ChatResponseData();
            data.setContent("hello");
            data.setToolCalls(null);

            LLMResponse response = (LLMResponse) buildLLMResponseMethod.invoke(provider, data);

            assertNotNull(response.getToolCalls());
            assertTrue(response.getToolCalls().isEmpty());
        }

        @Test
        @DisplayName("null usage produces null UsageInfo")
        void nullUsage() throws Exception {
            WebSocketMessage.ChatResponseData data = new WebSocketMessage.ChatResponseData();
            data.setContent("hello");
            data.setUsage(null);

            LLMResponse response = (LLMResponse) buildLLMResponseMethod.invoke(provider, data);

            assertNull(response.getUsage());
        }
    }

    // ==================== handleMessage via reflection ====================

    @Nested
    @DisplayName("handleMessage (private method)")
    class HandleMessageTests {

        private Method handleMessageMethod;

        @BeforeEach
        void setUp() throws Exception {
            handleMessageMethod = WebSocketLLMProvider.class.getDeclaredMethod(
                "handleMessage", String.class);
            handleMessageMethod.setAccessible(true);
        }

        @Test
        @DisplayName("PONG message processed without error")
        void pongMessage() throws Exception {
            String json = WebSocketMessage.ping("pong-test").toJson()
                .replace("PING", "PONG");

            // PONG is a no-op, should not throw
            assertDoesNotThrow(
                () -> handleMessageMethod.invoke(provider, json));
        }

        @Test
        @DisplayName("ERROR message with unknown requestId does not throw")
        void errorWithUnknownRequestId() throws Exception {
            WebSocketMessage errorMsg = WebSocketMessage.error("unknown-req", "test error");
            String json = errorMsg.toJson();

            assertDoesNotThrow(
                () -> handleMessageMethod.invoke(provider, json),
                "ERROR for unknown requestId should not throw");
        }

        @Test
        @DisplayName("TEXT_DELTA with unknown requestId is silently ignored")
        void textDeltaUnknownRequest() throws Exception {
            // Manually construct TEXT_DELTA JSON since there's no factory for server messages
            String json = "{\"type\":\"TEXT_DELTA\",\"request_id\":\"unknown\"," +
                "\"data\":{\"text\":\"hello\",\"index\":0}}";

            assertDoesNotThrow(
                () -> handleMessageMethod.invoke(provider, json),
                "TEXT_DELTA for unknown requestId should be silently ignored");
        }

        @Test
        @DisplayName("TOOL_CALL with unknown requestId is silently ignored")
        void toolCallUnknownRequest() throws Exception {
            String json = "{\"type\":\"TOOL_CALL\",\"request_id\":\"unknown\"," +
                "\"data\":{\"id\":\"tc-1\",\"name\":\"search\",\"input\":{\"q\":\"test\"}}}";

            assertDoesNotThrow(
                () -> handleMessageMethod.invoke(provider, json),
                "TOOL_CALL for unknown requestId should be silently ignored");
        }

        @Test
        @DisplayName("CHAT_RESPONSE with unknown requestId is handled gracefully")
        void chatResponseUnknownRequest() throws Exception {
            String json = "{\"type\":\"CHAT_RESPONSE\",\"request_id\":\"unknown\"," +
                "\"data\":{\"content\":\"hi\",\"stop_reason\":\"end_turn\"}}";

            assertDoesNotThrow(
                () -> handleMessageMethod.invoke(provider, json),
                "CHAT_RESPONSE for unknown requestId should not throw");
        }

        @Test
        @DisplayName("malformed JSON is handled gracefully (logged, not thrown)")
        void malformedJson() throws Exception {
            assertDoesNotThrow(
                () -> handleMessageMethod.invoke(provider, "not valid json{{{"),
                "Malformed JSON should be caught, not propagated");
        }
    }

    // ==================== Constructor with custom OkHttpClient ====================

    @Nested
    @DisplayName("Constructor variants")
    class ConstructorTests {

        @Test
        @DisplayName("two-arg constructor creates provider with correct URL and model")
        void twoArgConstructor() {
            WebSocketLLMProvider p = new WebSocketLLMProvider(
                "ws://example.com/ws", "my-model");

            assertEquals("websocket", p.getProviderName());
            assertEquals("my-model", p.getModelCapability().getModelId());
            assertFalse(p.isConnected());
            p.shutdown();
        }

        @Test
        @DisplayName("three-arg constructor accepts custom OkHttpClient")
        void threeArgConstructor() {
            OkHttpClient customClient = new OkHttpClient.Builder().build();
            WebSocketLLMProvider p = new WebSocketLLMProvider(
                "ws://example.com/ws", "custom-model", customClient);

            assertEquals("websocket", p.getProviderName());
            assertEquals("custom-model", p.getModelCapability().getModelId());
            assertFalse(p.isConnected());
            p.shutdown();
        }
    }
}
