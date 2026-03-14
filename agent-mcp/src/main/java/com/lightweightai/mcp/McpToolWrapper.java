package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 将 MCP 远程工具包装为框架的 Tool 接口（Reactive 版本）
 *
 * 通过 McpToolClient（McpAsyncClient）执行工具调用，
 * 支持将 ProgressNotification 和 LoggingNotification 作为流式事件推送。
 */
public class McpToolWrapper implements Tool, ToolMetadata {

    private static final Logger logger = LoggerFactory.getLogger(McpToolWrapper.class);

    private final McpToolClient toolClient;
    private final McpSchema.Tool mcpTool;
    private final String serverName;
    private boolean clientSide;

    /**
     * @param toolClient MCP 工具客户端（持有 McpAsyncClient + routers）
     * @param mcpTool    MCP 工具定义
     * @param serverName MCP 服务端名称
     */
    public McpToolWrapper(McpToolClient toolClient, McpSchema.Tool mcpTool, String serverName) {
        this.toolClient = toolClient;
        this.mcpTool = mcpTool;
        this.serverName = serverName;
    }

    @Override
    public String getName() {
        return mcpTool.name();
    }

    @Override
    public String getDescription() {
        return mcpTool.description();
    }

    @Override
    public ToolSchema getSchema() {
        try {
            McpSchema.JsonSchema jsonSchema = mcpTool.inputSchema();
            if (jsonSchema != null) {
                Map<String, Object> schemaMap = new HashMap<>();
                if (jsonSchema.type() != null) {
                    schemaMap.put("type", jsonSchema.type());
                }
                if (jsonSchema.properties() != null) {
                    schemaMap.put("properties", jsonSchema.properties());
                }
                if (jsonSchema.required() != null) {
                    schemaMap.put("required", jsonSchema.required());
                }
                return new ToolSchema(schemaMap);
            }
        } catch (Exception e) {
            logger.warn("Failed to parse MCP tool schema for {}: {}", getName(), e.getMessage());
        }
        return ToolSchema.empty();
    }

    @Override
    public Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
        // 使用 defer 确保整个流是 cold 的：sink 注册、工具调用都在订阅时发生
        return Flux.defer(() -> {
            String progressToken = UUID.randomUUID().toString();
            String toolName = getName();

            // 1. 注册进度监听
            Flux<ToolResultChunk> progressFlux = toolClient.getProgressRouter()
                .register(progressToken)
                .map(pn -> ToolResultChunk.progress(
                    toolName,
                    pn.message() != null ? pn.message() : "",
                    pn.progress() != null ? pn.progress() : 0.0,
                    pn.total() != null ? pn.total() : 1.0
                ))
                .onErrorResume(e -> {
                    logger.warn("Progress notification mapping failed: {}", e.getMessage());
                    return Mono.empty();
                });

            // 2. 注册日志监听（ln.data() 返回 Object，需要 String.valueOf 转换）
            Flux<ToolResultChunk> loggingFlux = toolClient.getLoggingRouter()
                .register(progressToken)
                .map(ln -> ToolResultChunk.log(
                    toolName,
                    ln.level() != null ? ln.level().name() : "INFO",
                    ln.data() != null ? String.valueOf(ln.data()) : ""
                ))
                .onErrorResume(e -> {
                    logger.warn("Logging notification mapping failed: {}", e.getMessage());
                    return Mono.empty();
                });

            // 3. 调用工具（Mono，完成时 emit COMPLETE 并关闭 router）
            Mono<ToolResultChunk> resultMono = toolClient
                .callToolReactive(mcpTool.name(), args, progressToken)
                .map(mcpResult -> {
                    String content = extractContent(mcpResult);
                    boolean isError = mcpResult.isError() != null && mcpResult.isError();
                    ToolResult toolResult = isError
                        ? ToolResult.error(content)
                        : ToolResult.success(content);
                    return ToolResultChunk.complete(toolName, toolResult);
                })
                .onErrorResume(e -> {
                    logger.error("MCP tool call failed: {} - {}", toolName, e.getMessage());
                    return Mono.just(ToolResultChunk.error(toolName, "MCP call failed: " + e.getMessage()));
                })
                .doFinally(signal -> {
                    toolClient.getProgressRouter().complete(progressToken);
                    toolClient.getLoggingRouter().complete(progressToken);
                });

            // 4. 合并：progress + logging 与 result 并行
            // resultMono 完成时 router 关闭，progress/logging flux 自然终止
            return Flux.merge(progressFlux, loggingFlux, resultMono.flux());
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(Map<String, Object> args) {
        return executeReactive(args)
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE
                      || c.getType() == ToolResultChunk.ChunkType.ERROR)
            .next()
            .map(c -> {
                if (c.getType() == ToolResultChunk.ChunkType.COMPLETE) {
                    return c.getResult();
                }
                return ToolResult.error(c.getMessage());
            })
            .block();
    }

    @Override
    public String getCategory() {
        return "mcp:" + serverName;
    }

    @Override
    public List<String> getTags() {
        return List.of("mcp", "remote", serverName);
    }

    @Override
    public String getAuthor() {
        return "mcp-server:" + serverName;
    }

    @Override
    public boolean isClientSide() {
        return clientSide;
    }

    public void setClientSide(boolean clientSide) {
        this.clientSide = clientSide;
    }

    public String getServerName() {
        return serverName;
    }

    public McpSchema.Tool getMcpTool() {
        return mcpTool;
    }

    public McpToolClient getToolClient() {
        return toolClient;
    }

    private String extractContent(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            if (content instanceof McpSchema.TextContent textContent) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(textContent.text());
            }
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "McpToolWrapper{name='" + getName() + "', server='" + serverName + "'}";
    }
}
