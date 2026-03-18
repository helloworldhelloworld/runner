package com.lightweightai.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


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
        String progressToken = UUID.randomUUID().toString();
        String toolName = getName();

        // 1. 注册进度监听
        Flux<ToolResultChunk> progressFlux = toolClient.getProgressRouter()
            .register(progressToken)
            .map(pn -> ToolResultChunk.progress(
                toolName,
                pn.message() != null ? pn.message() : "",
                pn.progress() != null ? pn.progress() : 0.0,
                pn.total() != null ? pn.total() : 1.0,
                pn.meta()
            ));

        // 2. 注册日志监听
        Flux<ToolResultChunk> loggingFlux = toolClient.getLoggingRouter()
            .register(progressToken)
            .map(ln -> ToolResultChunk.log(
                toolName,
                ln.level() != null ? ln.level().name() : "INFO",
                ln.logger(),
                ln.data() != null ? ln.data() : "",
                ln.meta()
            ));

        // 3. 调用工具（Mono）— 桥接 Reactor Context 中的 per-request headers 到 ThreadLocal
        //    McpHeaderContext.bind() 将 contextWrite() 写入的 header 注入 ThreadLocal，
        //    transport 的 customizeRequest 回调通过 McpHeaderContext.current() 读取。
        Flux<ToolResultChunk> resultFlux = Mono
            .deferContextual(ctx -> {
                McpHeaderContext.bind(ctx);
                return toolClient.callToolReactive(mcpTool.name(), args, progressToken);
            })
            .flatMapMany(mcpResult -> {
                String content = extractContent(mcpResult);
                boolean isError = mcpResult.isError() != null && mcpResult.isError();
                Map<String, Object> structuredContent = extractStructuredContent(mcpResult);

                ToolResult toolResult = isError
                    ? ToolResult.error(content)
                    : ToolResult.success(content, structuredContent);
                ToolResultChunk completeChunk = ToolResultChunk.complete(toolName, toolResult);

                // 如果上游把 streaming/content 片段汇总在结果中（如 params.directives[].payload.stepInfo），
                // 在最终 COMPLETE 前转为 LOG 事件，便于 demo 观察“中间过程”。
                List<ToolResultChunk> synthesizedLogs = extractStreamingStepLogs(toolName, mcpResult, structuredContent);
                if (synthesizedLogs.isEmpty()) {
                    return Flux.just(completeChunk);
                }
                return Flux.fromIterable(synthesizedLogs).concatWith(Mono.just(completeChunk));
            })
            .onErrorResume(e -> {
                logger.error("MCP tool call failed: {} - {}", toolName, e.getMessage());
                return Flux.just(ToolResultChunk.error(toolName, "MCP call failed: " + e.getMessage()));
            })
            .doFinally(signal -> {
                McpHeaderContext.unbind();
                toolClient.getProgressRouter().complete(progressToken);
                toolClient.getLoggingRouter().complete(progressToken);
            });

        // 4. 合并：progress + logging 与 result 并行
        // resultFlux 完成时 router 关闭，progress/logging flux 自然终止
        return Flux.merge(progressFlux, loggingFlux)
            .mergeWith(resultFlux);
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();


    @SuppressWarnings("unchecked")
    private Map<String, Object> extractStructuredContent(McpSchema.CallToolResult result) {
        Object structuredContent = result.structuredContent();
        if (structuredContent instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private List<ToolResultChunk> extractStreamingStepLogs(
            String toolName,
            McpSchema.CallToolResult result,
            Map<String, Object> structuredContent) {
        List<ToolResultChunk> chunks = new ArrayList<>();
        addStreamingStepLogs(chunks, toolName, structuredContent);

        if (chunks.isEmpty() && result.content() != null) {
            for (McpSchema.Content content : result.content()) {
                Map<String, Object> contentMap = OBJECT_MAPPER.convertValue(content, Map.class);
                addStreamingStepLogs(chunks, toolName, contentMap);
            }
        }

        return chunks;
    }

    @SuppressWarnings("unchecked")
    private void addStreamingStepLogs(List<ToolResultChunk> chunks, String toolName, Map<String, Object> source) {
        if (source == null) {
            return;
        }

        Object params = source.get("params");
        if (!(params instanceof Map<?, ?> paramsMap)) {
            return;
        }

        Object directives = paramsMap.get("directives");
        if (!(directives instanceof List<?> directiveList)) {
            return;
        }

        for (Object directive : directiveList) {
            if (!(directive instanceof Map<?, ?> directiveMap)) {
                continue;
            }
            Object payload = directiveMap.get("payload");
            if (!(payload instanceof Map<?, ?> payloadMap)) {
                continue;
            }
            Object stepInfo = payloadMap.get("stepInfo");
            if (stepInfo instanceof String step && !step.isBlank()) {
                chunks.add(ToolResultChunk.log(toolName, "INFO", step.strip()));
            }
        }
    }

    private String extractContent(McpSchema.CallToolResult result) {
        StringBuilder sb = new StringBuilder();

        // 1) 优先提取 TextContent
        if (result.content() != null) {
            for (McpSchema.Content content : result.content()) {
                if (content instanceof McpSchema.TextContent textContent
                        && textContent.text() != null
                        && !textContent.text().isBlank()) {
                    appendLine(sb, textContent.text());
                }
            }
        }

        // 2) 对于非 TextContent（如结构化内容块），回退到 JSON 序列化
        if (sb.isEmpty() && result.content() != null) {
            for (McpSchema.Content content : result.content()) {
                appendLine(sb, toJsonString(content));
            }
        }

        // 3) 再回退到 structuredContent
        if (sb.isEmpty() && result.structuredContent() != null) {
            appendLine(sb, toJsonString(result.structuredContent()));
        }

        return sb.toString();
    }

    private static void appendLine(StringBuilder sb, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("\n");
        }
        sb.append(value);
    }

    private static String toJsonString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String str) {
            return str;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    @Override
    public String toString() {
        return "McpToolWrapper{name='" + getName() + "', server='" + serverName + "'}";
    }
}
