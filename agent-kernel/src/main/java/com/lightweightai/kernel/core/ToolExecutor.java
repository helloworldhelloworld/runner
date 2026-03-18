package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.plugin.FunctionResult;
import com.lightweightai.kernel.plugin.PluginFunction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Executes tool calls from LLM using registered tools and plugins.
 *
 * Supports two registration paths:
 * 1. {@link Tool} objects via {@link ToolRegistry} (preferred)
 * 2. {@link PluginFunction} objects via legacy registerFunction methods
 *
 * When executing, ToolRegistry is checked first, then the legacy function registry.
 */
public class ToolExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ToolExecutor.class);

    private final Map<String, PluginFunction> functionRegistry;
    private final ToolRegistry toolRegistry;

    /**
     * Create a new ToolExecutor with its own ToolRegistry
     */
    public ToolExecutor() {
        this.functionRegistry = new HashMap<>();
        this.toolRegistry = new ToolRegistry();
    }

    /**
     * Create a new ToolExecutor backed by an existing ToolRegistry
     *
     * @param toolRegistry The tool registry to use
     */
    public ToolExecutor(ToolRegistry toolRegistry) {
        this.functionRegistry = new HashMap<>();
        this.toolRegistry = toolRegistry != null ? toolRegistry : new ToolRegistry();
    }

    /**
     * Register a Tool object
     *
     * @param tool The tool to register
     */
    public void registerTool(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("Tool cannot be null");
        }
        toolRegistry.register(tool);
    }

    /**
     * Register multiple Tool objects
     *
     * @param tools The tools to register
     */
    public void registerTools(List<Tool> tools) {
        if (tools != null) {
            toolRegistry.registerAll(tools);
        }
    }

    /**
     * Register annotated tool object (scans @ToolFunction methods)
     *
     * @param target Object containing @ToolFunction annotated methods
     * @return Number of tools registered
     */
    public int registerObject(Object target) {
        return toolRegistry.registerObject(target);
    }

    /**
     * Get the underlying ToolRegistry
     *
     * @return The tool registry
     */
    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    /**
     * Register a plugin's functions (legacy support)
     *
     * @param pluginFunctions Map of function name to PluginFunction
     */
    public void registerFunctions(Map<String, PluginFunction> pluginFunctions) {
        if (pluginFunctions != null) {
            functionRegistry.putAll(pluginFunctions);
        }
    }

    /**
     * Register a single function (legacy support)
     *
     * @param name     Function name
     * @param function PluginFunction instance
     */
    public void registerFunction(String name, PluginFunction function) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Function name cannot be null or empty");
        }
        if (function == null) {
            throw new IllegalArgumentException("Function cannot be null");
        }

        functionRegistry.put(name, function);
    }

    /**
     * Execute a single tool call.
     * Checks ToolRegistry first, then falls back to legacy function registry.
     *
     * @param toolCall The tool call request from LLM
     * @return ToolResult with the execution result or error
     */
    public ToolResult executeToolCall(ToolCall toolCall) {
        if (toolCall == null) {
            throw new IllegalArgumentException("ToolCall cannot be null");
        }

        try {
            // Check ToolRegistry first (preferred path)
            if (toolRegistry.has(toolCall.getName()) && toolRegistry.isEnabled(toolCall.getName())) {
                Tool tool = toolRegistry.get(toolCall.getName()).orElse(null);
                if (tool != null) {
                    return tool.execute(toolCall.getArguments())
                        .withToolUseId(toolCall.getId());
                }
            }

            // Fall back to legacy function registry
            PluginFunction function = functionRegistry.get(toolCall.getName());
            if (function == null) {
                return ToolResult.error(
                    toolCall.getId(),
                    "Tool not found: " + toolCall.getName()
                );
            }

            // Execute the function
            FunctionResult result = function.execute(toolCall.getArguments());

            // Convert to ToolResult
            if (result.isSuccess()) {
                return ToolResult.success(toolCall.getId(), result.getValue());
            } else {
                return ToolResult.error(toolCall.getId(), result.getError());
            }

        } catch (Exception e) {
            return ToolResult.error(
                toolCall.getId(),
                "Execution failed: " + e.getMessage()
            );
        }
    }

    // ==================== 带 Context 的执行（支持客户端工具路由）====================

    /**
     * 执行单个工具调用，支持客户端工具路由
     *
     * 如果工具实现了 ToolMetadata 且 isClientSide() 为 true，
     * 并且 context 中有可用的 ClientToolDispatcher，
     * 则将工具调用派发到客户端执行，而非在服务端调用 tool.execute()。
     *
     * @param toolCall 工具调用请求
     * @param context  执行上下文（携带客户端调度器），可为 null
     * @return 执行结果
     */
    public ToolResult executeToolCall(ToolCall toolCall, ToolExecutionContext context) {
        if (toolCall == null) {
            throw new IllegalArgumentException("ToolCall cannot be null");
        }

        try {
            // Check if this tool should be dispatched to client
            if (context != null && context.hasClientDispatcher()) {
                Tool tool = toolRegistry.get(toolCall.getName()).orElse(null);
                if (tool != null && isClientSideTool(tool)) {
                    return executeOnClient(toolCall, context);
                }
            }

            // Fall through to normal server-side execution
            return executeToolCall(toolCall);

        } catch (Exception e) {
            return ToolResult.error(toolCall.getId(),
                "Execution failed: " + e.getMessage());
        }
    }

    /**
     * 批量执行（带 context）
     */
    public List<ToolResult> executeToolCalls(List<ToolCall> toolCalls, ToolExecutionContext context) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
            .map(tc -> executeToolCall(tc, context))
            .collect(Collectors.toList());
    }

    /**
     * 异步批量执行（带 context）
     */
    public CompletableFuture<List<ToolResult>> executeToolCallsAsync(
            List<ToolCall> toolCalls, ToolExecutionContext context) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<CompletableFuture<ToolResult>> futures = toolCalls.stream()
            .map(tc -> CompletableFuture.supplyAsync(() -> executeToolCall(tc, context)))
            .collect(Collectors.toList());
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }

    /**
     * 判断工具是否标记为客户端执行
     */
    private boolean isClientSideTool(Tool tool) {
        return tool instanceof ToolMetadata && ((ToolMetadata) tool).isClientSide();
    }

    /**
     * 将工具调用派发到客户端执行
     */
    private ToolResult executeOnClient(ToolCall toolCall, ToolExecutionContext context) {
        String callId = UUID.randomUUID().toString();
        logger.info("Dispatching to client: {} (callId={})", toolCall.getName(), callId);

        try {
            ToolResult result = context.getClientDispatcher()
                .dispatch(callId, toolCall.getName(), toolCall.getArguments())
                .get(context.getClientToolTimeoutMs(), TimeUnit.MILLISECONDS);
            return result.withToolUseId(toolCall.getId());

        } catch (TimeoutException e) {
            logger.warn("Client tool timed out: {} (callId={})", toolCall.getName(), callId);
            return ToolResult.error(toolCall.getId(),
                "Client tool timed out: " + toolCall.getName());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.error(toolCall.getId(),
                "Client tool interrupted: " + toolCall.getName());

        } catch (Exception e) {
            logger.error("Client tool failed: {} (callId={})", toolCall.getName(), callId, e);
            return ToolResult.error(toolCall.getId(),
                "Client tool failed: " + e.getMessage());
        }
    }

    // ==================== 无 Context 的执行（原有 API）====================

    /**
     * Execute multiple tool calls sequentially
     *
     * @param toolCalls List of tool call requests
     * @return List of tool results in the same order
     */
    public List<ToolResult> executeToolCalls(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }

        return toolCalls.stream()
            .map(this::executeToolCall)
            .collect(Collectors.toList());
    }

    /**
     * Execute multiple tool calls in parallel
     *
     * @param toolCalls List of tool call requests
     * @return CompletableFuture of list of tool results
     */
    public CompletableFuture<List<ToolResult>> executeToolCallsAsync(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<CompletableFuture<ToolResult>> futures = toolCalls.stream()
            .map(toolCall -> CompletableFuture.supplyAsync(() -> executeToolCall(toolCall)))
            .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }

    // ==================== Reactive 执行 ====================

    /**
     * 单工具 reactive 调用，返回流式工具结果事件
     */
    public Flux<ToolResultChunk> executeToolCallReactive(ToolCall toolCall) {
        if (toolCall == null) {
            return Flux.error(new IllegalArgumentException("ToolCall cannot be null"));
        }
        Tool tool = toolRegistry.get(toolCall.getName()).orElse(null);
        if (tool == null || !toolRegistry.isEnabled(toolCall.getName())) {
            return Flux.just(ToolResultChunk.error(toolCall.getName(),
                    "Tool not found: " + toolCall.getName())
                .withToolCallId(toolCall.getId()));
        }
        return tool.executeReactive(toolCall.getArguments())
            .map(chunk -> chunk.withToolCallId(toolCall.getId()));
    }

    /**
     * 多工具并行 reactive 调用（Flux.merge 自然并发）
     */
    public Flux<ToolResultChunk> executeToolCallsReactive(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Flux.empty();
        }
        List<Flux<ToolResultChunk>> fluxes = toolCalls.stream()
            .map(this::executeToolCallReactive)
            .toList();
        return Flux.merge(fluxes);
    }

    /**
     * 收集所有最终结果（用于喂回 LLM）
     */
    public Mono<List<ToolResult>> executeToolCallsAndCollect(List<ToolCall> toolCalls) {
        return executeToolCallsReactive(toolCalls)
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE
                      || c.getType() == ToolResultChunk.ChunkType.ERROR)
            .map(c -> {
                if (c.getType() == ToolResultChunk.ChunkType.COMPLETE) {
                    return c.getResult().withToolUseId(c.getToolCallId());
                }
                return ToolResult.error(c.getToolCallId(), c.getMessage());
            })
            .collectList();
    }

    /**
     * Check if a tool is registered (in either registry)
     *
     * @param toolName Tool name to check
     * @return true if the tool is registered
     */
    public boolean hasFunction(String toolName) {
        return toolRegistry.has(toolName) || functionRegistry.containsKey(toolName);
    }

    /**
     * Get the total number of registered tools and functions
     *
     * @return Total count
     */
    public int getFunctionCount() {
        return toolRegistry.enabledCount() + functionRegistry.size();
    }

    /**
     * Get tool definitions in JSON schema format for LLM.
     * Merges definitions from ToolRegistry and legacy function registry.
     *
     * @return List of tool definitions as Map
     */
    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> definitions = new ArrayList<>();

        // Add Tool definitions from ToolRegistry
        definitions.addAll(toolRegistry.getToolDefinitions());

        // Add legacy PluginFunction definitions
        definitions.addAll(functionRegistry.values().stream()
            .map(PluginFunction::toJsonSchema)
            .collect(Collectors.toList()));

        return definitions;
    }

    /**
     * Clear all registered tools and functions
     */
    public void clear() {
        toolRegistry.clear();
        functionRegistry.clear();
    }

    @Override
    public String toString() {
        return "ToolExecutor{tools=" + toolRegistry.enabledCount()
            + ", functions=" + functionRegistry.size() + "}";
    }
}
