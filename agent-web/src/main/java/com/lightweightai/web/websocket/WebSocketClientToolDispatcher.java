package com.lightweightai.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.ClientToolDispatcher;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage;
import com.lightweightai.kernel.llm.websocket.WebSocketMessage.ClientToolCallData;
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

    public WebSocketClientToolDispatcher(WebSocketSession session) {
        this.session = session;
    }

    @Override
    public CompletableFuture<ToolResult> dispatch(String callId, String toolName,
                                                   Map<String, Object> args) {
        CompletableFuture<ToolResult> future = new CompletableFuture<>();
        pendingCalls.put(callId, future);

        // 构造 CLIENT_TOOL_CALL 消息并推送到客户端
        try {
            ClientToolCallData data = new ClientToolCallData(callId, toolName, args, null);
            WebSocketMessage message = WebSocketMessage.clientToolCall(callId, data);
            String json = MAPPER.writeValueAsString(message);

            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }

            logger.debug("Client tool call dispatched: {} (callId={})", toolName, callId);

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
