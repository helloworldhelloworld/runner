package com.lightweightai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LoggingNotificationRouter - 日志通知路由")
class LoggingNotificationRouterTest {

    private LoggingNotificationRouter router;

    @BeforeEach
    void setUp() {
        router = new LoggingNotificationRouter();
    }

    @Test
    @DisplayName("register 返回非空 Flux")
    void registerReturnsFlux() {
        Flux<McpSchema.LoggingMessageNotification> flux = router.register("token-1");
        assertNotNull(flux);
        router.complete("token-1");
    }

    @Test
    @DisplayName("complete 未注册的 token 不崩溃")
    void completeUnregisteredToken() {
        assertDoesNotThrow(() -> router.complete("unknown"));
    }

    @Test
    @DisplayName("null token 定向路由不崩溃")
    void nullTokenDirectRoute() {
        assertDoesNotThrow(() -> router.route(null, null));
    }

    @Test
    @DisplayName("多次 complete 同一 token 不崩溃")
    void doubleComplete() {
        router.register("token-2");
        router.complete("token-2");
        assertDoesNotThrow(() -> router.complete("token-2"));
    }

    @Test
    @DisplayName("多个独立 token 注册不互相干扰")
    void multipleTokens() {
        Flux<McpSchema.LoggingMessageNotification> flux1 = router.register("token-a");
        Flux<McpSchema.LoggingMessageNotification> flux2 = router.register("token-b");

        assertNotNull(flux1);
        assertNotNull(flux2);

        router.complete("token-a");
        assertDoesNotThrow(() -> router.complete("token-b"));
    }
}
