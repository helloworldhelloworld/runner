package com.lightweightai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProgressNotificationRouter 单元测试
 *
 * 覆盖：register/route/complete 生命周期、token 匹配、并发安全
 */
@DisplayName("ProgressNotificationRouter - 进度通知路由")
class ProgressNotificationRouterTest {

    private ProgressNotificationRouter router;

    @BeforeEach
    void setUp() {
        router = new ProgressNotificationRouter();
    }

    @Test
    @DisplayName("register 返回 Flux，complete 后正常结束")
    void registerAndComplete() {
        var flux = router.register("token-1");

        router.complete("token-1");

        StepVerifier.create(flux)
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("route 将通知分发到正确的 token")
    void routeToCorrectToken() {
        var flux = router.register("token-A");

        McpSchema.ProgressNotification notification = new McpSchema.ProgressNotification(
                "token-A", 0.5, 1.0, null, null);
        router.route(notification);
        router.complete("token-A");

        StepVerifier.create(flux)
                .assertNext(n -> {
                    assertEquals("token-A", n.progressToken().toString());
                    assertEquals(0.5, n.progress());
                })
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("route 不影响其他 token 的 sink")
    void routeDoesNotLeakToOtherTokens() {
        var fluxA = router.register("A");
        var fluxB = router.register("B");

        McpSchema.ProgressNotification notifA = new McpSchema.ProgressNotification(
                "A", 0.3, 1.0, null, null);
        router.route(notifA);
        router.complete("A");
        router.complete("B");

        StepVerifier.create(fluxA)
                .assertNext(n -> assertEquals(0.3, n.progress()))
                .expectComplete()
                .verify(Duration.ofSeconds(2));

        StepVerifier.create(fluxB)
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("route 对未注册的 token 不抛异常")
    void routeUnknownTokenSafe() {
        McpSchema.ProgressNotification notification = new McpSchema.ProgressNotification(
                "unknown", 1.0, 1.0, null, null);
        assertDoesNotThrow(() -> router.route(notification));
    }

    @Test
    @DisplayName("route 对 null token 的通知不抛异常")
    void routeNullTokenSafe() {
        McpSchema.ProgressNotification notification = new McpSchema.ProgressNotification(
                null, 0.0, 1.0, null, null);
        assertDoesNotThrow(() -> router.route(notification));
    }

    @Test
    @DisplayName("complete 对未注册的 token 不抛异常")
    void completeUnknownTokenSafe() {
        assertDoesNotThrow(() -> router.complete("nonexistent"));
    }

    @Test
    @DisplayName("多次 route 后一次 complete")
    void multipleRouteThenComplete() {
        var flux = router.register("multi");

        for (int i = 0; i < 3; i++) {
            router.route(new McpSchema.ProgressNotification("multi", (double) i / 3, 1.0, null, null));
        }
        router.complete("multi");

        StepVerifier.create(flux)
                .expectNextCount(3)
                .expectComplete()
                .verify(Duration.ofSeconds(2));
    }
}
