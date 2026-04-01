package com.lightweightai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProgressNotificationRouter - 进度通知路由")
class ProgressNotificationRouterTest {

    private ProgressNotificationRouter router;

    @BeforeEach
    void setUp() {
        router = new ProgressNotificationRouter();
    }

    @Test
    @DisplayName("register 返回非空 Flux")
    void registerReturnsFlux() {
        Flux<McpSchema.ProgressNotification> flux = router.register("token-1");
        assertNotNull(flux);
        // cleanup
        router.complete("token-1");
    }

    @Test
    @DisplayName("complete 未注册的 token 不崩溃")
    void completeUnregisteredToken() {
        assertDoesNotThrow(() -> router.complete("unknown"));
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
        Flux<McpSchema.ProgressNotification> flux1 = router.register("token-a");
        Flux<McpSchema.ProgressNotification> flux2 = router.register("token-b");

        assertNotNull(flux1);
        assertNotNull(flux2);

        // complete one should not affect the other
        router.complete("token-a");
        assertDoesNotThrow(() -> router.complete("token-b"));
    }
}
