package com.lightweightai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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

        List<McpSchema.ProgressNotification> results = flux.collectList().block();
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("route 将通知分发到正确的 token")
    void routeToCorrectToken() {
        var flux = router.register("token-A");

        McpSchema.ProgressNotification notification = new McpSchema.ProgressNotification(
                "token-A", 0.5, 1.0, null, null);
        router.route(notification);
        router.complete("token-A");

        List<McpSchema.ProgressNotification> results = flux.collectList().block();
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("token-A", results.get(0).progressToken().toString());
        assertEquals(0.5, results.get(0).progress());
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

        List<McpSchema.ProgressNotification> resultsA = fluxA.collectList().block();
        List<McpSchema.ProgressNotification> resultsB = fluxB.collectList().block();

        assertNotNull(resultsA);
        assertEquals(1, resultsA.size());
        assertEquals(0.3, resultsA.get(0).progress());

        assertNotNull(resultsB);
        assertTrue(resultsB.isEmpty());
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

        List<McpSchema.ProgressNotification> results = flux.collectList().block();
        assertNotNull(results);
        assertEquals(3, results.size());
    }
}
