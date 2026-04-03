package com.lightweightai.mcp;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 McpToolWrapper 的 concatWith 流拓扑：
 *
 *   Flux.merge(callTrigger, progressFlux, loggingFlux)   ← Phase 1: 通知帧
 *       .concatWith(resultFlux)                          ← Phase 2: COMPLETE 在最后
 *
 * 核心保证：所有 PROGRESS/LOG 帧在 COMPLETE 之前到达，不丢失。
 */
@DisplayName("McpToolWrapper concatWith 流拓扑验证")
class McpToolWrapperConcatFlowTest {

    private ProgressNotificationRouter progressRouter;
    private LoggingNotificationRouter loggingRouter;
    private String token;
    private static final String TOOL_NAME = "test_tool";

    @BeforeEach
    void setUp() {
        progressRouter = new ProgressNotificationRouter();
        loggingRouter = new LoggingNotificationRouter();
        token = "test-token";
    }

    @Test
    @DisplayName("正常流：progress → result，COMPLETE 在所有 PROGRESS 之后")
    void normalFlow_completeAfterAllProgress() {
        Flux<ToolResultChunk> progressFlux = progressRouter.register(token)
            .map(pn -> ToolResultChunk.progress(TOOL_NAME, pn.message(), 0.0, 1.0, null));

        Flux<ToolResultChunk> loggingFlux = loggingRouter.register(token)
            .map(ln -> ToolResultChunk.log(TOOL_NAME, "INFO", "test", ln.data(), null));

        // 模拟 MCP 调用：先发 progress，再返回 result
        Mono<ToolResultChunk> callMono = Mono.defer(() -> {
                progressRouter.route(new McpSchema.ProgressNotification(token, 0.5, 1.0, "step 1"));
                progressRouter.route(new McpSchema.ProgressNotification(token, 1.0, 1.0, "step 2"));
                return Mono.just(ToolResultChunk.complete(TOOL_NAME, ToolResult.success("done")));
            })
            .doOnTerminate(() -> {
                progressRouter.complete(token);
                loggingRouter.complete(token);
            })
            .cache();

        Flux<ToolResultChunk> callTrigger = callMono
            .then(Mono.<ToolResultChunk>empty())
            .onErrorResume(e -> Mono.empty())
            .flux();

        Flux<ToolResultChunk> resultFlux = callMono.flux();

        List<ToolResultChunk> chunks = Flux.merge(callTrigger, progressFlux, loggingFlux)
            .concatWith(resultFlux)
            .collectList()
            .block(Duration.ofSeconds(5));

        assertNotNull(chunks);
        assertEquals(3, chunks.size(), "2 PROGRESS + 1 COMPLETE");

        // COMPLETE 必须是最后一个
        assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(chunks.size() - 1).getType());

