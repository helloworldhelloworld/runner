package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.plugin.FunctionResult;
import com.lightweightai.kernel.plugin.PluginFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for async non-blocking ToolCallingLoop
 *
 * Demonstrates the reactive programming model where dependent tasks
 * are chained using CompletableFuture without blocking threads
 */
class ToolCallingLoopAsyncTest {

    private MockLLMProvider mockProvider;
    private ToolExecutor toolExecutor;
    private ToolCallingLoop toolCallingLoop;
    private AtomicInteger executionOrder;

    @BeforeEach
    void setUp() {
        mockProvider = new MockLLMProvider();
        toolExecutor = new ToolExecutor();
        executionOrder = new AtomicInteger(0);

        // Register a simple add function
        toolExecutor.registerFunction("add", createAddFunction());

        toolCallingLoop = ToolCallingLoop.builder()
            .provider(mockProvider)
            .toolExecutor(toolExecutor)
            .maxIterations(10)
            .build();
    }

    @Test
    void shouldExecuteAsyncNonBlocking() throws Exception {
        // Given: Mock LLM that returns tool call, then final response
        mockProvider.addResponse(createToolCallResponse("add", makeMap("a", 10, "b", 20)));
        mockProvider.addResponse(createTextResponse("The sum is 30"));

        List<ConversationMessage> messages = Collections.singletonList(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("What is 10 + 20?")
                .build()
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<LLMResponse> finalResponse = new AtomicReference<>();
        AtomicReference<String> executingThread = new AtomicReference<>();

        // When: Execute async (non-blocking)
        CompletableFuture<LLMResponse> future = toolCallingLoop.executeWithToolsAsync(
            messages,
            LLMOptions.builder().build()
        );

        // Verify main thread is NOT blocked
        assertFalse(future.isDone(), "Future should not be done immediately");
        executingThread.set(Thread.currentThread().getName());

        // Chain async operations (this is the reactive pattern)
        future.thenAccept(response -> {
            finalResponse.set(response);

            // Verify we're potentially on a different thread (async execution)
            String asyncThread = Thread.currentThread().getName();
            System.out.println("Main thread: " + executingThread.get());
            System.out.println("Async thread: " + asyncThread);

            latch.countDown();
        }).exceptionally(error -> {
            error.printStackTrace();
            latch.countDown();
            return null;
        });

        // Then: Wait for async completion
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Async execution should complete");

        assertNotNull(finalResponse.get());
        assertEquals("The sum is 30", finalResponse.get().getMessage().getTextContent());
    }

    @Test
    void shouldChainMultipleDependentAsyncOperations() throws Exception {
        // Given: Multi-turn conversation with dependent operations
        // Turn 1: LLM wants to call "add"
        mockProvider.addResponse(createToolCallResponse("add", makeMap("a", 5, "b", 3)));
        // Turn 2: LLM wants to call "add" again based on previous result
        mockProvider.addResponse(createToolCallResponse("add", makeMap("a", 8, "b", 2)));
        // Turn 3: Final response
        mockProvider.addResponse(createTextResponse("The final result is 10"));

        List<ConversationMessage> messages = Collections.singletonList(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Calculate 5+3, then add 2 to the result")
                .build()
        );

        // Track execution order to verify async chaining
        List<String> executionLog = Collections.synchronizedList(new ArrayList<>());

        // When: Execute async
        CompletableFuture<LLMResponse> future = toolCallingLoop.executeWithToolsAsync(
            messages,
            LLMOptions.builder().build()
        );

        // Add logging to track execution
        future.thenAccept(response -> {
            executionLog.add("Final response received: " + response.getMessage().getTextContent());
        });

        // Then: Wait and verify
        LLMResponse result = future.get(5, TimeUnit.SECONDS);

        assertEquals("The final result is 10", result.getMessage().getTextContent());
        assertEquals(3, mockProvider.getCallCount(), "Should have made 3 LLM calls");
    }

    @Test
    void shouldHandleAsyncErrorsGracefully() throws Exception {
        // Given: Provider that will fail
        mockProvider.addResponse(createToolCallResponse("add", makeMap("a", 10, "b", 20)));
        mockProvider.setShouldFail(true);

        List<ConversationMessage> messages = Collections.singletonList(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Test")
                .build()
        );

        // When: Execute async
        CompletableFuture<LLMResponse> future = toolCallingLoop.executeWithToolsAsync(
            messages,
            LLMOptions.builder().build()
        );

        // Then: Should handle error in async chain
        try {
            future.get(5, TimeUnit.SECONDS);
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof RuntimeException);
            assertTrue(e.getCause().getMessage().contains("Tool calling loop failed"));
        }
    }

