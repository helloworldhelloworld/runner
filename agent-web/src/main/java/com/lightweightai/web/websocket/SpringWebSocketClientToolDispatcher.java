package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.DefaultDirectiveManager;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.agent.directive.DirectiveManager;
import com.lightweightai.kernel.llm.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Spring WebSocket 的客户端工具调度器。
 *
 * 功能与 {@link VertxWebSocketClientToolDispatcher} 对齐，但使用 Spring {@link WebSocketSession}。
 * 每个 WebSocket 连接对应一个实例，连接断开时调用 {@link #cancelAll()}。
 */
public class SpringWebSocketClientToolDispatcher implements ClientToolDispatcher, ToolCallCompleter {

    private static final Logger logger = LoggerFactory.getLogger(SpringWebSocketClientToolDispatcher.class);

    private final WebSocketSession session;
    private final DirectiveManager directiveManager;

    private final ConcurrentHashMap<String, CompletableFuture<ToolResult>> pendingCalls =
            new ConcurrentHashMap<>();

    private volatile String latestPendingCallId;
    private volatile String latestPendingToolName;

    public SpringWebSocketClientToolDispatcher(WebSocketSession session) {
        this(session, new DefaultDirectiveManager());
    }

    public SpringWebSocketClientToolDispatcher(WebSocketSession session, DirectiveManager directiveManager) {
        this.session = Objects.requireNonNull(session, "session cannot be null");
        this.directiveManager = Objects.requireNonNull(directiveManager, "directiveManager cannot be null");
    }

    @Override
    public CompletableFuture<ToolResult> dispatch(String callId, String toolName, Directive directive) {
        if (!tryAcquireSingleFlight(callId, toolName)) {
            CompletableFuture<ToolResult> busy = new CompletableFuture<>();
            busy.completeExceptionally(new IllegalStateException(
                "Client dispatcher is busy: another client tool call is pending"));
            return busy;
        }

        CompletableFuture<ToolResult> future = new CompletableFuture<>();
        pendingCalls.put(callId, future);

        try {
            Directive primary = ensureDirectiveDefaults(directive, callId, toolName);
            String json = directiveManager.getResult(callId, primary);
            logger.debug("Directive dispatched: {} (directiveId={})", toolName, callId);

            if (!session.isOpen()) {
                pendingCalls.remove(callId);
                clearSingleFlight(callId);
                future.completeExceptionally(
                    new RuntimeException("WebSocket session is closed, cannot dispatch tool call"));
                return future;
            }

            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            pendingCalls.remove(callId);
            clearSingleFlight(callId);
            future.completeExceptionally(
                new RuntimeException("Failed to send client tool call: " + e.getMessage(), e));
        }

        future.whenComplete((result, error) -> {
            pendingCalls.remove(callId);
            clearSingleFlight(callId);
        });

        return future;
    }

    private Directive ensureDirectiveDefaults(Directive directive, String callId, String toolName) {
        Directive effective = directive != null
            ? directive
            : Directive.fromToolName(callId, toolName, null, 0);
        if (effective.getDirectiveId() == null || effective.getDirectiveId().isBlank()) {
            effective.setDirectiveId(callId);
        }
        if (effective.getNamespace() == null || effective.getNamespace().isBlank()
            || effective.getName() == null || effective.getName().isBlank()) {
            Directive fallback = Directive.fromToolName(callId, toolName, effective.getPayload(), 0);
            if (effective.getNamespace() == null || effective.getNamespace().isBlank()) {
                effective.setNamespace(fallback.getNamespace());
            }
            if (effective.getName() == null || effective.getName().isBlank()) {
                effective.setName(fallback.getName());
            }
        }
        return effective;
    }

    private synchronized boolean tryAcquireSingleFlight(String callId, String toolName) {
        if (latestPendingCallId != null) {
            return false;
        }
        latestPendingCallId = callId;
        latestPendingToolName = toolName;
        return true;
    }

    private synchronized void clearSingleFlight(String callId) {
        if (callId != null && callId.equals(latestPendingCallId)) {
            latestPendingCallId = null;
            latestPendingToolName = null;
        }
    }

    public boolean completeCall(String callId, ToolResult result) {
        CompletableFuture<ToolResult> future = pendingCalls.remove(callId);
        if (future != null) {
            clearSingleFlight(callId);
            future.complete(result);
            return true;
        }
        logger.warn("No pending call found for callId: {}", callId);
        return false;
    }

    public boolean completeLatestCall(ToolResult result) {
        String latest = latestPendingCallId;
        if (latest == null) {
            logger.warn("No latest pending call for fallback completion");
            return false;
        }
        return completeCall(latest, result);
    }

    public void cancelAll() {
        pendingCalls.forEach((callId, future) -> {
            future.completeExceptionally(
                new RuntimeException("WebSocket connection closed, client tool call cancelled"));
        });
        pendingCalls.clear();
        latestPendingCallId = null;
        latestPendingToolName = null;
    }

    public int pendingCount() {
        return pendingCalls.size();
    }
}
