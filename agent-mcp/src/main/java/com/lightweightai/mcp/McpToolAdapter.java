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

    // ==================== 异步适配（支持进度转发）====================

    /**
     * 将框架 Tool 转换为 MCP AsyncToolSpecification
     *
     * 如果 Tool 是 McpToolWrapper（代理场景），会将上游进度通知转发给下游客户端。
     * 普通本地工具则直接在 Mono 中执行。
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
            (exchange, args) -> {
                if (tool instanceof McpToolWrapper wrapper) {
                    return executeWithProgressForwarding(exchange, wrapper, args);
                }
                // 普通本地工具：直接执行
                return Mono.fromCallable(() -> tool.execute(args))
                    .map(McpToolAdapter::toCallToolResult)
                    .onErrorResume(e -> {
                        logger.error("Tool execution failed: {} - {}", tool.getName(), e.getMessage());
                        return Mono.just(CallToolResult.builder()
                            .content(List.of(new TextContent("Error: " + e.getMessage())))
                            .isError(true)
                            .build());
                    });
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
     * MCP 代理场景：执行上游 MCP 工具调用，同时将进度/日志转发给下游客户端
     */
    private static Mono<CallToolResult> executeWithProgressForwarding(
            McpAsyncServerExchange exchange,
            McpToolWrapper wrapper,
            Map<String, Object> args) {
        return wrapper.executeReactive(args)
            .flatMap(chunk -> {
                if (chunk.getType() == ToolResultChunk.ChunkType.PROGRESS) {
                    return exchange.progressNotification(
                        new McpSchema.ProgressNotification(
                            null,
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
                            wrapper.getName(),
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
