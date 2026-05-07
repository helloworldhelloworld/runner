package com.lightweightai.kernel.core;

import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;

import java.util.Collections;
import java.util.Map;

/**
 * 全链路统一流式事件
 *
 * 合并 LLM 流式输出和工具流式输出，用于整个 Reactive 管道的统一事件类型。
 */
public class StreamEvent {

    public enum EventType {
        TEXT_DELTA,        // LLM 文本片段
        TOOL_CALL_START,   // LLM 决定调用工具
        TOOL_PROGRESS,     // 工具执行进度 (来自 MCP ProgressNotification)
        TOOL_LOG,          // 工具执行日志 (来自 MCP LoggingNotification)
        TOOL_RESULT,       // 工具执行完成
        TOOL_ERROR,        // 工具执行错误
        LLM_COMPLETE,      // LLM 最终响应(无更多工具调用)
        ERROR,             // 管道错误
        POST_PROCESS_DATA, // 后处理器注入数据 (卡片、标注、风控信号等)
        TRACE,             // 调用链追踪日志

        // Orchestrator 生命周期
        AGENT_ROUTE,       // Orchestrator 路由决策：选中了哪个 agent
        AGENT_INTERRUPT,   // 用户打断：记录被打断时的 phase 和已生成文本长度
        AGENT_RESUME,      // 从打断处接续：记录新输入和恢复的上下文大小

        // Subagent 生命周期
        SUBAGENT_SPAWN,    // 子 agent 创建
        SUBAGENT_COMPLETE, // 子 agent 完成
        SUBAGENT_ERROR,    // 子 agent 失败
        SUBAGENT_CANCELLED, // 子 agent 被取消（级联停止）

        // Task 编排生命周期（DAG 节点级事件）
        TASK_START,        // 任务开始执行
        TASK_COMPLETE,     // 任务执行完成（携带 TaskResult payload）
        TASK_SKIPPED,      // 任务被 guard 跳过
        TASK_ERROR         // 任务执行失败
    }

    private final EventType type;
    private final String textDelta;
    private final ToolCall toolCall;
    private final ToolResultChunk chunk;
    private final LLMResponse response;
    private final Throwable error;
    private final String category;
    private final Map<String, Object> data;
    private final Map<String, Object> metadata;
    private final String tracePhase;
    private final String traceMessage;
    private final long traceTimestamp;

    private StreamEvent(EventType type, String textDelta, ToolCall toolCall,
                        ToolResultChunk chunk, LLMResponse response, Throwable error) {
        this(type, textDelta, toolCall, chunk, response, error, null, null, null);
    }

    private StreamEvent(EventType type, String textDelta, ToolCall toolCall,
                        ToolResultChunk chunk, LLMResponse response, Throwable error,
                        String category, Map<String, Object> data) {
        this(type, textDelta, toolCall, chunk, response, error, category, data, null);
    }

    private StreamEvent(EventType type, String textDelta, ToolCall toolCall,
                        ToolResultChunk chunk, LLMResponse response, Throwable error,
                        String category, Map<String, Object> data, Map<String, Object> metadata) {
        this(type, textDelta, toolCall, chunk, response, error, category, data, metadata, null, null, 0);
    }

    private StreamEvent(EventType type, String textDelta, ToolCall toolCall,
                        ToolResultChunk chunk, LLMResponse response, Throwable error,
                        String category, Map<String, Object> data, Map<String, Object> metadata,
                        String tracePhase, String traceMessage, long traceTimestamp) {
        this.type = type;
        this.textDelta = textDelta;
        this.toolCall = toolCall;
        this.chunk = chunk;
        this.response = response;
        this.error = error;
        this.category = category;
        this.data = data;
        this.metadata = metadata;
        this.tracePhase = tracePhase;
        this.traceMessage = traceMessage;
        this.traceTimestamp = traceTimestamp;
    }

    public static StreamEvent textDelta(String delta) {
        return new StreamEvent(EventType.TEXT_DELTA, delta, null, null, null, null);
    }

    public static StreamEvent textDelta(String delta, Map<String, Object> metadata) {
        return new StreamEvent(EventType.TEXT_DELTA, delta, null, null, null, null,
                null, null, metadata != null ? Collections.unmodifiableMap(metadata) : null);
    }

    public static StreamEvent toolCallStart(ToolCall call) {
        return new StreamEvent(EventType.TOOL_CALL_START, null, call, null, null, null);
    }

    public static StreamEvent toolProgress(ToolResultChunk chunk) {
        return new StreamEvent(EventType.TOOL_PROGRESS, null, null, chunk, null, null);
    }

    public static StreamEvent toolLog(ToolResultChunk chunk) {
        return new StreamEvent(EventType.TOOL_LOG, null, null, chunk, null, null);
    }

    public static StreamEvent toolResult(ToolResultChunk chunk) {
        return new StreamEvent(EventType.TOOL_RESULT, null, null, chunk, null, null);
    }

    public static StreamEvent toolError(ToolResultChunk chunk) {
        return new StreamEvent(EventType.TOOL_ERROR, null, null, chunk, null, null);
    }

    public static StreamEvent llmComplete(LLMResponse response) {
        return new StreamEvent(EventType.LLM_COMPLETE, null, null, null, response, null);
    }

    public static StreamEvent error(Throwable error) {
        return new StreamEvent(EventType.ERROR, null, null, null, null, error);
    }

    public static StreamEvent postProcessData(String category, Map<String, Object> data) {
        return new StreamEvent(EventType.POST_PROCESS_DATA, null, null, null, null, null,
                category, data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap());
    }