        // 前面全是 PROGRESS
        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(0).getType());
        assertEquals(ToolResultChunk.ChunkType.PROGRESS, chunks.get(1).getType());
    }

    @Test
    @DisplayName("异步通知帧：progress 在另一个线程发送，仍不丢失")
    void asyncProgress_notLost() throws InterruptedException {
        Flux<ToolResultChunk> progressFlux = progressRouter.register(token)
            .map(pn -> ToolResultChunk.progress(TOOL_NAME, pn.message(), 0.0, 1.0, null));

        Flux<ToolResultChunk> loggingFlux = loggingRouter.register(token)
            .map(ln -> ToolResultChunk.log(TOOL_NAME, "INFO", "test", ln.data(), null));

        CountDownLatch progressSent = new CountDownLatch(1);

        // 模拟 MCP 调用：异步线程发 progress，然后返回 result
        Mono<ToolResultChunk> callMono = Mono.defer(() -> {
                // 启动异步 progress 发送
                new Thread(() -> {
                    progressRouter.route(new McpSchema.ProgressNotification(token, 0.3, 1.0, "async step 1"));
                    progressRouter.route(new McpSchema.ProgressNotification(token, 0.7, 1.0, "async step 2"));
                    progressRouter.route(new McpSchema.ProgressNotification(token, 1.0, 1.0, "async step 3"));
                    progressSent.countDown();
                }).start();

                // 等 progress 发完再返回 result（模拟 MCP 协议顺序保证）
                try { progressSent.await(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
                return Mono.just(ToolResultChunk.complete(TOOL_NAME, ToolResult.success("async done")));
            })
            .doOnTerminate(() -> {
                progressRouter.complete(token);
                loggingRouter.complete(token);
            })
            .cache();

        Flux<ToolResultChunk> callTrigger = callMono
            .then(Mono.<ToolResultChunk>empty())
            .onErrorResume(e -> Mono.empty())
            .flux();

        Flux<ToolResultChunk> resultFlux = callMono.flux();

        List<ToolResultChunk> chunks = Flux.merge(callTrigger, progressFlux, loggingFlux)
            .concatWith(resultFlux)
            .collectList()
            .block(Duration.ofSeconds(5));

        assertNotNull(chunks);

        long progressCount = chunks.stream()
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.PROGRESS)
            .count();
        assertEquals(3, progressCount, "All 3 async PROGRESS chunks should arrive");

        // COMPLETE 必须是最后一个
        ToolResultChunk last = chunks.get(chunks.size() - 1);
        assertEquals(ToolResultChunk.ChunkType.COMPLETE, last.getType());
    }

    @Test
    @DisplayName("错误流：callMono 失败 → router 关闭不死锁 → ERROR chunk 发出")
    void errorFlow_routerClosedAndErrorChunkEmitted() {
        Flux<ToolResultChunk> progressFlux = progressRouter.register(token)
            .map(pn -> ToolResultChunk.progress(TOOL_NAME, pn.message(), 0.0, 1.0, null));

        Flux<ToolResultChunk> loggingFlux = loggingRouter.register(token)
            .map(ln -> ToolResultChunk.log(TOOL_NAME, "INFO", "test", ln.data(), null));

        AtomicBoolean routerClosed = new AtomicBoolean(false);

        Mono<ToolResultChunk> callMono = Mono.<ToolResultChunk>error(
                new RuntimeException("Connection refused"))
            .doOnTerminate(() -> {
                routerClosed.set(true);
                progressRouter.complete(token);
                loggingRouter.complete(token);
            })
            .cache();

        // callTrigger 必须吞掉 error，否则 merge 会取消 progressFlux
        Flux<ToolResultChunk> callTrigger = callMono
            .then(Mono.<ToolResultChunk>empty())
            .onErrorResume(e -> Mono.empty())
            .flux();

        Flux<ToolResultChunk> resultFlux = callMono
            .onErrorResume(e -> Mono.just(
                ToolResultChunk.error(TOOL_NAME, "MCP call failed: " + e.getMessage())))
            .flux();

        List<ToolResultChunk> chunks = Flux.merge(callTrigger, progressFlux, loggingFlux)
            .concatWith(resultFlux)
            .collectList()
            .block(Duration.ofSeconds(5));

        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        assertTrue(routerClosed.get(), "Router should be closed even on error");

        // 应有 ERROR chunk
        ToolResultChunk errorChunk = chunks.stream()
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.ERROR)
            .findFirst()
            .orElse(null);
        assertNotNull(errorChunk, "Should have ERROR chunk");
        assertTrue(errorChunk.getMessage().contains("Connection refused"));
    }

    @Test
    @DisplayName("无通知帧：只有 result → 只有 COMPLETE")
    void noNotifications_onlyComplete() {
        Flux<ToolResultChunk> progressFlux = progressRouter.register(token)
            .map(pn -> ToolResultChunk.progress(TOOL_NAME, pn.message(), 0.0, 1.0, null));

        Flux<ToolResultChunk> loggingFlux = loggingRouter.register(token)
            .map(ln -> ToolResultChunk.log(TOOL_NAME, "INFO", "test", ln.data(), null));

        Mono<ToolResultChunk> callMono = Mono.just(
                ToolResultChunk.complete(TOOL_NAME, ToolResult.success("quick result")))
            .doOnTerminate(() -> {
                progressRouter.complete(token);
                loggingRouter.complete(token);
            })
            .cache();

        Flux<ToolResultChunk> callTrigger = callMono
            .then(Mono.<ToolResultChunk>empty())
            .onErrorResume(e -> Mono.empty())
            .flux();

        Flux<ToolResultChunk> resultFlux = callMono.flux();

        List<ToolResultChunk> chunks = Flux.merge(callTrigger, progressFlux, loggingFlux)
            .concatWith(resultFlux)
            .collectList()
            .block(Duration.ofSeconds(5));

        assertNotNull(chunks);
        assertEquals(1, chunks.size());
        assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(0).getType());
    }

    @Test
    @DisplayName("progress + logging 混合 → 全部在 COMPLETE 之前")
    void mixedProgressAndLogging_allBeforeComplete() {
        Flux<ToolResultChunk> progressFlux = progressRouter.register(token)
            .map(pn -> ToolResultChunk.progress(TOOL_NAME, pn.message(), 0.0, 1.0, null));

        Flux<ToolResultChunk> loggingFlux = loggingRouter.register(token)
            .map(ln -> ToolResultChunk.log(TOOL_NAME, "INFO", "test",
                ln.data() != null ? ln.data() : "", null));

        Mono<ToolResultChunk> callMono = Mono.defer(() -> {
                progressRouter.route(new McpSchema.ProgressNotification(token, 0.5, 1.0, "progress"));
                loggingRouter.route(new McpSchema.LoggingMessageNotification(
                    McpSchema.LoggingLevel.INFO, "logger", "log entry", null));
                progressRouter.route(new McpSchema.ProgressNotification(token, 1.0, 1.0, "done"));
                return Mono.just(ToolResultChunk.complete(TOOL_NAME, ToolResult.success("result")));
            })
            .doOnTerminate(() -> {
                progressRouter.complete(token);
                loggingRouter.complete(token);
            })
            .cache();

        Flux<ToolResultChunk> callTrigger = callMono
            .then(Mono.<ToolResultChunk>empty())
            .onErrorResume(e -> Mono.empty())
            .flux();

        Flux<ToolResultChunk> resultFlux = callMono.flux();

        List<ToolResultChunk> chunks = Flux.merge(callTrigger, progressFlux, loggingFlux)
            .concatWith(resultFlux)
            .collectList()
            .block(Duration.ofSeconds(5));

        assertNotNull(chunks);
        assertEquals(4, chunks.size(), "2 PROGRESS + 1 LOG + 1 COMPLETE");

        // COMPLETE 必须是最后一个
        assertEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(chunks.size() - 1).getType());

        // 前面不能有 COMPLETE
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertNotEquals(ToolResultChunk.ChunkType.COMPLETE, chunks.get(i).getType(),
                "COMPLETE must be the last chunk, but found at index " + i);
        }
    }
}
