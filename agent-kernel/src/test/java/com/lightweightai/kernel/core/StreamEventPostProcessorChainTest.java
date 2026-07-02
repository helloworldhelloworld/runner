package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.postprocess.StreamPostProcessorPipeline;
import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests StreamEvent flow from a real ToolCallingLoop through StreamPostProcessorPipeline.
 *
 * Covers the critical gap: no test exercises events from a real ToolCallingLoop
 * flowing through a real StreamPostProcessorPipeline. Previous tests either:
 * - Test ToolCallingLoop in isolation (no post-processors)
 * - Test PostProcessorPipeline with mock events (no real ToolCallingLoop)
 *
 * This test verifies event ordering, type correctness, and that post-processors
 * can transform/filter events produced by the actual tool calling flow.
 */
@DisplayName("StreamEvent: ToolCallingLoop → PostProcessorPipeline chain")
class StreamEventPostProcessorChainTest {

    @Test
    @DisplayName("LLM_COMPLETE from ToolCallingLoop flows through PostProcessorPipeline")
    void llmCompleteFlowsThroughPostProcessor() {
        LLMProvider provider = endTurnProvider("hello");
        ToolRegistry registry = new ToolRegistry();
        ToolExecutor executor = new ToolExecutor(registry);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(executor)
                .maxIterations(5)
                .build();

        // Post-processor that appends a POST_PROCESS_DATA event after LLM_COMPLETE
        StreamPostProcessorPipeline pipeline = StreamPostProcessorPipeline.builder()
                .add(flux -> flux.concatWith(
                        Flux.just(StreamEvent.postProcessData("enrichment", Map.of("source", "test")))
                ))
                .build();

        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("test")
                .build()
        );

        Flux<StreamEvent> stream = loop.executeWithToolsReactive(messages, LLMOptions.builder().build());
        Flux<StreamEvent> processed = stream.transform(pipeline);

        List<StreamEvent> events = processed.collectList().block();

        assertNotNull(events);
        assertFalse(events.isEmpty());

