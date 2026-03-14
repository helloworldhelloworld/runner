package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将框架的 Tool 适配为 MCP ToolSpecification（同步和异步）
 *
 * 支持：
 * - toMcpTool() / toMcpTools() — 同步 SyncToolSpecification
 * - toAsyncMcpTool() / toAsyncMcpTools() — 异步 AsyncToolSpecification，支持代理进度转发
 */
public class McpToolAdapter {

    private static final Logger logger = LoggerFactory.getLogger(McpToolAdapter.class);

    private McpToolAdapter() {
        // Utility class
    }

    // ==================== 同步适配（保留兼容）====================

    public static McpServerFeatures.SyncToolSpecification toMcpTool(Tool tool) {
        McpSchema.JsonSchema inputSchema = toJsonSchema(tool);

        McpSchema.Tool mcpTool = new McpSchema.Tool(
            tool.getName(),
            null,
            tool.getDescription(),
            inputSchema,
            null,
            null,
            null
        );

        return new McpServerFeatures.SyncToolSpecification(
            mcpTool,
            (exchange, args) -> {
                try {
                    ToolResult result = tool.execute(args);
                    CallToolResult.Builder builder = CallToolResult.builder()
                        .content(List.of(new TextContent(result.getContent())))
                        .isError(result.isError());
                    if (result.hasStructuredContent()) {
                        builder.structuredContent(result.getStructuredContent());
                    }
                    return builder.build();
                } catch (Exception e) {
                    logger.error("Tool execution failed: {} - {}", tool.getName(), e.getMessage());
                    return CallToolResult.builder()
                        .content(List.of(new TextContent("Error: " + e.getMessage())))
                        .isError(true)
                        .build();
                }
            }
        );
    }

    public static List<McpServerFeatures.SyncToolSpecification> toMcpTools(ToolRegistry registry) {
        return registry.getEnabled().stream()
            .map(McpToolAdapter::toMcpTool)
            .collect(Collectors.toList());
    }

    // ==================== 异步适配（支持流式中间结果转发）====================

    /**
     * 将框架 Tool 转换为 MCP AsyncToolSpecification
     *
     * 使用 callHandler（而非 deprecated 的 call）获取完整 CallToolRequest，
     * 从 _meta.progressToken 提取客户端的进度令牌。
     *
     * 所有工具统一使用 executeReactive()：
     * - 本地工具：default executeReactive() 退化为单个 COMPLETE 事件
     * - 自定义 Reactive 工具：PROGRESS/LOG/COMPLETE 中间事件通过 MCP 通知转发
     * - McpToolWrapper（代理）：上游进度/日志通知实时转发给下游客户端
     */
    public static McpServerFeatures.AsyncToolSpecification toAsyncMcpTool(Tool tool) {
        McpSchema.JsonSchema inputSchema = toJsonSchema(tool);

        McpSchema.Tool mcpTool = new McpSchema.Tool(
            tool.getName(),
            null,
            tool.getDescription(),
            inputSchema,
            null,
            null,
            null
        );

        return new McpServerFeatures.AsyncToolSpecification(
            mcpTool,
            null, // deprecated call handler
            (exchange, request) -> {
                Map<String, Object> args = request.arguments() != null
                    ? request.arguments() : Map.of();

                // 从 CallToolRequest._meta 提取客户端的 progressToken
                Object progressToken = null;
                if (request.meta() != null) {
                    progressToken = request.meta().get("progressToken");
                }

                return executeWithStreaming(exchange, tool, args, progressToken);
            }
        );
    }

    /**
     * 将 ToolRegistry 中所有启用的工具转换为 AsyncToolSpecification 列表
     */
    public static List<McpServerFeatures.AsyncToolSpecification> toAsyncMcpTools(ToolRegistry registry) {
        return registry.getEnabled().stream()
            .map(McpToolAdapter::toAsyncMcpTool)
            .collect(Collectors.toList());
    }

    // ==================== 辅助方法 ====================

    /**
     * 通用流式工具执行：使用 executeReactive() 并将中间事件作为 MCP 通知转发
     *
     * 工作流程：
     * 1. 调用 tool.executeReactive(args) 获取 Flux&lt;ToolResultChunk&gt;
     * 2. PROGRESS 事件 → exchange.progressNotification()（携带客户端 progressToken）
     * 3. LOG 事件 → exchange.loggingNotification()
     * 4. COMPLETE/ERROR → 转换为 CallToolResult 返回
     *
     * @param exchange       MCP 服务端交换上下文
     * @param tool           框架工具（任意类型）
     * @param args           工具参数
     * @param progressToken  客户端请求中的 progressToken（可能为 null）
     */
    private static Mono<CallToolResult> executeWithStreaming(
            McpAsyncServerExchange exchange,
            Tool tool,
            Map<String, Object> args,
            Object progressToken) {
        return tool.executeReactive(args)
            .flatMap(chunk -> {
                if (chunk.getType() == ToolResultChunk.ChunkType.PROGRESS
                        && progressToken != null) {
                    return exchange.progressNotification(
                        new McpSchema.ProgressNotification(
                            progressToken,
                            chunk.getProgress(),
                            chunk.getTotal(),
                            chunk.getMessage()
                        )
                    ).thenReturn(chunk);
                }
                if (chunk.getType() == ToolResultChunk.ChunkType.LOG) {
                    return exchange.loggingNotification(
                        new McpSchema.LoggingMessageNotification(
                            McpSchema.LoggingLevel.INFO,
                            tool.getName(),
                            chunk.getMessage()
                        )
                    ).thenReturn(chunk);
                }
                return Mono.just(chunk);
            })
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE
                      || c.getType() == ToolResultChunk.ChunkType.ERROR)
            .next()
            .map(c -> {
                if (c.getType() == ToolResultChunk.ChunkType.COMPLETE && c.getResult() != null) {
                    return toCallToolResult(c.getResult());
                }
                return CallToolResult.builder()
                    .content(List.of(new TextContent(
                        c.getMessage() != null ? c.getMessage() : "Tool execution failed")))
                    .isError(true)
                    .build();
            })
            .switchIfEmpty(Mono.just(CallToolResult.builder()
                .content(List.of(new TextContent("No result from tool")))
                .isError(true)
                .build()))
            .onErrorResume(e -> {
                logger.error("Tool execution failed: {} - {}", tool.getName(), e.getMessage());
                return Mono.just(CallToolResult.builder()
                    .content(List.of(new TextContent("Error: " + e.getMessage())))
                    .isError(true)
                    .build());
            });
    }

    static CallToolResult toCallToolResult(ToolResult result) {
        CallToolResult.Builder builder = CallToolResult.builder()
            .content(List.of(new TextContent(result.getContent())))
            .isError(result.isError());
        if (result.hasStructuredContent()) {
            builder.structuredContent(result.getStructuredContent());
        }
        return builder.build();
    }

    public static Map<String, Boolean> extractAnnotations(Tool tool) {
        if (tool instanceof ToolMetadata metadata) {
            return Map.of(
                "readOnlyHint", metadata.isReadOnly(),
                "destructiveHint", metadata.isDestructive(),
                "idempotentHint", metadata.isIdempotent(),
                "openWorldHint", metadata.isOpenWorld()
            );
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private static McpSchema.JsonSchema toJsonSchema(Tool tool) {
        Map<String, Object> schemaMap = tool.getSchema().toMap();
        return new McpSchema.JsonSchema(
            (String) schemaMap.getOrDefault("type", "object"),
            (Map<String, Object>) schemaMap.get("properties"),
            (List<String>) schemaMap.get("required"),
            null,
            null,
            null
        );
    }
}