    /**
     * 调用链追踪事件
     *
     * @param phase   阶段名称（如 gateway.enter, agent.prompt, llm.request, tool.execute 等）
     * @param message 描述信息
     */
    public static StreamEvent trace(String phase, String message) {
        return new StreamEvent(EventType.TRACE, null, null, null, null, null,
                null, null, null, phase, message, System.currentTimeMillis());
    }

    /** 带附加数据的 trace */
    public static StreamEvent trace(String phase, String message, Map<String, Object> data) {
        return new StreamEvent(EventType.TRACE, null, null, null, null, null,
                null, data, null, phase, message, System.currentTimeMillis());
    }

    // ==================== Orchestrator 生命周期事件 ====================

    /** Orchestrator 路由决策 */
    public static StreamEvent agentRoute(String agentId, String sessionId) {
        return new StreamEvent(EventType.AGENT_ROUTE, null, null, null, null, null,
                null, Map.of("agentId", agentId, "sessionId", sessionId), null,
                "agent.route", agentId, System.currentTimeMillis());
    }

    /** 用户打断 */
    public static StreamEvent agentInterrupt(String runId, String phase, int textLength) {
        return new StreamEvent(EventType.AGENT_INTERRUPT, null, null, null, null, null,
                null, Map.of("runId", runId, "phase", phase, "textLength", textLength), null,
                "agent.interrupt", runId, System.currentTimeMillis());
    }

    /** 从打断处接续 */
    public static StreamEvent agentResume(String runId, String newInput, int contextSize) {
        return new StreamEvent(EventType.AGENT_RESUME, null, null, null, null, null,
                null, Map.of("runId", runId, "newInput", newInput, "contextSize", contextSize), null,
                "agent.resume", runId, System.currentTimeMillis());
    }

    // ==================== Subagent 生命周期事件 ====================

    /** 子 agent 创建 */
    public static StreamEvent subagentSpawn(String runId, String agentId, String task) {
        return new StreamEvent(EventType.SUBAGENT_SPAWN, null, null, null, null, null,
                null, Map.of("runId", runId, "agentId", agentId, "task", task), null,
                "subagent.spawn", runId, System.currentTimeMillis());
    }

    /** 子 agent 完成 */
    public static StreamEvent subagentComplete(String runId, String result, long durationMs, int tokenUsage) {
        return new StreamEvent(EventType.SUBAGENT_COMPLETE, null, null, null, null, null,
                null, Map.of("runId", runId, "result", result, "durationMs", durationMs, "tokenUsage", tokenUsage), null,
                "subagent.complete", runId, System.currentTimeMillis());
    }

    /** 子 agent 失败 */
    public static StreamEvent subagentError(String runId, String error) {
        return new StreamEvent(EventType.SUBAGENT_ERROR, null, null, null, null, null,
                null, Map.of("runId", runId, "error", error), null,
                "subagent.error", runId + ": " + error, System.currentTimeMillis());
    }

    /** 子 agent 被取消 */
    public static StreamEvent subagentCancelled(String runId, String reason) {
        return new StreamEvent(EventType.SUBAGENT_CANCELLED, null, null, null, null, null,
                null, Map.of("runId", runId, "reason", reason), null,
                "subagent.cancelled", runId, System.currentTimeMillis());
    }

    // ==================== Task 编排生命周期事件 ====================

    /** 任务开始执行。data 须包含 taskName。 */
    public static StreamEvent taskStart(String taskName, Map<String, Object> data) {
        return new StreamEvent(EventType.TASK_START, null, null, null, null, null,
                null, data != null ? Collections.unmodifiableMap(data) : Map.of(), null,
                "task.start", taskName, System.currentTimeMillis());
    }

    /** 任务执行完成。data 须包含 taskName 与 taskResult(TaskResult)。 */
    public static StreamEvent taskComplete(String taskName, Map<String, Object> data) {
        return new StreamEvent(EventType.TASK_COMPLETE, null, null, null, null, null,
                null, data != null ? Collections.unmodifiableMap(data) : Map.of(), null,
                "task.complete", taskName, System.currentTimeMillis());
    }

    /** 任务执行失败。data 须包含 taskName 与 taskResult(TaskResult, isError)。 */
    public static StreamEvent taskError(String taskName, Map<String, Object> data) {
        return new StreamEvent(EventType.TASK_ERROR, null, null, null, null, null,
                null, data != null ? Collections.unmodifiableMap(data) : Map.of(), null,
                "task.error", taskName, System.currentTimeMillis());
    }

    /** 任务被 guard 跳过。data 须包含 taskName。 */
    public static StreamEvent taskSkipped(String taskName, Map<String, Object> data) {
        return new StreamEvent(EventType.TASK_SKIPPED, null, null, null, null, null,
                null, data != null ? Collections.unmodifiableMap(data) : Map.of(), null,
                "task.skipped", taskName, System.currentTimeMillis());
    }

    public EventType getType() {
        return type;
    }

    public String getTextDelta() {
        return textDelta;
    }

    public ToolCall getToolCall() {
        return toolCall;
    }

    public ToolResultChunk getChunk() {
        return chunk;
    }

    public LLMResponse getResponse() {
        return response;
    }

    public Throwable getError() {
        return error;
    }

    public String getCategory() {
        return category;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public Map<String, Object> getMetadata() {
        return metadata != null ? metadata : Collections.emptyMap();
    }

    public String getTracePhase() { return tracePhase; }
    public String getTraceMessage() { return traceMessage; }
    public long getTraceTimestamp() { return traceTimestamp; }

    @Override
    public String toString() {
        return "StreamEvent{type=" + type +
                (textDelta != null ? ", textDelta='" + textDelta + "'" : "") +
                (toolCall != null ? ", toolCall=" + toolCall : "") +
                (chunk != null ? ", chunk=" + chunk : "") +
                (category != null ? ", category='" + category + "'" : "") +
                (data != null ? ", data=" + data : "") +
                "}";
    }
}
