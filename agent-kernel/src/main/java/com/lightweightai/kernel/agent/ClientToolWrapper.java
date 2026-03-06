package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 客户端工具包装器 — 将 Tool 接口桥接到客户端执行
 *
 * 类似 McpToolWrapper 将工具调用桥接到 MCP Server，
 * ClientToolWrapper 将工具调用通过 WebSocket 发送到客户端（浏览器/App），
 * 阻塞等待客户端上报结果后返回。
 *
 * <pre>
 * 调用流程:
 *
 *   AgentLoop                    Server                      Client (浏览器/App)
 *      │                           │                              │
 *      │  executeToolCall()        │                              │
 *      ├─────────────────────────► │                              │
 *      │                           │  WS: CLIENT_TOOL_CALL        │
 *      │                           ├─────────────────────────────►│
 *      │                           │                              │ 执行工具（如拍照/GPS/签名）
 *      │                           │  WS: CLIENT_TOOL_RESULT      │
 *      │                           │◄─────────────────────────────┤
 *      │  ToolResult               │                              │
 *      │◄──────────────────────────┤                              │
 *      │                           │                              │
 * </pre>
 *
 * <b>使用方式:</b>
 * <pre>
 * // 1. 定义客户端工具
 * ClientToolWrapper cameraTool = new ClientToolWrapper(
 *     "take_photo", "拍照并返回图片URL", ToolSchema.empty(),
 *     dispatcher, 30_000  // 30秒超时
 * );
 *
 * // 2. 注册到 ToolRegistry
 * registry.register(cameraTool);
 *
 * // 3. AgentLoop / ToolExecutor 像调用本地工具一样调用
 * ToolResult result = cameraTool.execute(Map.of("resolution", "1080p"));
 * </pre>
 */
public class ClientToolWrapper implements Tool, ToolMetadata {

    private static final Logger logger = LoggerFactory.getLogger(ClientToolWrapper.class);

    /** 默认超时 60 秒 */
    private static final long DEFAULT_TIMEOUT_MS = 60_000;

    private final String name;
    private final String description;
    private final ToolSchema schema;
    private final ClientToolDispatcher dispatcher;
    private final long timeoutMs;

    /**
     * @param name        工具名称（唯一标识）
     * @param description 工具描述（供 LLM 理解）
     * @param schema      参数 Schema
     * @param dispatcher  客户端调度器（负责 WebSocket 推送和结果等待）
     * @param timeoutMs   超时毫秒数（客户端需要在此时间内回传结果）
     */
    public ClientToolWrapper(String name, String description, ToolSchema schema,
                             ClientToolDispatcher dispatcher, long timeoutMs) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Tool name cannot be null or empty");
        }
        if (dispatcher == null) {
            throw new IllegalArgumentException("ClientToolDispatcher cannot be null");
        }
        this.name = name;
        this.description = description != null ? description : "";
        this.schema = schema != null ? schema : ToolSchema.empty();
        this.dispatcher = dispatcher;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_TIMEOUT_MS;
    }

    /**
     * 使用默认超时（60秒）
     */
    public ClientToolWrapper(String name, String description, ToolSchema schema,
                             ClientToolDispatcher dispatcher) {
        this(name, description, schema, dispatcher, DEFAULT_TIMEOUT_MS);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public ToolSchema getSchema() {
        return schema;
    }

    /**
     * 执行工具：通过 dispatcher 派发到客户端，阻塞等待结果
     */
    @Override
    public ToolResult execute(Map<String, Object> args) {
        String callId = UUID.randomUUID().toString();
        logger.info("Dispatching client tool call: {} (callId={})", name, callId);

        try {
            ToolResult result = dispatcher.dispatch(callId, name, args)
                    .get(timeoutMs, TimeUnit.MILLISECONDS);

            logger.info("Client tool result received: {} (callId={}, isError={})",
                    name, callId, result.isError());
            return result;

        } catch (TimeoutException e) {
            logger.warn("Client tool timed out: {} (callId={}, timeout={}ms)", name, callId, timeoutMs);
            return ToolResult.error("客户端工具超时: " + name + " (" + timeoutMs + "ms)");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Client tool interrupted: {} (callId={})", name, callId);
            return ToolResult.error("客户端工具调用被中断: " + name);

        } catch (Exception e) {
            logger.error("Client tool call failed: {} (callId={})", name, callId, e);
            return ToolResult.error("客户端工具调用失败: " + e.getMessage());
        }
    }

    /**
     * 客户端工具默认需要用户确认（非自动执行）
     */
    @Override
    public boolean isAutoExecute() {
        return false;
    }

    // ==================== ToolMetadata ====================

    @Override
    public String getCategory() {
        return "client";
    }

    @Override
    public List<String> getTags() {
        return List.of("client", "remote", "async");
    }

    @Override
    public String getAuthor() {
        return "client-side";
    }

    @Override
    public boolean isOpenWorld() {
        return true;
    }

    @Override
    public boolean isClientSide() {
        return true;
    }

    /**
     * 获取超时配置
     */
    public long getTimeoutMs() {
        return timeoutMs;
    }

    @Override
    public String toString() {
        return "ClientToolWrapper{name='" + name + "', timeout=" + timeoutMs + "ms}";
    }
}
