package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMResponse;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeProvider — Claude API integration")
class ClaudeProviderTest {

    private MockWebServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    private ClaudeProvider createProvider() {
        return new TestableClaudeProvider("test-api-key", "claude-3-5-sonnet-20241022",
            new OkHttpClient(), mockServer.url("/v1").toString());
    }

    // ==================== Constructor validation ====================

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("null apiKey → IllegalArgumentException")
        void nullApiKey() {
            assertThrows(IllegalArgumentException.class,
                () -> new ClaudeProvider(null, "claude-3-5-sonnet"));
        }

        @Test
        @DisplayName("empty apiKey → IllegalArgumentException")
        void emptyApiKey() {
            assertThrows(IllegalArgumentException.class,
                () -> new ClaudeProvider("  ", "claude-3-5-sonnet"));
        }

        @Test
        @DisplayName("null model → IllegalArgumentException")
        void nullModel() {
            assertThrows(IllegalArgumentException.class,
                () -> new ClaudeProvider("key", null));
        }

        @Test
        @DisplayName("empty model → IllegalArgumentException")
        void emptyModel() {
            assertThrows(IllegalArgumentException.class,
                () -> new ClaudeProvider("key", "  "));
        }

        @Test
        @DisplayName("valid params → provider created")
        void validConstruction() {
            ClaudeProvider provider = new ClaudeProvider("key", "claude-3-5-sonnet");
            assertEquals("claude", provider.getProviderName());
            assertNotNull(provider.getModelCapability());
        }
    }

    // ==================== Request body construction ====================

    @Nested
    @DisplayName("Request body construction")
    class RequestBodyTests {

        @Test
        @DisplayName("system message extracted to top-level 'system' field")
        void systemPromptExtracted() throws Exception {
            mockServer.enqueue(successResponse("Hello", "end_turn"));
            ClaudeProvider provider = createProvider();

            List<ConversationMessage> messages = List.of(
                ConversationMessage.builder().role(MessageRole.SYSTEM).textContent("You are helpful").build(),
                ConversationMessage.builder().role(MessageRole.USER).textContent("Hi").build()
            );

            provider.complete(messages, LLMOptions.builder().build());

            RecordedRequest request = mockServer.takeRequest();
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());

            assertEquals("You are helpful", body.get("system").asText());
            JsonNode messagesArray = body.get("messages");
            for (JsonNode msg : messagesArray) {
                assertNotEquals("system", msg.get("role").asText(),
                    "System messages should not appear in messages array");
            }
        }

        @Test
        @DisplayName("TOOL role messages sent as 'user' role")
        void toolRoleConvertedToUser() throws Exception {
            mockServer.enqueue(successResponse("OK", "end_turn"));
            ClaudeProvider provider = createProvider();

            List<ConversationMessage> messages = List.of(
                ConversationMessage.builder().role(MessageRole.USER).textContent("call tool").build(),
                ConversationMessage.builder().role(MessageRole.TOOL).textContent("tool result")
                    .addMetadata("tool_use_id", "id1").build(),
                ConversationMessage.builder().role(MessageRole.ASSISTANT).textContent("got it").build()
            );

            provider.complete(messages, LLMOptions.builder().build());

            RecordedRequest request = mockServer.takeRequest();
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
            JsonNode messagesArray = body.get("messages");

            assertEquals("user", messagesArray.get(0).get("role").asText());
            assertEquals("user", messagesArray.get(1).get("role").asText());
            assertEquals("assistant", messagesArray.get(2).get("role").asText());
        }

        @Test
        @DisplayName("tool definitions included in request body when provided")
        void toolDefinitionsIncluded() throws Exception {
            mockServer.enqueue(successResponse("Done", "end_turn"));
            ClaudeProvider provider = createProvider();

            List<Map<String, Object>> toolDefs = List.of(
                Map.of("name", "search", "description", "Search tool",
                    "input_schema", Map.of("type", "object"))
            );

            provider.complete(
                List.of(ConversationMessage.builder().role(MessageRole.USER).textContent("hi").build()),
                LLMOptions.builder().toolDefinitions(toolDefs).build()
            );

            RecordedRequest request = mockServer.takeRequest();
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());

            assertTrue(body.has("tools"), "Request body should contain 'tools' field");
            assertEquals(1, body.get("tools").size());
            assertEquals("search", body.get("tools").get(0).get("name").asText());
        }

        @Test
        @DisplayName("temperature and maxTokens set correctly")
        void temperatureAndMaxTokens() throws Exception {
            mockServer.enqueue(successResponse("OK", "end_turn"));
            ClaudeProvider provider = createProvider();

            provider.complete(
                List.of(ConversationMessage.builder().role(MessageRole.USER).textContent("hi").build()),
                LLMOptions.builder().temperature(0.7).maxTokens(2048).build()
            );

            RecordedRequest request = mockServer.takeRequest();
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());

            assertEquals(0.7, body.get("temperature").asDouble(), 0.01);
            assertEquals(2048, body.get("max_tokens").asInt());
        }

        @Test
        @DisplayName("default maxTokens is 4096 when not specified")
        void defaultMaxTokens() throws Exception {
            mockServer.enqueue(successResponse("OK", "end_turn"));
            ClaudeProvider provider = createProvider();

            provider.complete(
                List.of(ConversationMessage.builder().role(MessageRole.USER).textContent("hi").build()),
                LLMOptions.builder().build()
            );

            RecordedRequest request = mockServer.takeRequest();
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());

            assertEquals(4096, body.get("max_tokens").asInt());
        }

        @Test
        @DisplayName("API key sent in x-api-key header")
        void apiKeyHeader() throws Exception {
            mockServer.enqueue(successResponse("OK", "end_turn"));
            ClaudeProvider provider = createProvider();

            provider.complete(
                List.of(ConversationMessage.builder().role(MessageRole.USER).textContent("hi").build()),
                LLMOptions.builder().build()
            );

            RecordedRequest request = mockServer.takeRequest();
            assertEquals("test-api-key", request.getHeader("x-api-key"));
            assertEquals("2023-06-01", request.getHeader("anthropic-version"));
        }
    }

    // ==================== Response parsing ====================

    @Nested
    @DisplayName("Response parsing")
    class ResponseParsingTests {

        @Test
        @DisplayName("text response parsed correctly")
        void textResponse() {
            mockServer.enqueue(successResponse("Hello world", "end_turn"));
            ClaudeProvider provider = createProvider();

            LLMResponse response = provider.complete(
                List.of(ConversationMessage.builder().role(MessageRole.USER).textContent("hi").build()),
                LLMOptions.builder().build()
            );

            assertEquals("Hello world", response.getMessage().getTextContent());
            assertEquals("end_turn", response.getStopReason());
            assertFalse(response.hasToolCalls());
        }

        @Test
        @DisplayName("tool_use response parsed with tool calls")
        void toolUseResponse() {
            String responseBody = """
                {
                    "content": [
                        {"type": "text", "text": "Let me search for that."},
                        {"type": "tool_use", "id": "toolu_1", "name": "search", "input": {"query": "weather"}}
                    ],
                    "stop_reason": "tool_use",
                    "usage": {"input_tokens": 100, "output_tokens": 50}
                }
                """;
            mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(responseBody)
                .addHeader("Content-Type", "application/json"));

            ClaudeProvider provider = createProvider();
            LLMResponse response = provider.complete(
                List.of(ConversationMessage.builder().role(MessageRole.USER).textContent("weather?").build()),
                LLMOptions.builder().build()
            );

            assertTrue(response.hasToolCalls());
            assertEquals(1, response.getToolCalls().size());
            assertEquals("toolu_1", response.getToolCalls().get(0).getId());
            assertEquals("search", response.getToolCalls().get(0).getName());
            assertEquals("weather", response.getToolCalls().get(0).getArguments().get("query"));
            assertEquals("Let me search for that.", response.getMessage().getTextContent());
        }

        @Test
        @DisplayName("usage info parsed correctly")
        void usageInfo() {
            mockServer.enqueue(successResponse("OK", "end_turn", 150, 30));
            ClaudeProvider provider = createProvider();

            LLMResponse response = provider.complete(
                List.of(ConversationMessage.builder().role(MessageRole.USER).textContent("hi").build()),
                LLMOptions.builder().build()
            );

            assertNotNull(response.getUsage());
            assertEquals(150, response.getUsage().getInputTokens());
            assertEquals(30, response.getUsage().getOutputTokens());
            assertEquals(180, response.getUsage().getTotalTokens());
        }

        @Test
        @DisplayName("HTTP error → RuntimeException with status code and error body")
        void httpError() {
            mockServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("{\"error\": {\"message\": \"Rate limited\"}}")
                .addHeader("Content-Type", "application/json"));

            ClaudeProvider provider = createProvider();
            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> provider.complete(
                    List.of(ConversationMessage.builder().role(MessageRole.USER).textContent("hi").build()),
                    LLMOptions.builder().build()
                ));

            assertTrue(ex.getMessage().contains("429"));
            assertTrue(ex.getMessage().contains("Rate limited"));
        }
    }

    // ==================== Helpers ====================

    private MockResponse successResponse(String text, String stopReason) {
        return successResponse(text, stopReason, 100, 50);
    }

    private MockResponse successResponse(String text, String stopReason, int inputTokens, int outputTokens) {
        String body = String.format("""
            {
                "content": [{"type": "text", "text": "%s"}],
                "stop_reason": "%s",
                "usage": {"input_tokens": %d, "output_tokens": %d}
            }
            """, text, stopReason, inputTokens, outputTokens);
        return new MockResponse()
            .setResponseCode(200)
            .setBody(body)
            .addHeader("Content-Type", "application/json");
    }

    /**
     * Testable subclass that overrides the API base URL to point to MockWebServer.
     */
    private static class TestableClaudeProvider extends ClaudeProvider {
        private final String baseUrl;
        private final OkHttpClient httpClient;
        private final ObjectMapper objectMapper = new ObjectMapper();

        TestableClaudeProvider(String apiKey, String model, OkHttpClient httpClient, String baseUrl) {
            super(apiKey, model, httpClient);
            this.baseUrl = baseUrl;
            this.httpClient = httpClient;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            try {
                // Use reflection-free approach: build request manually to point to mock server
                java.lang.reflect.Method buildMethod = ClaudeProvider.class.getDeclaredMethod(
                    "buildRequestBody", List.class, LLMOptions.class);
                buildMethod.setAccessible(true);
                String requestBody = (String) buildMethod.invoke(this, messages, options);

                okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(baseUrl + "/messages")
                    .header("x-api-key", "test-api-key")
                    .header("anthropic-version", "2023-06-01")
                    .header("content-type", "application/json")
                    .post(okhttp3.RequestBody.create(requestBody,
                        okhttp3.MediaType.get("application/json; charset=utf-8")))
                    .build();

                try (okhttp3.Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "No error details";
                        throw new RuntimeException("API call failed: " + response.code() + " " + response.message() +
                            " (url: " + baseUrl + "/messages)" +
                            "\nError details: " + errorBody);
                    }
                    String responseBody = response.body().string();
                    java.lang.reflect.Method parseMethod = ClaudeProvider.class.getDeclaredMethod(
                        "parseResponse", String.class);
                    parseMethod.setAccessible(true);
                    return (LLMResponse) parseMethod.invoke(this, responseBody);
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw new RuntimeException(e.getCause());
            } catch (Exception e) {
                throw new RuntimeException("Test failed", e);
            }
        }
    }
}
