package com.lightweightai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 将 McpAsyncClient 全局的 progressConsumer 回调
 * 路由到每次独立工具调用对应的 Flux sink。
 *
 * MCP 的 ProgressNotification 携带 progressToken，
 * 每次 callTool 请求中通过 _meta.progressToken 传入唯一 token，
 * 服务端在执行过程中通过同一个 token 推送进度通知，
 * Router 根据 token 查找对应的 sink 并分发。
 */
public class ProgressNotificationRouter {

    private final ConcurrentHashMap<String, Sinks.Many<McpSchema.ProgressNotification>> sinks
            = new ConcurrentHashMap<>();

    /**
     * 注册一个新的工具调用，返回 Flux 供订阅
     */
    public Flux<McpSchema.ProgressNotification> register(String progressToken) {
        Sinks.Many<McpSchema.ProgressNotification> sink =
                Sinks.many().unicast().onBackpressureBuffer();
        sinks.put(progressToken, sink);
        return sink.asFlux()
                .doFinally(signal -> sinks.remove(progressToken));
    }

    /**
     * 由 progressConsumer 回调调用，分发到对应 sink
     */
    public void route(McpSchema.ProgressNotification notification) {
        Object token = notification.progressToken();
        if (token != null) {
            Sinks.Many<McpSchema.ProgressNotification> sink = sinks.get(token.toString());
            if (sink != null) {
                sink.tryEmitNext(notification);
            }
        }
    }

    /**
     * 工具调用完成时关闭对应 sink
     */
    public void complete(String progressToken) {
        Sinks.Many<McpSchema.ProgressNotification> sink = sinks.remove(progressToken);
        if (sink != null) {
            sink.tryEmitComplete();
        }
    }
}
