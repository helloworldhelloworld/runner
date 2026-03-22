package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * 会话感知的 ClientToolDispatcher —— 全局单例，延迟绑定实际 dispatcher。
 *
 * 服务启动时即可注册端工具（如 GetPositionTool），无需等待 WebSocket 连接。
 * WebSocket 连接建立时通过 {@link #setDelegate} 绑定实际的 dispatcher，
 * 断开时通过 {@link #clearDelegate} 清除。
 *
 * 如果调用时无 delegate（无客户端连接），返回失败 Future。
 */
public class SessionAwareClientToolDispatcher implements ClientToolDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(SessionAwareClientToolDispatcher.class);

    private volatile ClientToolDispatcher delegate;

    @Override
    public CompletableFuture<ToolResult> dispatch(String callId, String toolName, Directive directive) {
        ClientToolDispatcher current = delegate;
        if (current == null) {
            CompletableFuture<ToolResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(
                new IllegalStateException("No client connected — cannot dispatch client tool: " + toolName));
            logger.warn("Client tool dispatch failed: no active client connection for {}", toolName);
            return failed;
        }
        return current.dispatch(callId, toolName, directive);
    }

    /**
     * 绑定实际的 dispatcher（WebSocket 连接建立时调用）
     */
    public void setDelegate(ClientToolDispatcher delegate) {
        this.delegate = delegate;
        logger.info("Client tool dispatcher delegate set: {}", delegate.getClass().getSimpleName());
    }

    /**
     * 清除 delegate（WebSocket 断开时调用）
     */
    public void clearDelegate() {
        this.delegate = null;
        logger.info("Client tool dispatcher delegate cleared");
    }

    /**
     * 是否有活跃的 delegate
     */
    public boolean hasDelegate() {
        return delegate != null;
    }
}
