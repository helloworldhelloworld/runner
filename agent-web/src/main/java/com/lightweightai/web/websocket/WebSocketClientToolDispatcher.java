package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.agent.directive.ClientManifest;
import com.lightweightai.kernel.agent.directive.Directive;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ClientToolCallData;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.DirectiveData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 WebSocket 的客户端工具调度器
 *
 * 负责将工具调用通过 WebSocket 推送到客户端，并管理挂起请求的 Future。
 * 当客户端回传 CLIENT_TOOL_RESULT 消息时，由 ChatWebSocketHandler 调用
 * {@link #completeCall(String, ToolResult)} 来完成对应的 Future。
 *
 * <pre>
 * 生命周期绑定到一个 WebSocketSession:
 *   - 一个 session 对应一个 dispatcher 实例
 *   - session 断开时，所有挂起的 Future 都会被异常完成
 * </pre>
 */
public class WebSocketClientToolDispatcher implements ClientToolDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketClientToolDispatcher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WebSocketSession session;

    /** callId → CompletableFuture，等待客户端回传结果 */
    private final ConcurrentHashMap<String, CompletableFuture<ToolResult>> pendingCalls =
            new ConcurrentHashMap<>();

    /** 端侧上报的能力清单，非 null 表示支持 Directive 协议 */
    private volatile ClientManifest manifest;

    public WebSocketClientToolDispatcher(WebSocketSession session) {
        this.session = session;
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

            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }

        } catch (IOException e) {
            pendingCalls.remove(callId);
            future.completeExceptionally(
                    new RuntimeException("Failed to send client tool call: " + e.getMessage(), e));
        }

        // Future 超时由 ClientToolWrapper 的 get(timeout) 控制，这里不额外设置
        future.whenComplete((result, error) -> pendingCalls.remove(callId));

        return future;
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
            future.complete(result);
            return true;
        }
        logger.warn("No pending call found for callId: {}", callId);
        return false;
    }

    /**
     * 会话断开时，取消所有挂起的调用
     */
    public void cancelAll() {
        pendingCalls.forEach((callId, future) -> {
            future.completeExceptionally(
                    new RuntimeException("WebSocket session closed, client tool call cancelled"));
        });
        pendingCalls.clear();
    }

    /**
     * 获取当前挂起的调用数量
     */
    public int pendingCount() {
        return pendingCalls.size();
    }
}
