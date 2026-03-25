package com.lightweightai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoggingNotificationRouter 单元测试
 *
 * 覆盖：register/route/complete 生命周期、广播行为、按 token 路由
 */
@DisplayName("LoggingNotificationRouter - 日志通知路由")
class LoggingNotificationRouterTest {

    private LoggingNotificationRouter router;

    @BeforeEach
    void setUp() {
        router = new LoggingNotificationRouter();
    }

    @Test
    @DisplayName("register 返回 Flux，complete 后正常结束")
    void registerAndComplete() {
        var flux = router.register("log-1");
        router.complete("log-1");

        List<McpSchema.LoggingMessageNotification> results = flux.collectList().block();
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("广播 route 分发到所有活跃 sink")
    void broadcastRouteToAllSinks() {
        var fluxA = router.register("A");
        var fluxB = router.register("B");

        McpSchema.LoggingMessageNotification log = new McpSchema.LoggingMessageNotification(
                McpSchema.LoggingLevel.INFO, "test-logger", "Hello log", null);
        router.route(log);
        router.complete("A");
        router.complete("B");

        List<McpSchema.LoggingMessageNotification> resultsA = fluxA.collectList().block();
        List<McpSchema.LoggingMessageNotification> resultsB = fluxB.collectList().block();

        assertNotNull(resultsA);
        assertEquals(1, resultsA.size());

        assertNotNull(resultsB);
        assertEquals(1, resultsB.size());
    }

    @Test
    @DisplayName("按 token 路由只分发到指定 sink")
    void routeByTokenOnlyTargetsSink() {
        var fluxA = router.register("A");
        var fluxB = router.register("B");

        McpSchema.LoggingMessageNotification log = new McpSchema.LoggingMessageNotification(
                McpSchema.LoggingLevel.DEBUG, "logger", "targeted", null);
        router.route("A", log);
        router.complete("A");
        router.complete("B");

        List<McpSchema.LoggingMessageNotification> resultsA = fluxA.collectList().block();
        List<McpSchema.LoggingMessageNotification> resultsB = fluxB.collectList().block();

        assertNotNull(resultsA);
        assertEquals(1, resultsA.size());

        assertNotNull(resultsB);
        assertTrue(resultsB.isEmpty());
    }

    @Test
    @DisplayName("按 null token 路由不抛异常")
    void routeByNullTokenSafe() {
        McpSchema.LoggingMessageNotification log = new McpSchema.LoggingMessageNotification(
                McpSchema.LoggingLevel.WARNING, "logger", "warn", null);
        assertDoesNotThrow(() -> router.route(null, log));
    }

    @Test
    @DisplayName("complete 对未注册的 token 不抛异常")
    void completeUnknownTokenSafe() {
        assertDoesNotThrow(() -> router.complete("unknown"));
    }

    @Test
    @DisplayName("广播对无活跃 sink 时不抛异常")
    void broadcastNoSinksSafe() {
        McpSchema.LoggingMessageNotification log = new McpSchema.LoggingMessageNotification(
                McpSchema.LoggingLevel.ERROR, "logger", "nobody listening", null);
        assertDoesNotThrow(() -> router.route(log));
    }
}
