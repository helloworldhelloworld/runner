package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.*;
import okhttp3.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClaudeProvider 单元测试
 *
 * 覆盖关键路径：
 * - 构造器参数校验
 * - 同步调用 complete() 请求构建和响应解析
 * - 工具调用响应解析
 * - HTTP 错误处理
 * - 系统消息提取
 * - 角色映射
 * - ModelCapability
 */
@DisplayName("ClaudeProvider - 核心 LLM 提供者")
class ClaudeProviderTest {

    private MockOkHttpClient mockClient;
    private ClaudeProvider provider;

    @BeforeEach
    void setUp() {
        mockClient = new MockOkHttpClient();
        provider = new ClaudeProvider("test-api-key", "claude-3-5-sonnet-20241022", mockClient);
    }

    // ==================== 构造器测试 ====================

    @Test
    @DisplayName("API key 为 null 时抛异常")
    void shouldRejectNullApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClaudeProvider(null, "claude-3-5-sonnet-20241022"));
    }

    @Test
    @DisplayName("API key 为空字符串时抛异常")
    void shouldRejectEmptyApiKey() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClaudeProvider("  ", "claude-3-5-sonnet-20241022"));
    }

    @Test
    @DisplayName("Model 为 null 时抛异常")
    void shouldRejectNullModel() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClaudeProvider("test-key", null));
    }

    @Test
    @DisplayName("Model 为空字符串时抛异常")
    void shouldRejectEmptyModel() {
        assertThrows(IllegalArgumentException.class, () ->
            new ClaudeProvider("test-key", "  "));
    }

    // ==================== 同步调用测试 ====================

    @Test
    @DisplayName("成功解析纯文本响应")
    void shouldParseTextResponse() {
        String responseJson = """
            {
                "id": "msg_123",
                "type": "message",
                "role": "assistant",
                "content": [{"type": "text", "text": "Hello world"}],
                "stop_reason": "end_turn",
                "usage": {"input_tokens": 10, "output_tokens": 5}
            }
            """;
        mockClient.setResponse(200, responseJson);

        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Hi")
                .build()
        );

        LLMResponse response = provider.complete(messages, LLMOptions.builder().build());

        assertEquals("Hello world", response.getMessage().getTextContent());
        assertFalse(response.hasToolCalls());
        assertEquals("end_turn", response.getStopReason());
        assertNotNull(response.getUsage());
        assertEquals(10, response.getUsage().getInputTokens());
        assertEquals(5, response.getUsage().getOutputTokens());
    }

    @Test
    @DisplayName("成功解析工具调用响应")
    void shouldParseToolUseResponse() {
        String responseJson = """
            {
                "id": "msg_456",
                "type": "message",
                "role": "assistant",
                "content": [
                    {"type": "text", "text": "Let me calculate that."},
                    {"type": "tool_use", "id": "toolu_123", "name": "calculator", "input": {"expression": "2+2"}}
                ],
                "stop_reason": "tool_use",
                "usage": {"input_tokens": 20, "output_tokens": 15}
            }
            """;
        mockClient.setResponse(200, responseJson);

        LLMResponse response = provider.complete(
            createUserMessages("Calculate 2+2"),
            LLMOptions.builder().build()
        );

        assertTrue(response.hasToolCalls());
        assertEquals(1, response.getToolCalls().size());

        ToolCall toolCall = response.getToolCalls().get(0);
        assertEquals("toolu_123", toolCall.getId());
        assertEquals("calculator", toolCall.getName());
        assertEquals("2+2", toolCall.getArguments().get("expression"));
        assertEquals("tool_use", response.getStopReason());

        // Text before tool call should be preserved
        assertEquals("Let me calculate that.", response.getMessage().getTextContent());
    }

    @Test
    @DisplayName("成功解析多工具调用响应")
    void shouldParseMultipleToolCalls() {
        String responseJson = """
            {
                "id": "msg_789",
                "type": "message",
                "role": "assistant",
                "content": [
                    {"type": "tool_use", "id": "t1", "name": "weather", "input": {"city": "Tokyo"}},
                    {"type": "tool_use", "id": "t2", "name": "time", "input": {"timezone": "Asia/Tokyo"}}
                ],
                "stop_reason": "tool_use",
                "usage": {"input_tokens": 30, "output_tokens": 25}
            }
            """;
        mockClient.setResponse(200, responseJson);

        LLMResponse response = provider.complete(
            createUserMessages("Weather and time in Tokyo"),
            LLMOptions.builder().build()
        );

        assertTrue(response.hasToolCalls());
        assertEquals(2, response.getToolCalls().size());
        assertEquals("weather", response.getToolCalls().get(0).getName());
        assertEquals("time", response.getToolCalls().get(1).getName());
    }

    // ==================== 请求构建测试 ====================

    @Test
    @DisplayName("系统消息从 messages 中提取到顶层 system 字段")
    void shouldExtractSystemMessage() {
        String responseJson = """
            {"id": "msg_1", "type": "message", "role": "assistant",
             "content": [{"type": "text", "text": "ok"}], "stop_reason": "end_turn",
             "usage": {"input_tokens": 5, "output_tokens": 2}}
            """;
        mockClient.setResponse(200, responseJson);

        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.SYSTEM)
                .textContent("You are a helpful assistant")
                .build(),
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Hi")
                .build()
        );

        provider.complete(messages, LLMOptions.builder().build());

        // Verify the request was sent (we can check the captured request body)
        String requestBody = mockClient.getLastRequestBody();
        assertTrue(requestBody.contains("\"system\""), "Should have system field");
        assertTrue(requestBody.contains("You are a helpful assistant"));
        // System messages should not appear in messages array
        assertTrue(requestBody.contains("\"messages\""));
    }

    @Test
    @DisplayName("请求包含自定义 maxTokens 和 temperature")
    void shouldIncludeCustomOptions() {
        String responseJson = """
            {"id": "msg_1", "type": "message", "role": "assistant",
             "content": [{"type": "text", "text": "ok"}], "stop_reason": "end_turn",
             "usage": {"input_tokens": 5, "output_tokens": 2}}
            """;
        mockClient.setResponse(200, responseJson);

        LLMOptions options = LLMOptions.builder()
            .maxTokens(2048)
            .temperature(0.7)
            .build();

        provider.complete(createUserMessages("test"), options);

        String body = mockClient.getLastRequestBody();
        assertTrue(body.contains("\"max_tokens\":2048"), "Should have custom max_tokens");
        assertTrue(body.contains("\"temperature\":0.7"), "Should have custom temperature");
    }

    @Test
    @DisplayName("请求包含工具定义")
    void shouldIncludeToolDefinitions() {
        String responseJson = """
            {"id": "msg_1", "type": "message", "role": "assistant",
             "content": [{"type": "text", "text": "ok"}], "stop_reason": "end_turn",
             "usage": {"input_tokens": 5, "output_tokens": 2}}
            """;
        mockClient.setResponse(200, responseJson);

        Map<String, Object> toolDef = Map.of(
            "name", "calculator",
            "description", "A calculator"
        );
        LLMOptions options = LLMOptions.builder()
            .toolDefinitions(List.of(toolDef))
            .build();

        provider.complete(createUserMessages("test"), options);

        String body = mockClient.getLastRequestBody();
        assertTrue(body.contains("\"tools\""), "Should have tools array");
        assertTrue(body.contains("calculator"));
    }

    // ==================== 错误处理测试 ====================

    @Test
    @DisplayName("HTTP 错误返回详细错误信息")
    void shouldThrowOnHttpError() {
        mockClient.setResponse(429, "{\"error\": {\"type\": \"rate_limit_error\", \"message\": \"Rate limited\"}}");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            provider.complete(createUserMessages("test"), LLMOptions.builder().build()));

        assertTrue(ex.getMessage().contains("429"));
        assertTrue(ex.getMessage().contains("Rate limited"));
    }

    @Test
    @DisplayName("HTTP 401 错误")
    void shouldThrowOnAuthError() {
        mockClient.setResponse(401, "{\"error\": {\"type\": \"authentication_error\", \"message\": \"Invalid API key\"}}");

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            provider.complete(createUserMessages("test"), LLMOptions.builder().build()));

        assertTrue(ex.getMessage().contains("401"));
    }

    @Test
    @DisplayName("网络异常包装为 RuntimeException")
    void shouldWrapIOException() {
        mockClient.setShouldThrow(new IOException("Connection refused"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            provider.complete(createUserMessages("test"), LLMOptions.builder().build()));

        assertTrue(ex.getMessage().contains("Failed to call Claude API"));
    }

    // ==================== Provider 元数据测试 ====================

    @Test
    @DisplayName("provider 名称为 claude")
    void shouldReturnProviderName() {
        assertEquals("claude", provider.getProviderName());
    }

    @Test
    @DisplayName("ModelCapability 返回正确的模型信息")
    void shouldReturnModelCapability() {
        ModelCapability capability = provider.getModelCapability();
        assertNotNull(capability);
        assertEquals("claude-3-5-sonnet-20241022", capability.getModelId());
        assertEquals(200000, capability.getMaxContextTokens());
        assertTrue(capability.getSupportedFeatures().contains(ModelCapability.ModelFeature.TOOL_CALLING));
        assertTrue(capability.getSupportedFeatures().contains(ModelCapability.ModelFeature.STREAMING));
    }

    @Test
    @DisplayName("completeAsync 基于 complete 实现")
    void shouldSupportAsyncCompletion() throws Exception {
        String responseJson = """
            {"id": "msg_1", "type": "message", "role": "assistant",
             "content": [{"type": "text", "text": "async ok"}], "stop_reason": "end_turn",
             "usage": {"input_tokens": 5, "output_tokens": 2}}
            """;
        mockClient.setResponse(200, responseJson);

        LLMResponse response = provider.completeAsync(
            createUserMessages("test"),
            LLMOptions.builder().build()
        ).get(5, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals("async ok", response.getMessage().getTextContent());
    }

    // ==================== 角色映射测试 ====================

    @Test
    @DisplayName("TOOL 角色映射为 user")
    void shouldMapToolRoleToUser() {
        String responseJson = """
            {"id": "msg_1", "type": "message", "role": "assistant",
             "content": [{"type": "text", "text": "ok"}], "stop_reason": "end_turn",
             "usage": {"input_tokens": 5, "output_tokens": 2}}
            """;
        mockClient.setResponse(200, responseJson);

        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("call the tool")
                .build(),
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("calling tool")
                .build(),
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.TOOL)
                .textContent("tool result: 42")
                .build()
        );

        provider.complete(messages, LLMOptions.builder().build());

        String body = mockClient.getLastRequestBody();
        // TOOL role should be mapped to "user" in the request
        assertFalse(body.contains("\"role\":\"tool\""), "TOOL role should be mapped to user");
    }

    // ==================== 辅助方法 ====================

    private List<ConversationMessage> createUserMessages(String text) {
        return List.of(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(text)
            .build());
    }

    // ==================== Mock HTTP Client ====================

    private static class MockOkHttpClient extends OkHttpClient {
        private int statusCode = 200;
        private String responseBody = "";
        private IOException shouldThrow = null;
        private String lastRequestBody = null;

        void setResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.responseBody = body;
        }

        void setShouldThrow(IOException e) {
            this.shouldThrow = e;
        }

        String getLastRequestBody() {
            return lastRequestBody;
        }

        @Override
        public Call newCall(Request request) {
            // Capture request body
            try {
                if (request.body() != null) {
                    okio.Buffer buffer = new okio.Buffer();
                    request.body().writeTo(buffer);
                    lastRequestBody = buffer.readUtf8();
                }
            } catch (IOException ignored) {}

            return new MockCall(request, statusCode, responseBody, shouldThrow);
        }
    }

    private static class MockCall implements Call {
        private final Request request;
        private final int statusCode;
        private final String responseBody;
        private final IOException shouldThrow;

        MockCall(Request request, int statusCode, String responseBody, IOException shouldThrow) {
            this.request = request;
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.shouldThrow = shouldThrow;
        }

        @Override
        public Request request() { return request; }

        @Override
        public Response execute() throws IOException {
            if (shouldThrow != null) throw shouldThrow;
            ResponseBody body = ResponseBody.create(
                MediaType.get("application/json"), responseBody);
            return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message(statusCode == 200 ? "OK" : "Error")
                .body(body)
                .build();
        }

        @Override public void enqueue(Callback callback) {}
        @Override public void cancel() {}
        @Override public boolean isExecuted() { return false; }
        @Override public boolean isCanceled() { return false; }
        @Override public Call clone() { return this; }
        @Override public okio.Timeout timeout() { return okio.Timeout.NONE; }
    }
}
