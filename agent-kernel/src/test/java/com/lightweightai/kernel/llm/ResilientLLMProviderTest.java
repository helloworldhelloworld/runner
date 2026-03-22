package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ResilientLLMProvider - 弹性LLM装饰器")
class ResilientLLMProviderTest {

    // ==================== 构造和委托 ====================

    @Test
    @DisplayName("null委托应抛异常")
    void shouldThrowOnNullDelegate() {
        assertThrows(IllegalArgumentException.class, () -> new ResilientLLMProvider(null));
    }

    @Test
    @DisplayName("初始健康状态为UNKNOWN")
    void shouldStartWithUnknownHealth() {
        var provider = new ResilientLLMProvider(createSuccessProvider());
        assertEquals(ResilientLLMProvider.HealthStatus.UNKNOWN, provider.getHealthStatus());
    }

    @Test
    @DisplayName("委托getProviderName")
    void shouldDelegateProviderName() {
        var provider = new ResilientLLMProvider(createSuccessProvider());
        assertEquals("test-provider", provider.getProviderName());
    }

    @Test
    @DisplayName("委托getModelCapability")
    void shouldDelegateModelCapability() {
        var delegate = createSuccessProvider();
        var provider = new ResilientLLMProvider(delegate);
        assertEquals(delegate.getModelCapability(), provider.getModelCapability());
    }

    @Test
    @DisplayName("获取被装饰的Provider")
    void shouldGetDelegate() {
        var delegate = createSuccessProvider();
        var provider = new ResilientLLMProvider(delegate);
        assertSame(delegate, provider.getDelegate());
    }

    // ==================== 成功路径 ====================

    @Test
    @DisplayName("成功调用后状态变为HEALTHY")
    void shouldBecomeHealthyOnSuccess() {
        var provider = new ResilientLLMProvider(createSuccessProvider());

        LLMResponse response = provider.complete(List.of(), LLMOptions.builder().build());

        assertNotNull(response);
        assertEquals(ResilientLLMProvider.HealthStatus.HEALTHY, provider.getHealthStatus());
    }

    @Test
    @DisplayName("成功调用更新统计指标")
    void shouldUpdateMetricsOnSuccess() {
        var provider = new ResilientLLMProvider(createSuccessProvider());
        provider.complete(List.of(), LLMOptions.builder().build());

        var info = provider.getHealthInfo();
        assertEquals(1, info.totalRequests());
        assertEquals(0, info.totalFailures());
        assertEquals(0, info.consecutiveFailures());
        assertTrue(info.lastSuccessTime() > 0);
    }

    // ==================== 重试逻辑 ====================

    @Test
    @DisplayName("瞬时失败后重试成功")
    void shouldRetryAndSucceed() {
        AtomicInteger attempts = new AtomicInteger(0);
        var delegate = new StubLLMProvider("retry-provider") {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                if (attempts.incrementAndGet() <= 1) {
                    throw new RuntimeException("Server error: 500");
                }
                return createResponse("success");
            }
        };

        // maxRetries=2, backoff=10ms (fast for testing)
        var provider = new ResilientLLMProvider(delegate, 2, 10);
        LLMResponse response = provider.complete(List.of(), LLMOptions.builder().build());