    @Test
    void shouldSupportCompositionWithOtherAsyncOperations() throws Exception {
        // This test demonstrates the key advantage: composability
        // You can combine this with other async operations

        mockProvider.addResponse(createTextResponse("Hello"));

        List<ConversationMessage> messages = Collections.singletonList(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Say hello")
                .build()
        );

        // When: Compose with other async operations
        CompletableFuture<String> composedResult = toolCallingLoop
            .executeWithToolsAsync(messages, LLMOptions.builder().build())
            .thenApply(response -> response.getMessage().getTextContent())
            .thenApply(String::toUpperCase)
            .thenCompose(text -> CompletableFuture.supplyAsync(() -> {
                // Simulate another async operation (e.g., database save)
                return "Saved: " + text;
            }));

        // Then: Entire chain is non-blocking
        String result = composedResult.get(5, TimeUnit.SECONDS);
        assertEquals("Saved: HELLO", result);
    }

    @Test
    void shouldNotBlockThreadsInAsyncExecution() throws Exception {
        // This test verifies the non-blocking nature

        mockProvider.addResponse(createToolCallResponse("add", makeMap("a", 1, "b", 2)));
        mockProvider.addResponse(createTextResponse("Result: 3"));
        mockProvider.setDelay(100); // Simulate network latency

        List<ConversationMessage> messages = Collections.singletonList(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Calculate 1+2")
                .build()
        );

        long startTime = System.currentTimeMillis();

        // When: Start async execution
        CompletableFuture<LLMResponse> future = toolCallingLoop.executeWithToolsAsync(
            messages,
            LLMOptions.builder().build()
        );

        // Verify that we return immediately (non-blocking)
        long callTime = System.currentTimeMillis() - startTime;
        assertTrue(callTime < 50, "Should return immediately, took: " + callTime + "ms");

        // The actual work happens asynchronously
        LLMResponse result = future.get(5, TimeUnit.SECONDS);

        long totalTime = System.currentTimeMillis() - startTime;
        assertTrue(totalTime >= 100, "Total execution should include delay time");

        assertEquals("Result: 3", result.getMessage().getTextContent());
    }

    // Helper methods

    private PluginFunction createAddFunction() {
        return new PluginFunction() {
            @Override
            public String getName() {
                return "add";
            }

            @Override
            public String getDescription() {
                return "Add two numbers";
            }

            @Override
            public List<com.lightweightai.kernel.plugin.FunctionParameter> getParameters() {
                return Collections.emptyList();
            }

            @Override
            public FunctionResult execute(Map<String, Object> input) {
                int a = (Integer) input.get("a");
                int b = (Integer) input.get("b");
                return FunctionResult.success(String.valueOf(a + b));
            }

            @Override
            public Map<String, Object> toJsonSchema() {
                return new HashMap<String, Object>() {{ put("name", "add"); put("description", "Add two numbers"); }};
            }
        };
    }

    private LLMResponse createToolCallResponse(String toolName, Map<String, Object> args) {
        ToolCall toolCall = new ToolCall("call_" + executionOrder.incrementAndGet(), toolName, args);

        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("")
                .build())
            .toolCalls(Collections.singletonList(toolCall))
            .build();
    }

    private LLMResponse createTextResponse(String text) {
        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text)
                .build())
            .build();
    }

    /**
     * Mock LLM Provider for testing async behavior
     */
    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> makeMap(Object... keyValues) {
        Map<K, V> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((K) keyValues[i], (V) keyValues[i + 1]);
        }
        return map;
    }

    private static class MockLLMProvider implements LLMProvider {
        private final Queue<LLMResponse> responses = new LinkedList<>();
        private boolean shouldFail = false;
        private int callCount = 0;
        private long delay = 0;

        public void addResponse(LLMResponse response) {
            responses.add(response);
        }

        public void setShouldFail(boolean fail) {
            this.shouldFail = fail;
        }

        public void setDelay(long delayMs) {
            this.delay = delayMs;
        }

        public int getCallCount() {
            return callCount;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            throw new UnsupportedOperationException("Use completeAsync for testing");
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(
            List<ConversationMessage> messages,
            LLMOptions options
        ) {
            return CompletableFuture.supplyAsync(() -> {
                callCount++;

                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                if (shouldFail) {
                    throw new RuntimeException("Simulated LLM failure");
                }

                LLMResponse response = responses.poll();
                if (response == null) {
                    throw new RuntimeException("No more mock responses available");
                }
                return response;
            });
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(
            List<ConversationMessage> messages,
            LLMOptions options,
            StreamEventHandler handler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModelCapability getModelCapability() {
            return null;
        }

        @Override
        public String getProviderName() {
            return "mock";
        }
    }
}
