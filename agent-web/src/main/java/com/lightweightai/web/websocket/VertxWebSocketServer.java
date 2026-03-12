package com.lightweightai.web.websocket;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vert.x WebSocket 服务器，通过 Spring SmartLifecycle 管理生命周期。
 *
 * <p>设计要点：
 * <ul>
 *   <li>启动失败时抛异常，阻止 Spring 上下文继续</li>
 *   <li>关闭时先通知所有客户端，再停止服务器，最后关闭 Vert.x 实例</li>
 *   <li>phase 设置为 Integer.MAX_VALUE - 100，在 Spring 组件之后启动、之前关闭</li>
 *   <li>同步启动/关闭，确保 Spring 生命周期回调返回时服务器状态确定</li>
 * </ul>
 */
public class VertxWebSocketServer implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(VertxWebSocketServer.class);

    /** WebSocket 端点路径 */
    private static final String WS_PATH = "/ws/chat";

    /** 启动/关闭超时（秒） */
    private static final int LIFECYCLE_TIMEOUT_SECONDS = 30;

    private final Vertx vertx;
    private final VertxChatWebSocketHandler handler;
    private final int port;

    private volatile HttpServer httpServer;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public VertxWebSocketServer(Vertx vertx, VertxChatWebSocketHandler handler, int port) {
        this.vertx = vertx;
        this.handler = handler;
        this.port = port;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            logger.warn("Vert.x WebSocket server is already running");
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean startSuccess = new AtomicBoolean(false);

        HttpServerOptions options = new HttpServerOptions()
            .setPort(port)
            .setHost("0.0.0.0")
            // 生产级别参数
            .setMaxWebSocketMessageSize(64 * 1024)     // 64KB 单条消息上限
            .setMaxWebSocketFrameSize(64 * 1024)       // 64KB 帧大小上限
            .setIdleTimeout(300)                        // 5分钟空闲超时
            .setIdleTimeoutUnit(TimeUnit.SECONDS)
            .setTcpKeepAlive(true)                     // TCP Keep-Alive
            .setTcpNoDelay(true)                       // 禁用 Nagle 算法，降低延迟
            .setAcceptBacklog(1024);                   // 连接等待队列

        httpServer = vertx.createHttpServer(options);

        httpServer.webSocketHandler(ws -> {
            // 路径校验
            if (!WS_PATH.equals(ws.path())) {
                ws.reject(404);
                return;
            }

            handler.onConnect(ws);

            ws.textMessageHandler(msg -> handler.onMessage(ws, msg));
            ws.closeHandler(v -> handler.onClose(ws));
            ws.exceptionHandler(err -> handler.onError(ws, err));
        });

        // 对非 WebSocket 的 HTTP 请求返回 426 Upgrade Required
        httpServer.requestHandler(req ->
            req.response()
                .setStatusCode(426)
                .putHeader("Content-Type", "text/plain")
                .end("WebSocket endpoint. Use ws:// protocol.")
        );

        httpServer.listen()
            .onSuccess(server -> {
                startSuccess.set(true);
                latch.countDown();
                logger.info("Vert.x WebSocket server started on port {} (path: {})", server.actualPort(), WS_PATH);
            })
            .onFailure(err -> {
                latch.countDown();
                logger.error("Failed to start Vert.x WebSocket server on port {}", port, err);
            });

        try {
            if (!latch.await(LIFECYCLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                running.set(false);
                throw new IllegalStateException(
                    "Vert.x WebSocket server startup timed out after " + LIFECYCLE_TIMEOUT_SECONDS + "s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
            throw new IllegalStateException("Vert.x WebSocket server startup interrupted", e);
        }

        if (!startSuccess.get()) {
            running.set(false);
            throw new IllegalStateException("Vert.x WebSocket server failed to start on port " + port);
        }
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        logger.info("Stopping Vert.x WebSocket server...");

        CountDownLatch latch = new CountDownLatch(1);

        // 1. 通知所有客户端并关闭连接
        handler.closeAll();

        // 2. 关闭 HTTP 服务器（停止接受新连接）
        if (httpServer != null) {
            httpServer.close()
                .onComplete(ar -> {
                    if (ar.failed()) {
                        logger.warn("Error closing Vert.x HTTP server", ar.cause());
                    }
                    latch.countDown();
                });
        } else {
            latch.countDown();
        }

        try {
            if (!latch.await(LIFECYCLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                logger.warn("Vert.x HTTP server close timed out after {}s", LIFECYCLE_TIMEOUT_SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Vert.x HTTP server close interrupted");
        }

        logger.info("Vert.x WebSocket server stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // 在大多数 Spring 组件之后启动，之前关闭
        return Integer.MAX_VALUE - 100;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * 获取实际监听端口（服务器启动后可用）
     */
    public int getActualPort() {
        return httpServer != null ? httpServer.actualPort() : -1;
    }
}