        assertNotNull(response);
        assertEquals(ResilientLLMProvider.HealthStatus.HEALTHY, provider.getHealthStatus());
    }

    @Test
    @DisplayName("超过重试次数后抛出LLMProviderException")
    void shouldThrowAfterMaxRetries() {
        var delegate = new StubLLMProvider("fail-provider") {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                throw new RuntimeException("Server error: 500");
            }
        };

        var provider = new ResilientLLMProvider(delegate, 2, 10);

        LLMProviderException ex = assertThrows(LLMProviderException.class,
            () -> provider.complete(List.of(), LLMOptions.builder().build()));

        assertEquals("fail-provider", ex.getProviderName());
        assertTrue(ex.getMessage().contains("fail-provider"));
    }

    @Test
    @DisplayName("不可重试的4xx错误立即失败")
    void shouldNotRetry4xxErrors() {
        AtomicInteger attempts = new AtomicInteger(0);
        var delegate = new StubLLMProvider("auth-fail") {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                attempts.incrementAndGet();
                throw new RuntimeException("Authentication failed: 401");
            }
        };

        var provider = new ResilientLLMProvider(delegate, 3, 10);
        assertThrows(LLMProviderException.class,
            () -> provider.complete(List.of(), LLMOptions.builder().build()));

        // Should only be called once (no retries)
        assertEquals(1, attempts.get());
    }

    @Test
    @DisplayName("400错误不重试")
    void shouldNotRetry400() {
        AtomicInteger attempts = new AtomicInteger(0);
        var delegate = new StubLLMProvider("bad-request") {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                attempts.incrementAndGet();
                throw new RuntimeException("Bad request: 400");
            }
        };

        var provider = new ResilientLLMProvider(delegate, 3, 10);
        assertThrows(LLMProviderException.class,
            () -> provider.complete(List.of(), LLMOptions.builder().build()));
        assertEquals(1, attempts.get());
    }

    @Test
    @DisplayName("429限流可重试")
    void shouldRetry429RateLimit() {
        AtomicInteger attempts = new AtomicInteger(0);
        var delegate = new StubLLMProvider("rate-limited") {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                if (attempts.incrementAndGet() <= 1) {
                    throw new RuntimeException("Rate limited: 429");
                }
                return createResponse("success");
            }
        };

        var provider = new ResilientLLMProvider(delegate, 2, 10);
        LLMResponse response = provider.complete(List.of(), LLMOptions.builder().build());
        assertNotNull(response);
        assertEquals(2, attempts.get());
    }

    // ==================== 健康状态转换 ====================

    @Test
    @DisplayName("失败后状态变为DEGRADED")
    void shouldBecomeDegradedOnFailure() {
        var delegate = new StubLLMProvider("degraded") {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                throw new RuntimeException("Error: 401");
            }
        };

        var provider = new ResilientLLMProvider(delegate, 0, 10);
        try { provider.complete(List.of(), LLMOptions.builder().build()); } catch (Exception ignored) {}

        assertEquals(ResilientLLMProvider.HealthStatus.DEGRADED, provider.getHealthStatus());
    }

    @Test
    @DisplayName("3次连续失败后状态变为UNHEALTHY")
    void shouldBecomeUnhealthyAfter3Failures() {
        var delegate = new StubLLMProvider("unhealthy") {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                throw new RuntimeException("Error: 401");
            }
        };

        var provider = new ResilientLLMProvider(delegate, 0, 10);
        for (int i = 0; i < 3; i++) {
            try { provider.complete(List.of(), LLMOptions.builder().build()); } catch (Exception ignored) {}
        }

        assertEquals(ResilientLLMProvider.HealthStatus.UNHEALTHY, provider.getHealthStatus());
    }

    @Test
    @DisplayName("失败后成功恢复为HEALTHY")
    void shouldRecoverToHealthyAfterFailure() {
        AtomicInteger callCount = new AtomicInteger(0);
        var delegate = new StubLLMProvider("recover") {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                if (callCount.incrementAndGet() <= 2) {
                    throw new RuntimeException("Error: 401");
                }
                return createResponse("recovered");
            }
        };

        var provider = new ResilientLLMProvider(delegate, 0, 10);

        // Two failures
        try { provider.complete(List.of(), LLMOptions.builder().build()); } catch (Exception ignored) {}
        try { provider.complete(List.of(), LLMOptions.builder().build()); } catch (Exception ignored) {}
        assertEquals(ResilientLLMProvider.HealthStatus.DEGRADED, provider.getHealthStatus());

        // Success
        provider.complete(List.of(), LLMOptions.builder().build());
        assertEquals(ResilientLLMProvider.HealthStatus.HEALTHY, provider.getHealthStatus());

        var info = provider.getHealthInfo();
        assertEquals(0, info.consecutiveFailures());
    }

    // ==================== HealthInfo ====================

    @Test
    @DisplayName("HealthInfo包含完整信息")
    void shouldReturnCompleteHealthInfo() {
        var provider = new ResilientLLMProvider(createSuccessProvider());
        provider.complete(List.of(), LLMOptions.builder().build());

        var info = provider.getHealthInfo();
        assertEquals(ResilientLLMProvider.HealthStatus.HEALTHY, info.status());
        assertEquals("test-provider", info.providerName());
        assertEquals(1, info.totalRequests());
        assertEquals(0, info.totalFailures());
        assertEquals(0, info.consecutiveFailures());
        assertTrue(info.lastSuccessTime() > 0);
        assertEquals(0L, info.lastFailureTime());
    }

    @Test
    @DisplayName("失败后HealthInfo记录错误信息")
    void shouldRecordErrorInHealthInfo() {
        var delegate = new StubLLMProvider("error-info") {
            @Override
            public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
                throw new RuntimeException("Connection timeout: 503");
            }
        };

        var provider = new ResilientLLMProvider(delegate, 0, 10);
        try { provider.complete(List.of(), LLMOptions.builder().build()); } catch (Exception ignored) {}

        var info = provider.getHealthInfo();
        assertTrue(info.lastError().contains("503"));
        assertTrue(info.lastFailureTime() > 0);
        assertEquals(1, info.totalFailures());
    }

    // ==================== Async ====================

    @Test
    @DisplayName("异步调用成功")
    void shouldCompleteAsync() throws Exception {
        var provider = new ResilientLLMProvider(createSuccessProvider());

        CompletableFuture<LLMResponse> future = provider.completeAsync(List.of(), LLMOptions.builder().build());
        LLMResponse response = future.get();

        assertNotNull(response);
        assertEquals(ResilientLLMProvider.HealthStatus.HEALTHY, provider.getHealthStatus());
    }

    // ==================== 辅助方法 ====================

    private StubLLMProvider createSuccessProvider() {
        return new StubLLMProvider("test-provider");
    }

    static LLMResponse createResponse(String text) {
        return LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(text)
                .build())
            .stopReason("end_turn")
            .build();
    }

    /**
     * Stub LLM provider for testing.
     */
    static class StubLLMProvider implements LLMProvider {
        private final String name;

        StubLLMProvider(String name) {
            this.name = name;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            return createResponse("stub response");
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options, StreamEventHandler handler) {
            LLMResponse response = complete(messages, options);
            handler.onComplete(response);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public ModelCapability getModelCapability() {
            return null;
        }

        @Override
        public String getProviderName() {
            return name;
        }
    }
}
