package com.lightweightai.kernel.llm.claude;

import com.lightweightai.kernel.llm.*;
import okhttp3.*;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClaudeProvider streaming functionality
 */
class ClaudeProviderStreamTest {

    private ClaudeProvider provider;
    private MockOkHttpClient mockClient;

    @BeforeEach
    void setUp() {
        mockClient = new MockOkHttpClient();
        provider = new ClaudeProvider("test-api-key", "claude-3-5-sonnet-20241022", mockClient);
    }

    @Test
    void shouldHandleStreamingTextResponse() throws Exception {
        // Given: Mock streaming response with text deltas
        String sseResponse =
            "event: message_start\n" +
            "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_123\",\"role\":\"assistant\"}}\n" +
            "\n" +
            "event: content_block_start\n" +
            "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n" +
            "\n" +
            "event: content_block_delta\n" +
            "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}\n" +
            "\n" +
            "event: content_block_delta\n" +
            "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\" world\"}}\n" +
            "\n" +
            "event: content_block_stop\n" +
            "data: {\"type\":\"content_block_stop\",\"index\":0}\n" +
            "\n" +
            "event: message_stop\n" +
            "data: {\"type\":\"message_stop\"}\n" +
            "\n";

        mockClient.setResponse(200, sseResponse);

        // Track streaming events
        List<String> textDeltas = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<LLMResponse> finalResponse = new AtomicReference<>();

        LLMProvider.StreamEventHandler handler = new LLMProvider.StreamEventHandler() {
            @Override
            public void onTextDelta(String delta) {
                textDeltas.add(delta);
            }

            @Override
            public void onComplete(LLMResponse response) {
                finalResponse.set(response);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                error.printStackTrace();
                latch.countDown();
            }
        };

        // When: Stream a response
        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Test message")
                .build()
        );

        CompletableFuture<LLMResponse> future = provider.completeStream(
            messages,
            LLMOptions.builder().build(),
            handler
        );

        // Then: Should receive text deltas and final response
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(2, textDeltas.size());
        assertEquals("Hello", textDeltas.get(0));
        assertEquals(" world", textDeltas.get(1));

        assertNotNull(finalResponse.get());
        assertEquals("Hello world", finalResponse.get().getMessage().getTextContent());
    }

    @Test
    void shouldHandleStreamingToolUse() throws Exception {
        // Given: Mock streaming response with tool use
        String sseResponse =
            "event: message_start\n" +
            "data: {\"type\":\"message_start\",\"message\":{\"id\":\"msg_123\",\"role\":\"assistant\"}}\n" +
            "\n" +
            "event: content_block_start\n" +
            "data: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"call_123\",\"name\":\"add\"}}\n" +
            "\n" +
            "event: content_block_delta\n" +
            "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"a\\\":10\"}}\n" +
            "\n" +
            "event: content_block_delta\n" +
            "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\",\\\"b\\\":20}\"}}\n" +
            "\n" +
            "event: content_block_stop\n" +
            "data: {\"type\":\"content_block_stop\",\"index\":0}\n" +
            "\n" +
            "event: message_stop\n" +
            "data: {\"type\":\"message_stop\"}\n" +
            "\n";

        mockClient.setResponse(200, sseResponse);

        // Track streaming events
        List<ToolCall> toolCalls = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<LLMResponse> finalResponse = new AtomicReference<>();

        LLMProvider.StreamEventHandler handler = new LLMProvider.StreamEventHandler() {
            @Override
            public void onToolCallDelta(ToolCall toolCall) {
                toolCalls.add(toolCall);
            }

            @Override
            public void onComplete(LLMResponse response) {
                finalResponse.set(response);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                error.printStackTrace();
                latch.countDown();
            }
        };

        // When: Stream a response with tool use
        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Add 10 and 20")
                .build()
        );

        CompletableFuture<LLMResponse> future = provider.completeStream(
            messages,
            LLMOptions.builder().build(),
            handler
        );

        // Then: Should receive tool call and final response
        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals(1, toolCalls.size());
        ToolCall toolCall = toolCalls.get(0);
        assertEquals("call_123", toolCall.getId());
        assertEquals("add", toolCall.getName());
        assertEquals(10, toolCall.getArguments().get("a"));
        assertEquals(20, toolCall.getArguments().get("b"));

        assertNotNull(finalResponse.get());
        assertEquals(1, finalResponse.get().getToolCalls().size());
    }

    /**
     * Mock OkHttpClient for testing
     */
    private static class MockOkHttpClient extends OkHttpClient {
        private int statusCode = 200;
        private String responseBody = "";

        public void setResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.responseBody = body;
        }

        @Override
        public Call newCall(Request request) {
            return new MockCall(request, statusCode, responseBody);
        }
    }

    /**
     * Mock Call for testing
     */
    private static class MockCall implements Call {
        private final Request request;
        private final int statusCode;
        private final String responseBody;

        public MockCall(Request request, int statusCode, String responseBody) {
            this.request = request;
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response execute() throws IOException {
            ResponseBody body = ResponseBody.create(responseBody, MediaType.get("text/event-stream"));
            return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message("OK")
                .body(body)
                .build();
        }

        @Override
        public void enqueue(Callback callback) {
            // Not used in tests
        }

        @Override
        public void cancel() {
            // Not used in tests
        }

        @Override
        public boolean isExecuted() {
            return false;
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public Call clone() {
            return this;
        }

        @Override
        public okio.Timeout timeout() {
            return okio.Timeout.NONE;
        }
    }
}
