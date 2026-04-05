package com.lightweightai.web.config;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("DynamicLLMProvider - delegating provider with hot-swap support")
class DynamicLLMProviderTest {

    private LLMProvider initialProvider;
    private LLMProvider secondProvider;
    private LLMOptions options;
    private DynamicLLMProvider dynamicProvider;
    private List<ConversationMessage> messages;

    @BeforeEach
    void setUp() {
        initialProvider = mock(LLMProvider.class);
        secondProvider = mock(LLMProvider.class);
        options = mock(LLMOptions.class);
        dynamicProvider = new DynamicLLMProvider(initialProvider);
        messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("hello")
                .build()
        );
    }

    @Test
    @DisplayName("complete() delegates to initial provider")
    void completeDelegatesToInitialProvider() {
        LLMResponse expected = mock(LLMResponse.class);
        when(initialProvider.complete(messages, options)).thenReturn(expected);

        LLMResponse result = dynamicProvider.complete(messages, options);

        assertSame(expected, result);
        verify(initialProvider).complete(messages, options);
    }

    @Test
    @DisplayName("completeAsync() delegates to initial provider")
    void completeAsyncDelegatesToInitialProvider() {
        CompletableFuture<LLMResponse> expected = CompletableFuture.completedFuture(mock(LLMResponse.class));
        when(initialProvider.completeAsync(messages, options)).thenReturn(expected);

        CompletableFuture<LLMResponse> result = dynamicProvider.completeAsync(messages, options);

        assertSame(expected, result);
        verify(initialProvider).completeAsync(messages, options);
    }

    @Test
    @DisplayName("completeStream() delegates to initial provider")
    void completeStreamDelegatesToInitialProvider() {
        LLMProvider.StreamEventHandler handler = mock(LLMProvider.StreamEventHandler.class);
        CompletableFuture<LLMResponse> expected = CompletableFuture.completedFuture(mock(LLMResponse.class));
        when(initialProvider.completeStream(messages, options, handler)).thenReturn(expected);

        CompletableFuture<LLMResponse> result = dynamicProvider.completeStream(messages, options, handler);

        assertSame(expected, result);
        verify(initialProvider).completeStream(messages, options, handler);
    }

    @Test
    @DisplayName("completeStreamReactive() delegates to initial provider")
    void completeStreamReactiveDelegatesToInitialProvider() {
        Flux<StreamEvent> expected = Flux.empty();
        when(initialProvider.completeStreamReactive(messages, options)).thenReturn(expected);

        Flux<StreamEvent> result = dynamicProvider.completeStreamReactive(messages, options);

        assertSame(expected, result);
        verify(initialProvider).completeStreamReactive(messages, options);
    }

    @Test
    @DisplayName("setDelegate() hot-swaps the underlying provider")
    void setDelegateHotSwapsProvider() {
        LLMResponse firstResponse = mock(LLMResponse.class);
        LLMResponse secondResponse = mock(LLMResponse.class);
        when(initialProvider.complete(messages, options)).thenReturn(firstResponse);
        when(secondProvider.complete(messages, options)).thenReturn(secondResponse);

        assertSame(firstResponse, dynamicProvider.complete(messages, options));

        dynamicProvider.setDelegate(secondProvider);

        assertSame(secondResponse, dynamicProvider.complete(messages, options));

        verify(initialProvider).complete(messages, options);
        verify(secondProvider).complete(messages, options);
    }

    @Test
    @DisplayName("getDelegate() returns current provider")
    void getDelegateReturnsCurrentProvider() {
        assertSame(initialProvider, dynamicProvider.getDelegate());

        dynamicProvider.setDelegate(secondProvider);
        assertSame(secondProvider, dynamicProvider.getDelegate());
    }

    @Test
    @DisplayName("getModelCapability() delegates to current provider")
    void getModelCapabilityDelegatesToProvider() {
        ModelCapability capability = mock(ModelCapability.class);
        when(initialProvider.getModelCapability()).thenReturn(capability);

        ModelCapability result = dynamicProvider.getModelCapability();

        assertSame(capability, result);
        verify(initialProvider).getModelCapability();
    }

    @Test
    @DisplayName("getProviderName() delegates to current provider")
    void getProviderNameDelegatesToProvider() {
        when(initialProvider.getProviderName()).thenReturn("claude");

        String result = dynamicProvider.getProviderName();

        assertEquals("claude", result);
        verify(initialProvider).getProviderName();
    }
}