        // Should have LLM_COMPLETE from ToolCallingLoop
        boolean hasLlmComplete = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE);
        assertTrue(hasLlmComplete, "LLM_COMPLETE should survive post-processing");

        // Should have POST_PROCESS_DATA from pipeline
        boolean hasPostProcess = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.POST_PROCESS_DATA);
        assertTrue(hasPostProcess, "Pipeline should have appended POST_PROCESS_DATA");

        // POST_PROCESS_DATA should come AFTER LLM_COMPLETE
        int llmCompleteIdx = -1;
        int postProcessIdx = -1;
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).getType() == StreamEvent.EventType.LLM_COMPLETE) llmCompleteIdx = i;
            if (events.get(i).getType() == StreamEvent.EventType.POST_PROCESS_DATA) postProcessIdx = i;
        }
        assertTrue(postProcessIdx > llmCompleteIdx,
                "POST_PROCESS_DATA should come after LLM_COMPLETE");
    }

    @Test
    @DisplayName("Tool call events (TOOL_CALL_START, TOOL_RESULT) flow through PostProcessor")
    void toolCallEventsFlowThroughPostProcessor() {
        AtomicInteger callCount = new AtomicInteger(0);

        LLMProvider provider = new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> msgs, LLMOptions opts) {
                throw new UnsupportedOperationException();
            }
            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> msgs, LLMOptions opts) {
                throw new UnsupportedOperationException();
            }
            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> msgs, LLMOptions opts, StreamEventHandler h) {
                throw new UnsupportedOperationException();
            }
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> msgs, LLMOptions opts) {
                int call = callCount.incrementAndGet();
                if (call == 1) {
                    ToolCall tc = new ToolCall("call_1", "echo", Map.of("text", "hi"));
                    LLMResponse resp = LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent("")
                                    .build())
                            .toolCalls(List.of(tc))
                            .stopReason("tool_use")
                            .build();
                    return Flux.just(StreamEvent.llmComplete(resp));
                }
                LLMResponse resp = LLMResponse.builder()
                        .message(ConversationMessage.builder()
                                .role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("done")
                                .build())
                        .stopReason("end_turn")
                        .build();
                return Flux.just(StreamEvent.llmComplete(resp));
            }
            @Override
            public ModelCapability getModelCapability() { return null; }
            @Override
            public String getProviderName() { return "tool-test"; }
        };

        ToolRegistry registry = new ToolRegistry();
        registry.register(echoTool());
        ToolExecutor executor = new ToolExecutor(registry);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(executor)
                .maxIterations(5)
                .build();

        // Post-processor that counts tool events
        List<StreamEvent.EventType> observedTypes = Collections.synchronizedList(new ArrayList<>());
        StreamPostProcessorPipeline pipeline = StreamPostProcessorPipeline.builder()
                .add(flux -> flux.doOnNext(e -> observedTypes.add(e.getType())))
                .build();

        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("call echo")
                .build()
        );

        Flux<StreamEvent> processed = loop.executeWithToolsReactive(messages, LLMOptions.builder().build())
                .transform(pipeline);

        processed.collectList().block();

        // Verify all tool-related event types were seen by the post-processor
        assertTrue(observedTypes.contains(StreamEvent.EventType.TOOL_CALL_START),
                "Post-processor should see TOOL_CALL_START");
        assertTrue(observedTypes.contains(StreamEvent.EventType.TOOL_RESULT),
                "Post-processor should see TOOL_RESULT");
        assertTrue(observedTypes.contains(StreamEvent.EventType.LLM_COMPLETE),
                "Post-processor should see LLM_COMPLETE");
    }

    @Test
    @DisplayName("Post-processor can filter out specific event types from ToolCallingLoop")
    void postProcessorCanFilterEvents() {
        LLMProvider provider = endTurnProvider("filtered");
        ToolRegistry registry = new ToolRegistry();
        ToolExecutor executor = new ToolExecutor(registry);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(executor)
                .maxIterations(5)
                .build();

        // Post-processor that filters out LLM_COMPLETE events
        StreamPostProcessorPipeline pipeline = StreamPostProcessorPipeline.builder()
                .add(flux -> flux.filter(e -> e.getType() != StreamEvent.EventType.LLM_COMPLETE))
                .build();

        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("test filter")
                .build()
        );

        List<StreamEvent> events = loop.executeWithToolsReactive(messages, LLMOptions.builder().build())
                .transform(pipeline)
                .collectList().block();

        assertNotNull(events);
        boolean hasLlmComplete = events.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE);
        assertFalse(hasLlmComplete, "LLM_COMPLETE should have been filtered out");
    }

    // ==================== Helpers ====================

    private static LLMProvider endTurnProvider(String text) {
        return new LLMProvider() {
            @Override
            public LLMResponse complete(List<ConversationMessage> msgs, LLMOptions opts) {
                return LLMResponse.builder()
                        .message(ConversationMessage.builder()
                                .role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent(text)
                                .build())
                        .stopReason("end_turn")
                        .build();
            }
            @Override
            public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> msgs, LLMOptions opts) {
                return CompletableFuture.completedFuture(complete(msgs, opts));
            }
            @Override
            public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> msgs, LLMOptions opts, StreamEventHandler h) {
                LLMResponse r = complete(msgs, opts);
                h.onComplete(r);
                return CompletableFuture.completedFuture(r);
            }
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> msgs, LLMOptions opts) {
                return Flux.just(StreamEvent.llmComplete(complete(msgs, opts)));
            }
            @Override
            public ModelCapability getModelCapability() { return null; }
            @Override
            public String getProviderName() { return "end-turn-test"; }
        };
    }

    private static Tool echoTool() {
        return new Tool() {
            @Override public String getName() { return "echo"; }
            @Override public String getDescription() { return "Echo tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("echo: " + args.getOrDefault("text", ""));
            }
        };
    }
}
