package com.lightweightai.web.config;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DynamicLLMProvider — hot-swap delegation and payload transmission")
class DynamicLLMProviderTransmissionTest {

    @Test
    @DisplayName("complete() delegates to current provider and passes messages + options")
    void completeDelegatesWithPayload() {
        AtomicReference<List<ConversationMessage>> capturedMessages = new AtomicReference<>();
        AtomicReference<LLMOptions> capturedOptions = new AtomicReference<>();

        LLMProvider spy = capturingProvider(capturedMessages, capturedOptions, "response-1");
        DynamicLLMProvider dynamic = new DynamicLLMProvider(spy);

        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent("hello")
                        .build()
        );
        LLMOptions options = LLMOptions.builder()
                .toolDefinitions(List.of(Map.of("name", "search")))
                .build();

        LLMResponse response = dynamic.complete(messages, options);

        assertSame(messages, capturedMessages.get());
        assertSame(options, capturedOptions.get());
        assertEquals("response-1", response.getMessage().getTextContent());
    }

    @Test
    @DisplayName("setDelegate hot-swaps the underlying provider")
    void setDelegateHotSwaps() {
        LLMProvider first = fixedProvider("first");
        LLMProvider second = fixedProvider("second");

        DynamicLLMProvider dynamic = new DynamicLLMProvider(first);
        assertEquals("first", dynamic.complete(List.of(), LLMOptions.builder().build())
                .getMessage().getTextContent());

        dynamic.setDelegate(second);
        assertEquals("second", dynamic.complete(List.of(), LLMOptions.builder().build())
                .getMessage().getTextContent());
    }

    @Test
    @DisplayName("getDelegate returns current delegate")
    void getDelegateReturnsCurrent() {
        LLMProvider initial = fixedProvider("init");
        DynamicLLMProvider dynamic = new DynamicLLMProvider(initial);

        assertSame(initial, dynamic.getDelegate());

        LLMProvider replacement = fixedProvider("new");
        dynamic.setDelegate(replacement);
        assertSame(replacement, dynamic.getDelegate());
    }

    @Test
    @DisplayName("getProviderName delegates to current provider")
    void providerNameDelegates() {
        LLMProvider provider = new StubProvider("my-provider", "text");
        DynamicLLMProvider dynamic = new DynamicLLMProvider(provider);

        assertEquals("my-provider", dynamic.getProviderName());
    }

    @Test
    @DisplayName("completeStreamReactive delegates and returns Flux")
    void reactiveStreamDelegates() {
        LLMResponse resp = LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("streamed")
                        .build())
                .stopReason("end_turn")
                .build();

        LLMProvider provider = new StubProvider("test", "streamed") {
            @Override
            public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
                return Flux.just(
                        StreamEvent.textDelta("str"),
                        StreamEvent.textDelta("eamed"),
                        StreamEvent.llmComplete(resp)
                );
            }
        };

        DynamicLLMProvider dynamic = new DynamicLLMProvider(provider);

        StepVerifier.create(dynamic.completeStreamReactive(List.of(), LLMOptions.builder().build()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.TEXT_DELTA, e.getType()))
                .assertNext(e -> assertEquals(StreamEvent.EventType.LLM_COMPLETE, e.getType()))
                .verifyComplete();
    }

    @Test
    @DisplayName("toolDefinitions in LLMOptions are transmitted to delegate (chain payload test)")
    void toolDefinitionsTransmitted() {
        AtomicReference<LLMOptions> captured = new AtomicReference<>();

        LLMProvider spy = new StubProvider("spy", "ok") {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                captured.set(options);
                return super.complete(messages, options);
            }
        };

        DynamicLLMProvider dynamic = new DynamicLLMProvider(spy);

        List<Map<String, Object>> toolDefs = List.of(
                Map.of("name", "search", "description", "Search the web"),
                Map.of("name", "read_file", "description", "Read a file")
        );
        LLMOptions opts = LLMOptions.builder().toolDefinitions(toolDefs).build();

        dynamic.complete(List.of(), opts);

        assertNotNull(captured.get());
        assertEquals(2, captured.get().getToolDefinitions().size());
        assertEquals("search", captured.get().getToolDefinitions().get(0).get("name"));
    }

    private static LLMProvider fixedProvider(String text) {
        return new StubProvider("fixed", text);
    }

    private static LLMProvider capturingProvider(
            AtomicReference<List<ConversationMessage>> msgRef,
            AtomicReference<LLMOptions> optRef,
            String responseText) {
        return new StubProvider("capturing", responseText) {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                msgRef.set(messages);
                optRef.set(options);
                return super.complete(messages, options);
            }
        };
    }

    private static class StubProvider implements LLMProvider {
        private final String name;
        private final String text;

        StubProvider(String name, String text) {
            this.name = name;
            this.text = text;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            return LLMResponse.builder()
                    .message(ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.ASSISTANT)
                            .textContent(text)
                            .build())
                    .stopReason("end_turn")
                    .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
            return CompletableFuture.completedFuture(complete(m, o));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, StreamEventHandler h) {
            LLMResponse r = complete(m, o);
            h.onComplete(r);
            return CompletableFuture.completedFuture(r);
        }

        @Override
        public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
            return Flux.just(StreamEvent.llmComplete(complete(m, o)));
        }

        @Override public ModelCapability getModelCapability() { return null; }
        @Override public String getProviderName() { return name; }
    }
}
