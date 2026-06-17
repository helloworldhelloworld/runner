package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission tests for ResilientLLMProvider.
 *
 * Existing ResilientLLMProviderTest covers retry logic and health status.
 * This test focuses on payload fidelity: options and messages must pass through
 * unmodified to the delegate.
 */
@DisplayName("ResilientLLMProvider - Payload Transmission")
class ResilientLLMProviderTransmissionTest {

    @Test
    @DisplayName("complete() passes LLMOptions (toolDefinitions, temperature) to delegate")
    void completeShouldPassOptionsToDelegate() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        ResilientLLMProvider resilient = new ResilientLLMProvider(spy, 2, 10);

        List<Map<String, Object>> toolDefs = List.of(
            Map.of("name", "weather", "description", "Get weather")
        );
        LLMOptions options = LLMOptions.builder()
                .toolDefinitions(toolDefs)
                .temperature(0.7)
                .maxTokens(1024)
                .build();

        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("hello")
                .build()
        );

        resilient.complete(messages, options);

        LLMOptions captured = spy.lastOptions();
        assertNotNull(captured, "delegate should receive options");
        assertEquals(toolDefs, captured.getToolDefinitions(),
                "toolDefinitions must pass through unmodified");
        assertEquals(0.7, captured.getTemperature(), 0.001,
                "temperature must pass through");
        assertEquals(1024, captured.getMaxTokens(),
                "maxTokens must pass through");

        List<ConversationMessage> capturedMsgs = spy.lastMessages();
        assertEquals(1, capturedMsgs.size());
        assertEquals("hello", capturedMsgs.get(0).getTextContent());
    }

    @Test
    @DisplayName("completeAsync() passes options to delegate")
    void completeAsyncShouldPassOptions() throws Exception {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        ResilientLLMProvider resilient = new ResilientLLMProvider(spy, 2, 10);

        List<Map<String, Object>> toolDefs = List.of(
            Map.of("name", "calc", "description", "Calculate")
        );
        LLMOptions options = LLMOptions.builder()
                .toolDefinitions(toolDefs)
                .build();

        CompletableFuture<LLMResponse> future = resilient.completeAsync(
            List.of(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("2+2")
                .build()),
            options);

        future.get();

        assertNotNull(spy.lastOptions());
        assertEquals(toolDefs, spy.lastOptions().getToolDefinitions());
    }

    @Test
    @DisplayName("completeStreamReactive() passes options to delegate")
    void completeStreamReactiveShouldPassOptions() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("streamed");
        ResilientLLMProvider resilient = new ResilientLLMProvider(spy, 2, 10);

        List<Map<String, Object>> toolDefs = List.of(
            Map.of("name", "search", "description", "Search web")
        );
        LLMOptions options = LLMOptions.builder()
                .toolDefinitions(toolDefs)
                .temperature(0.5)
                .build();

        StepVerifier.create(
            resilient.completeStreamReactive(
                List.of(ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent("search for cats")
                    .build()),
                options))
            .expectNextMatches(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE)
            .verifyComplete();

        assertEquals(toolDefs, spy.lastOptions().getToolDefinitions());
        assertEquals(0.5, spy.lastOptions().getTemperature(), 0.001);
    }

    @Test
    @DisplayName("retry preserves original options across attempts")
    void retryShouldPreserveOptionsAcrossAttempts() {
        AtomicInteger attempts = new AtomicInteger(0);
        AtomicReference<LLMOptions> firstAttemptOpts = new AtomicReference<>();
        AtomicReference<LLMOptions> secondAttemptOpts = new AtomicReference<>();

        LLMProvider delegate = new ResilientLLMProviderTest.StubLLMProvider("retry-opts") {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                int attempt = attempts.incrementAndGet();
                if (attempt == 1) {
                    firstAttemptOpts.set(options);
                    throw new RuntimeException("Server error: 500");
                }
                secondAttemptOpts.set(options);
                return ResilientLLMProviderTest.createResponse("ok");
            }
        };

        ResilientLLMProvider resilient = new ResilientLLMProvider(delegate, 2, 10);

        List<Map<String, Object>> toolDefs = List.of(
            Map.of("name", "tool1", "description", "desc1")
        );
        LLMOptions options = LLMOptions.builder()
                .toolDefinitions(toolDefs)
                .temperature(0.9)
                .build();

        resilient.complete(List.of(), options);

        assertSame(firstAttemptOpts.get(), secondAttemptOpts.get(),
                "same LLMOptions object should be passed on retry");
        assertEquals(toolDefs, secondAttemptOpts.get().getToolDefinitions());
    }
}
