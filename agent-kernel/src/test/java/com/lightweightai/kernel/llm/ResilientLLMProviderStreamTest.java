package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResilientLLMProvider 补充测试 — 流式与响应式路径
 *
 * 补充已有 ResilientLLMProviderTest 中未覆盖的 completeStream / completeStreamReactive 路径，
 * 以及网络异常分类、异常解包等边界场景。
 */
@DisplayName("ResilientLLMProvider - 流式与响应式")
class ResilientLLMProviderStreamTest {

    // ==================== completeStream ====================

    @Nested
    @DisplayName("completeStream 回调流式")
    class CompleteStreamTests {

        @Test
        @DisplayName("流式调用成功更新健康状态")
        void shouldBecomeHealthyOnStreamSuccess() throws Exception {
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("stream-ok");
            var provider = new ResilientLLMProvider(delegate, 2, 10);

            CompletableFuture<LLMResponse> future = provider.completeStream(
                    List.of(), LLMOptions.builder().build(), new LLMProvider.StreamEventHandler() {});

            LLMResponse response = future.get();
            assertNotNull(response);
            assertEquals(ResilientLLMProvider.HealthStatus.HEALTHY, provider.getHealthStatus());
        }

        @Test
        @DisplayName("流式调用失败后重试成功")
        void shouldRetryStreamAndSucceed() throws Exception {
            AtomicInteger attempts = new AtomicInteger(0);
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("stream-retry") {
                @Override
                public CompletableFuture<LLMResponse> completeStream(
                        List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
                    if (attempts.incrementAndGet() <= 1) {
                        return CompletableFuture.failedFuture(new RuntimeException("Server error: 500"));
                    }
                    LLMResponse response = ResilientLLMProviderTest.createResponse("stream success");
                    handler.onComplete(response);
                    return CompletableFuture.completedFuture(response);
                }
            };

            var provider = new ResilientLLMProvider(delegate, 2, 10);
            CompletableFuture<LLMResponse> future = provider.completeStream(
                    List.of(), LLMOptions.builder().build(), new LLMProvider.StreamEventHandler() {});

            LLMResponse response = future.get();
            assertNotNull(response);
            assertEquals(2, attempts.get());
            assertEquals(ResilientLLMProvider.HealthStatus.HEALTHY, provider.getHealthStatus());
        }

        @Test
        @DisplayName("流式调用超过重试次数抛出异常")
        void shouldThrowAfterMaxStreamRetries() {
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("stream-fail") {
                @Override
                public CompletableFuture<LLMResponse> completeStream(
                        List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
                    return CompletableFuture.failedFuture(new RuntimeException("Server error: 502"));
                }
            };

            var provider = new ResilientLLMProvider(delegate, 1, 10);
            CompletableFuture<LLMResponse> future = provider.completeStream(
                    List.of(), LLMOptions.builder().build(), new LLMProvider.StreamEventHandler() {});

            ExecutionException ex = assertThrows(ExecutionException.class, future::get);
            assertInstanceOf(LLMProviderException.class, ex.getCause());
        }
    }

    // ==================== completeStreamReactive ====================

    @Nested
    @DisplayName("completeStreamReactive 响应式")
    class CompleteStreamReactiveTests {

        @Test
        @DisplayName("响应式流成功完成")
        void shouldCompleteReactiveStreamSuccessfully() {
            StreamEvent event = StreamEvent.textDelta("hello");
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("reactive-ok") {
                @Override
                public Flux<StreamEvent> completeStreamReactive(
                        List<ConversationMessage> messages, LLMOptions options) {
                    return Flux.just(event, StreamEvent.llmComplete(
                            ResilientLLMProviderTest.createResponse("done")));
                }
            };

            var provider = new ResilientLLMProvider(delegate, 2, 10);

            StepVerifier.create(provider.completeStreamReactive(
                            List.of(), LLMOptions.builder().build()))
                    .expectNext(event)
                    .expectNextMatches(e -> e.getType() == StreamEvent.EventType.LLM_COMPLETE)
                    .verifyComplete();

            assertEquals(ResilientLLMProvider.HealthStatus.HEALTHY, provider.getHealthStatus());
        }

