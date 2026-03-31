package com.lightweightai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 将 McpAsyncClient 全局的 loggingConsumer 回调
 * 路由到每次独立工具调用对应的 Flux sink。
 *
 * 结构与 ProgressNotificationRouter 相同，
 * key 为 progressToken（MCP 规范中日志通知关联同一请求上下文）。
 */
public class LoggingNotificationRouter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingNotificationRouter.class);

    private final ConcurrentHashMap<String, Sinks.Many<McpSchema.LoggingMessageNotification>> sinks
            = new ConcurrentHashMap<>();

    /**
     * 注册一个新的工具调用，返回 Flux 供订阅。
     * 不在 Flux 上添加 doFinally 移除 sink — 由 complete() 统一管理生命周期。
     */
    public Flux<McpSchema.LoggingMessageNotification> register(String progressToken) {
        Sinks.Many<McpSchema.LoggingMessageNotification> sink =
                Sinks.many().unicast().onBackpressureBuffer();
        sinks.put(progressToken, sink);
        return sink.asFlux();
    }

    /**
     * 由 loggingConsumer 回调调用，分发到对应 sink
     */
    public void route(McpSchema.LoggingMessageNotification notification) {
        // MCP logging 通知没有直接的 progressToken 字段，
        // 但当我们控制 McpServerRunner 代理时可以关联。
        // 对于全局日志，广播到所有活跃 sink。
        logger.debug("[MCP-LOGGING] Broadcasting to {} active sinks, level={}, data={}",
            sinks.size(), notification.level(), notification.data());
        for (Sinks.Many<McpSchema.LoggingMessageNotification> sink : sinks.values()) {
            sink.tryEmitNext(notification);
        }
    }

    /**
     * 按 token 路由（当能确定关联的 token 时使用）
     */
    public void route(String progressToken, McpSchema.LoggingMessageNotification notification) {
        if (progressToken != null) {
            Sinks.Many<McpSchema.LoggingMessageNotification> sink = sinks.get(progressToken);
            if (sink != null) {
                sink.tryEmitNext(notification);
            } else {
                logger.warn("[MCP-LOGGING] No sink for token={}, notification dropped", progressToken);
            }
        }
    }

    /**
     * 工具调用完成时关闭对应 sink
     */
    public void complete(String progressToken) {
        Sinks.Many<McpSchema.LoggingMessageNotification> sink = sinks.remove(progressToken);
        if (sink != null) {
            logger.debug("[MCP-LOGGING] Completing sink for token={}", progressToken);
            sink.tryEmitComplete();
        }
    }
}
