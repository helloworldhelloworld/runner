package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.ClientManifest;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ClientToolCallData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.DirectiveData;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Vert.x WebSocket 的客户端工具调度器。
 *
 * 负责将工具调用通过 WebSocket 推送到客户端，并管理挂起请求的 Future。
 * 当客户端回传 CLIENT_TOOL_RESULT 消息时，由 VertxChatWebSocketHandler 调用
 * {@link #completeCall(String, ToolResult)} 来完成对应的 Future。
 *
 * <p>生命周期绑定到一个 ServerWebSocket：
 * <ul>
 *   <li>一个 connection 对应一个 dispatcher 实例</li>
 *   <li>连接断开时，所有挂起的 Future 都会被异常完成</li>
 * </ul>
 *
 * <p>线程安全：Vert.x ServerWebSocket.writeTextMessage() 在 4.x 中是线程安全的，
 * 内部会调度到正确的 event loop。ConcurrentHashMap 保证挂起调用的跨线程安全访问。
 */
public class VertxWebSocketClientToolDispatcher implements ClientToolDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(VertxWebSocketClientToolDispatcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ServerWebSocket ws;

    /** callId → CompletableFuture，等待客户端回传结果 */
    private final ConcurrentHashMap<String, CompletableFuture<ToolResult>> pendingCalls =
            new ConcurrentHashMap<>();

    /** 单飞控制：仅保留最近一个挂起调用（无 ID 回包时用于回退匹配） */
    private volatile String latestPendingCallId;
    private volatile String latestPendingToolName;

    /** 端侧上报的能力清单，非 null 表示支持 Directive 协议 */
    private volatile ClientManifest manifest;

    public VertxWebSocketClientToolDispatcher(ServerWebSocket ws) {
        this.ws = ws;
    }

    /**
     * 设置端侧 manifest（表示该 session 支持 Directive 协议）
     */
    public void setManifest(ClientManifest manifest) {
        this.manifest = manifest;
    }

    /**
     * 获取端侧 manifest
     */
    public ClientManifest getManifest() {
        return manifest;
    }

    /**
     * 是否支持 Directive 协议（端侧已上报 manifest）
     */
    public boolean hasManifest() {
        return manifest != null;
    }

    @Override
    public CompletableFuture<ToolResult> dispatch(String callId, String toolName,
                                                   Map<String, Object> args) {
        if (!tryAcquireSingleFlight(callId, toolName)) {
            CompletableFuture<ToolResult> busy = new CompletableFuture<>();
            busy.completeExceptionally(new IllegalStateException(
                "Client dispatcher is busy: another client tool call is pending"));
            return busy;
        }

        CompletableFuture<ToolResult> future = new CompletableFuture<>();
        pendingCalls.put(callId, future);

        try {
            String json;
            if (hasManifest()) {
                // Directive 协议格式（新客户端）
                Directive directive = Directive.fromToolName(callId, toolName, args, 0);
                DirectiveData data = new DirectiveData(
                    directive.getDirectiveId(),
                    directive.getNamespace(),
                    directive.getName(),
                    directive.getPayload(),
                    directive.getTimeoutMs() > 0 ? directive.getTimeoutMs() : null
                );
                WebSocketMessage message = WebSocketMessage.directive(callId, data);
                json = MAPPER.writeValueAsString(message);
                logger.debug("Directive dispatched: {} (directiveId={})", toolName, callId);
            } else {
                // 旧协议格式（向后兼容）
                ClientToolCallData data = new ClientToolCallData(callId, toolName, args, null);
                WebSocketMessage message = WebSocketMessage.clientToolCall(callId, data);
                json = MAPPER.writeValueAsString(message);
                logger.debug("Client tool call dispatched: {} (callId={})", toolName, callId);
            }

            if (ws.isClosed()) {
                pendingCalls.remove(callId);
                clearSingleFlight(callId);
                future.completeExceptionally(
                    new RuntimeException("WebSocket connection is closed, cannot dispatch tool call"));
                return future;
            }

            ws.writeTextMessage(json).onFailure(err -> {
                pendingCalls.remove(callId);
                clearSingleFlight(callId);
                if (!future.isDone()) {
                    future.completeExceptionally(
                        new RuntimeException("Failed to send client tool call: " + err.getMessage(), err));
                }
            });

        } catch (Exception e) {
            pendingCalls.remove(callId);
            clearSingleFlight(callId);
            future.completeExceptionally(
                    new RuntimeException("Failed to serialize client tool call: " + e.getMessage(), e));
        }

        // Future 超时由 ClientToolWrapper 的 get(timeout) 控制
        future.whenComplete((result, error) -> {
            pendingCalls.remove(callId);
            clearSingleFlight(callId);
        });

        return future;
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

    /**
     * 客户端回传结果时调用此方法，完成挂起的 Future
     *
     * @param callId 调用标识
     * @param result 客户端返回的工具结果
     * @return true 如果匹配到挂起的请求
     */
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

    /**
     * 回退匹配：当端侧未携带 callId/directiveId 时，按单飞中的最近挂起调用完成。
     *
     * @param expectedToolName 期望匹配的下行 toolName（namespace.downAction）；为空则仅按单飞回退
     */
    public boolean completeLatestCall(ToolResult result, String expectedToolName) {
        String latest = latestPendingCallId;
        if (latest == null) {
            logger.warn("No latest pending call for fallback completion");
            return false;
        }

        if (expectedToolName != null && !expectedToolName.isBlank()) {
            String pendingToolName = latestPendingToolName;
            if (!expectedToolName.equals(pendingToolName)) {
                logger.warn("Fallback completion tool mismatch: expected={}, pending={}",
                    expectedToolName, pendingToolName);
                return false;
            }
        }

        return completeCall(latest, result);
    }

    public boolean completeLatestCall(ToolResult result) {
        return completeLatestCall(result, null);
    }

    /**
     * 连接断开时，取消所有挂起的调用
     */
    public void cancelAll() {
        pendingCalls.forEach((callId, future) -> {
            future.completeExceptionally(
                    new RuntimeException("WebSocket connection closed, client tool call cancelled"));
        });
        pendingCalls.clear();
        latestPendingCallId = null;
        latestPendingToolName = null;
    }

    /**
     * 获取当前挂起的调用数量
     */
    public int pendingCount() {
        return pendingCalls.size();
    }
}
