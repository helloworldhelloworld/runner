package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSource;
import com.lightweightai.mcp.transport.WebSocketMcpClientTransport;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * MCP 工具客户端（Reactive 版本）
 *
 * 基于 McpAsyncClient，支持 ProgressNotification 和 LoggingNotification 路由。
 * 实现 ToolSource 接口，可直接作为 ToolRegistry 的工具来源。
 */
public class McpToolClient implements ToolSource, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(McpToolClient.class);

    private final String serverName;
    private final McpClientTransport transport;
    private final McpAsyncClient asyncClient;
    private final ProgressNotificationRouter progressRouter;
    private final LoggingNotificationRouter loggingRouter;
    private List<McpToolWrapper> discoveredTools;

    /** registerTools 时记录的目标 registry，用于 tools/list_changed 自动刷新 */
    private volatile ToolRegistry registeredRegistry;
    private volatile ToolsChangedListener toolsChangedListener;
    private volatile ScheduledExecutorService refreshScheduler;

    private McpToolClient(Builder builder) {
        this.serverName = builder.serverName;
        this.transport = builder.transport;
        this.progressRouter = new ProgressNotificationRouter();
        this.loggingRouter = new LoggingNotificationRouter();

        this.asyncClient = McpClient.async(builder.transport)
            .requestTimeout(builder.timeout)
            .progressConsumer(notification -> {
                progressRouter.route(notification);
                return Mono.empty();
            })
            .loggingConsumer(notification -> {
                loggingRouter.route(notification);
                return Mono.empty();
            })
            .toolsChangeConsumer(tools -> {
                applyToolListChange(tools);
                return Mono.empty();
            })
            .build();
    }

    /**
     * 初始化连接并执行 MCP 握手
     */
    public McpToolClient initialize() {
        asyncClient.initialize().block();
        logger.info("MCP client connected to server '{}'", serverName);
        return this;
    }

    // ==================== ToolSource 接口 ====================

    @Override
    public List<Tool> discoverTools() {
        return new ArrayList<>(discoverMcpTools());
    }

    // ==================== MCP 工具发现 ====================

    public List<McpToolWrapper> discoverMcpTools() {
        McpSchema.ListToolsResult result = asyncClient.listTools().block();
        discoveredTools = new ArrayList<>();

        if (result != null && result.tools() != null) {
            for (McpSchema.Tool mcpTool : result.tools()) {
                McpToolWrapper wrapper = new McpToolWrapper(this, mcpTool, serverName);
                discoveredTools.add(wrapper);
                logger.debug("Discovered MCP tool: {} from server '{}'", mcpTool.name(), serverName);
            }
        }

        logger.info("Discovered {} tools from MCP server '{}'",
                discoveredTools.size(), serverName);
        return discoveredTools;
    }

    public List<McpToolWrapper> discoverMcpTools(Predicate<Tool> filter) {
        return discoverMcpTools().stream()
            .filter(filter)
            .toList();
    }

    public int registerTools(ToolRegistry registry) {
        return registerTools(registry, tool -> true);
    }

    public int registerTools(ToolRegistry registry, Predicate<Tool> filter) {
        this.registeredRegistry = registry;
        List<McpToolWrapper> tools = discoverMcpTools(filter);
        for (McpToolWrapper tool : tools) {
            registry.register(tool);
        }
        logger.info("Registered {} MCP tools from '{}' into ToolRegistry", tools.size(), serverName);
        return tools.size();
    }

    /**
     * 主动触发工具列表刷新（重新 tools/list），适用于所有 Transport。
     *
     * <p>与缓存的 {@code discoveredTools} 做差量对比：新增工具注册到 registry，
     * 移除工具从 registry 注销，并触发 {@link ToolsChangedListener}。
     *
     * @param registry 目标 ToolRegistry
     * @return 刷新后的工具数量
     */
    public int refreshTools(ToolRegistry registry) {
        this.registeredRegistry = registry;
        Set<String> before = currentToolNames();
        List<McpToolWrapper> tools = discoverMcpTools();
        Set<String> after = tools.stream().map(McpToolWrapper::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        for (McpToolWrapper tool : tools) {
            registry.register(tool);
        }
        Set<String> removed = new LinkedHashSet<>(before);
        removed.removeAll(after);
        for (String name : removed) {
            registry.unregister(name);
        }

        Set<String> added = new LinkedHashSet<>(after);
        added.removeAll(before);
        notifyToolsChanged(added, removed);
        return tools.size();
    }

    // ==================== Reactive 工具调用 ====================

    /**
     * Reactive 工具调用（供 McpToolWrapper 使用）
     *
     * @param name          工具名
     * @param args          参数
     * @param progressToken 进度 token（传入 _meta.progressToken）
     * @return Mono of CallToolResult
     */
    public Mono<McpSchema.CallToolResult> callToolReactive(
            String name, Map<String, Object> args, String progressToken) {
        return callToolReactive(name, args, progressToken, Map.of());
    }

    /**
     * Reactive 工具调用（含请求级 metadata）
     *
     * <p>{@code requestMeta} 通过 JSON-RPC 消息体的 {@code _meta.requestHeaders} 传递，
     * 与 progressToken 一并写入 {@code _meta}。对任意 transport 均生效（随消息体传输）。
     *
     * @param name          工具名
     * @param args          参数
     * @param progressToken 进度 token（写入 {@code _meta.progressToken}）
     * @param requestMeta   请求级 metadata（写入 {@code _meta.requestHeaders}），可为空
     */
    public Mono<McpSchema.CallToolResult> callToolReactive(
            String name, Map<String, Object> args, String progressToken,
            Map<String, String> requestMeta) {
        return asyncClient.callTool(
            new McpSchema.CallToolRequest(name, args, buildCallMeta(progressToken, requestMeta)));
    }

    /**
     * 组装 {@code tools/call} 的 {@code _meta}：progressToken + 请求级 requestHeaders。
     */
    static Map<String, Object> buildCallMeta(String progressToken, Map<String, String> requestMeta) {
        Map<String, Object> meta = new HashMap<>();
        if (progressToken != null) {
            meta.put("progressToken", progressToken);
        }
        if (requestMeta != null && !requestMeta.isEmpty()) {
            meta.put(McpRequestMetaContext.META_FIELD, new HashMap<>(requestMeta));
        }
        return meta;
    }

    // ==================== Router 访问 ====================

    public ProgressNotificationRouter getProgressRouter() {
        return progressRouter;
    }

    public LoggingNotificationRouter getLoggingRouter() {
        return loggingRouter;
    }

    // ==================== 其他方法 ====================

    public List<McpToolWrapper> getDiscoveredTools() {
        return discoveredTools != null ? discoveredTools : List.of();
    }

    public String getServerName() {
        return serverName;
    }

    public McpAsyncClient getAsyncClient() {
        return asyncClient;
    }

    /**
     * 查询当前 Transport 是否处于连接状态。
     * WebSocket Transport 返回实际连接状态；其余 Transport（HTTP/SSE/STDIO）始终返回 true。
     */
    public boolean isConnected() {
        if (transport instanceof WebSocketMcpClientTransport ws) {
            return ws.isConnected();
        }
        return true;
    }

    /**
     * 注册工具变更监听器。当 MCP Server 推送 {@code notifications/tools/list_changed}
     * （由 SDK 解析并回调）或调用 {@link #refreshTools} 检测到差量时触发。
     */
    public void onToolsChanged(ToolsChangedListener listener) {
        this.toolsChangedListener = listener;
    }

    @FunctionalInterface
    public interface ToolsChangedListener {
        /**
         * @param added   新增的工具名集合
         * @param removed 移除的工具名集合
         */
        void onChanged(Set<String> added, Set<String> removed);
    }

    /**
     * 启动工具列表定时轮询兜底（当 MCP Server 不推送 tools/list_changed 时）。
     * interval &lt;= 0 时不启动。每次轮询调用 {@link #refreshTools}。
     */
    public synchronized void startToolRefreshPolling(Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            return;
        }
        if (refreshScheduler != null) {
            return;
        }
        refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-tool-refresh-" + serverName);
            t.setDaemon(true);
            return t;
        });
        long millis = interval.toMillis();
        refreshScheduler.scheduleAtFixedRate(() -> {
            try {
                ToolRegistry registry = this.registeredRegistry;
                if (registry != null) {
                    refreshTools(registry);
                }
            } catch (Exception e) {
                logger.warn("Tool refresh polling failed for '{}': {}", serverName, e.getMessage());
            }
        }, millis, millis, TimeUnit.MILLISECONDS);
    }

    private Set<String> currentToolNames() {
        if (discoveredTools == null) {
            return new LinkedHashSet<>();
        }
        return discoveredTools.stream().map(McpToolWrapper::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void notifyToolsChanged(Set<String> added, Set<String> removed) {
        if (added.isEmpty() && removed.isEmpty()) {
            return;
        }
        logger.info("MCP tools changed on '{}': +{} -{}", serverName, added, removed);
        ToolsChangedListener listener = this.toolsChangedListener;
        if (listener != null) {
            listener.onChanged(added, removed);
        }
    }

    /**
     * SDK toolsChangeConsumer 回调：收到 tools/list_changed 后 SDK 传入最新工具列表。
     * 据此更新 registry 并触发监听器（基于工具名做差量）。
     */
    private void applyToolListChange(List<McpSchema.Tool> tools) {
        Set<String> before = currentToolNames();

        List<McpToolWrapper> wrappers = new ArrayList<>();
        Set<String> after = new LinkedHashSet<>();
        for (McpSchema.Tool mcpTool : tools) {
            wrappers.add(new McpToolWrapper(this, mcpTool, serverName));
            after.add(mcpTool.name());
        }
        this.discoveredTools = wrappers;

        ToolRegistry registry = this.registeredRegistry;
        if (registry != null) {
            for (McpToolWrapper tool : wrappers) {
                registry.register(tool);
            }
            Set<String> removed = new LinkedHashSet<>(before);
            removed.removeAll(after);
            for (String name : removed) {
                registry.unregister(name);
            }
        }

        Set<String> added = new LinkedHashSet<>(after);
        added.removeAll(before);
        Set<String> removed = new LinkedHashSet<>(before);
        removed.removeAll(after);
        notifyToolsChanged(added, removed);
    }

    @Override
    public void close() {
        ScheduledExecutorService scheduler = this.refreshScheduler;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        asyncClient.close();
        logger.info("MCP client for server '{}' closed", serverName);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String serverName = "mcp-server";
        private McpClientTransport transport;
        private Duration timeout = Duration.ofSeconds(30);

        public Builder serverName(String serverName) {
            this.serverName = serverName;
            return this;
        }

        public Builder transport(McpClientTransport transport) {
            this.transport = transport;
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public McpToolClient build() {
            if (transport == null) {
                throw new IllegalStateException("Transport is required");
            }
            return new McpToolClient(this);
        }
    }
}
