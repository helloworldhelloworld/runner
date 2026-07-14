package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.RiskLevel;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
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

    /**
     * 进度终帧(progress&gt;=total)晚于 result 到达时的有界等待窗口（issue #197）。
     * 仅在「有进度活动但尚未见终帧」时才生效，给晚到的终帧留出 route 时间；
     * 终帧先到或无进度活动时立即完成，不引入延迟。
     */
    private static final Duration PROGRESS_TERMINAL_GRACE = Duration.ofMillis(200);

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
        //
        //    使用 .cache() 确保 MCP 调用只执行一次，callTrigger + resultFlux 共享结果。
        //    doOnTerminate 关闭 router → sink complete → 已 buffer 的通知帧排空 → progressFlux/loggingFlux 完成。
        Mono<McpSchema.CallToolResult> callMono = Mono
            .deferContextual(ctx -> {
                McpHeaderContext.bind(ctx);
                Map<String, String> requestMeta = McpRequestMetaContext.current(ctx);
                return toolClient.callToolReactive(mcpTool.name(), args, progressToken, requestMeta);
            })
            .doOnTerminate(() -> {
                // 进度终帧(progress>=total)可能晚于 result 到达：用 grace 收口避免终帧被抢跑的
                // complete 丢弃(issue #197)；无进度/无终帧/出错时立即或有界完成，不挂死。
                toolClient.getProgressRouter()
                    .completeAfterTerminalOrGrace(progressToken, PROGRESS_TERMINAL_GRACE);
                toolClient.getLoggingRouter().complete(progressToken);
            })
            .cache();

        // callTrigger: 触发 MCP 调用，不发射任何 chunk，吞掉 error（由 resultFlux 处理）
        Flux<ToolResultChunk> callTrigger = callMono
            .then(Mono.<ToolResultChunk>empty())
            .onErrorResume(e -> Mono.empty())
            .flux();

        // resultFlux: 从 cached callMono 构建 COMPLETE/ERROR chunk
        Flux<ToolResultChunk> resultFlux = callMono
            .flatMapMany(mcpResult -> {
                String content = extractContent(mcpResult);
                boolean isError = mcpResult.isError() != null && mcpResult.isError();
                Map<String, Object> structuredContent = extractStructuredContent(mcpResult);

                ToolResult toolResult = isError
                    ? ToolResult.error(content)
                    : ToolResult.success(content, structuredContent);
                ToolResultChunk completeChunk = ToolResultChunk.complete(toolName, toolResult);

                // 如果上游把 streaming/content 片段汇总在结果中（如 params.directives[].payload.stepInfo），
                // 在最终 COMPLETE 前转为 LOG 事件，便于 demo 观察"中间过程"。
                List<ToolResultChunk> synthesizedLogs = extractStreamingStepLogs(toolName, mcpResult, structuredContent);
                if (synthesizedLogs.isEmpty()) {
                    return Flux.just(completeChunk);
                }
                return Flux.fromIterable(synthesizedLogs).concatWith(Mono.just(completeChunk));
            })
            .onErrorResume(e -> {
                logger.error("MCP tool call failed: {} - {}", toolName, e.getMessage());
                return Flux.just(ToolResultChunk.error(toolName, "MCP call failed: " + e.getMessage()));
            });

        // 4. Phase 1: callTrigger 启动调用 + progress/logging 并行流入
        //    doOnTerminate 关闭 router → 通知帧排空 → merge 完成
        //    Phase 2: concatWith → resultFlux 发射 COMPLETE（结构性保证在所有通知帧之后）
        return Flux.merge(callTrigger, progressFlux, loggingFlux)
            .concatWith(resultFlux)
            .doFinally(signal -> McpHeaderContext.unbind());
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

    /** _meta key carrying an explicit RiskLevel (see ADR-007). */
    static final String META_RISK_KEY = "com.lightweightai.kernel/riskLevel";

    /**
     * 恢复远程工具的风险等级（ADR-007）。
     *
     * MCP 协议无原生 risk 字段，故按优先级从工具定义恢复：
     * <ol>
     *   <li>{@code _meta["com.lightweightai.kernel/riskLevel"]} 显式声明（大小写不敏感）；</li>
     *   <li>标准 annotations 推导：readOnlyHint→SAFE，destructiveHint→SYSTEM，
     *       否则（annotations 存在但非只读非破坏）→WRITE；</li>
     *   <li>兜底 {@link RiskLevel#SAFE}（无注解的旧 server 行为不变）。</li>
     * </ol>
     */
    @Override
    public RiskLevel riskLevel() {
        RiskLevel explicit = riskFromMeta(mcpTool.meta());
        if (explicit != null) {
            return explicit;
        }
        RiskLevel fromAnnotations = riskFromAnnotations(mcpTool.annotations());
        if (fromAnnotations != null) {
            return fromAnnotations;
        }
        return RiskLevel.SAFE;
    }

    /** 从 _meta 读显式风险；缺失或非法返回 null（交由后续规则）。 */
    private RiskLevel riskFromMeta(Map<String, Object> meta) {
        if (meta == null) {
            return null;
        }
        Object value = meta.get(META_RISK_KEY);
        if (!(value instanceof String s) || s.isBlank()) {
            return null;
        }
        try {
            return RiskLevel.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            logger.warn("MCP tool '{}' has invalid {}='{}', ignoring", getName(), META_RISK_KEY, s);
            return null;
        }
    }

    /** 用 MCP 标准 annotation hints 推导风险；无 annotations 返回 null。 */
    private static RiskLevel riskFromAnnotations(McpSchema.ToolAnnotations annotations) {
        if (annotations == null) {
            return null;
        }
        if (Boolean.TRUE.equals(annotations.readOnlyHint())) {
            return RiskLevel.SAFE;
        }
        if (Boolean.TRUE.equals(annotations.destructiveHint())) {
            return RiskLevel.SYSTEM;
        }
        // annotations 存在但非只读、非破坏：有副作用但不破坏 → WRITE
        return RiskLevel.WRITE;
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