        @Test
        @DisplayName("响应式流失败后自动重试")
        void shouldRetryReactiveStream() {
            AtomicInteger attempts = new AtomicInteger(0);
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("reactive-retry") {
                @Override
                public Flux<StreamEvent> completeStreamReactive(
                        List<ConversationMessage> messages, LLMOptions options) {
                    if (attempts.incrementAndGet() <= 1) {
                        return Flux.error(new RuntimeException("Server error: 503"));
                    }
                    return Flux.just(StreamEvent.textDelta("retried"));
                }
            };

            var provider = new ResilientLLMProvider(delegate, 2, 10);

            StepVerifier.create(provider.completeStreamReactive(
                            List.of(), LLMOptions.builder().build()))
                    .expectNextMatches(e -> "retried".equals(e.getTextDelta()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("不可重试的错误不触发 reactive retry")
        void shouldNotRetryNonRetryableInReactive() {
            AtomicInteger attempts = new AtomicInteger(0);
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("reactive-no-retry") {
                @Override
                public Flux<StreamEvent> completeStreamReactive(
                        List<ConversationMessage> messages, LLMOptions options) {
                    attempts.incrementAndGet();
                    return Flux.error(new RuntimeException("Authentication failed: 401"));
                }
            };

            var provider = new ResilientLLMProvider(delegate, 3, 10);

            StepVerifier.create(provider.completeStreamReactive(
                            List.of(), LLMOptions.builder().build()))
                    .expectError(RuntimeException.class)
                    .verify();

            assertEquals(1, attempts.get());
        }
    }

    // ==================== 异常分类 ====================

    @Nested
    @DisplayName("异常分类 isRetryable")
    class ExceptionClassificationTests {

        @Test
        @DisplayName("403 错误不重试")
        void shouldNotRetry403() {
            AtomicInteger attempts = new AtomicInteger(0);
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("forbidden") {
                @Override
                public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                    attempts.incrementAndGet();
                    throw new RuntimeException("Forbidden: 403");
                }
            };

            var provider = new ResilientLLMProvider(delegate, 3, 10);
            assertThrows(LLMProviderException.class,
                    () -> provider.complete(List.of(), LLMOptions.builder().build()));
            assertEquals(1, attempts.get());
        }

        @Test
        @DisplayName("404 错误不重试")
        void shouldNotRetry404() {
            AtomicInteger attempts = new AtomicInteger(0);
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("not-found") {
                @Override
                public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                    attempts.incrementAndGet();
                    throw new RuntimeException("Not Found: 404");
                }
            };

            var provider = new ResilientLLMProvider(delegate, 3, 10);
            assertThrows(LLMProviderException.class,
                    () -> provider.complete(List.of(), LLMOptions.builder().build()));
            assertEquals(1, attempts.get());
        }

        @Test
        @DisplayName("504 网关超时可重试")
        void shouldRetry504() {
            AtomicInteger attempts = new AtomicInteger(0);
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("gateway-timeout") {
                @Override
                public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                    if (attempts.incrementAndGet() <= 1) {
                        throw new RuntimeException("Gateway Timeout: 504");
                    }
                    return ResilientLLMProviderTest.createResponse("recovered");
                }
            };

            var provider = new ResilientLLMProvider(delegate, 2, 10);
            LLMResponse response = provider.complete(List.of(), LLMOptions.builder().build());
            assertNotNull(response);
            assertEquals(2, attempts.get());
        }

        @Test
        @DisplayName("null 消息的异常默认可重试")
        void shouldRetryNullMessageException() {
            AtomicInteger attempts = new AtomicInteger(0);
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("null-msg") {
                @Override
                public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                    if (attempts.incrementAndGet() <= 1) {
                        throw new RuntimeException((String) null);
                    }
                    return ResilientLLMProviderTest.createResponse("ok");
                }
            };

            var provider = new ResilientLLMProvider(delegate, 2, 10);
            LLMResponse response = provider.complete(List.of(), LLMOptions.builder().build());
            assertNotNull(response);
        }
    }

    // ==================== 异常解包 ====================

    @Nested
    @DisplayName("异常解包")
    class ExceptionUnwrapTests {

        @Test
        @DisplayName("CompletionException 被解包后识别不可重试")
        void shouldUnwrapCompletionException() {
            AtomicInteger attempts = new AtomicInteger(0);
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("unwrap-count") {
                @Override
                public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                    attempts.incrementAndGet();
                    throw new CompletionException(new RuntimeException("Bad Request: 401"));
                }
            };

            var provider = new ResilientLLMProvider(delegate, 3, 10);
            assertThrows(LLMProviderException.class,
                    () -> provider.complete(List.of(), LLMOptions.builder().build()));
            // 401 不可重试，只调用 1 次
            assertEquals(1, attempts.get());
        }
    }

    // ==================== LLMProviderException 包装 ====================

    @Nested
    @DisplayName("异常包装")
    class ExceptionWrappingTests {

        @Test
        @DisplayName("LLMProviderException 包含 provider 名称和重试次数")
        void shouldWrapWithContext() {
            var delegate = new ResilientLLMProviderTest.StubLLMProvider("ctx-provider") {
                @Override
                public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                    throw new RuntimeException("Error: 401");
                }
            };

            var provider = new ResilientLLMProvider(delegate, 0, 10);
            LLMProviderException ex = assertThrows(LLMProviderException.class,
                    () -> provider.complete(List.of(), LLMOptions.builder().build()));

            assertEquals("ctx-provider", ex.getProviderName());
            assertEquals(1, ex.getAttempts());
            assertTrue(ex.getMessage().contains("ctx-provider"));
        }
    }
}
