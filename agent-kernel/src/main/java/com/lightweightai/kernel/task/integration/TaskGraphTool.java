package com.lightweightai.kernel.task.integration;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.task.core.TaskContext;
import com.lightweightai.kernel.task.core.TaskResult;
import com.lightweightai.kernel.task.graph.TaskGraph;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 把 {@link TaskGraph} 桥接为 {@link Tool}，暴露给 LLM。
 *
 * <h3>明确的契约（PR #109 review §三.7）</h3>
 *
 * <h4>1. args → TaskContext 映射</h4>
 * <p>LLM 传入的 {@code Map<String, Object> args} 整体作为 TaskContext 的 <b>attributes</b>。
 * 约定 key：</p>
 * <ul>
 *   <li>{@code "input"} — 主输入字符串（如用户 query）</li>
 *   <li>其他 key — free-form，由各 Task 实现自行约定 / 校验</li>
 * </ul>
 *
 * <h4>2. 内部事件透传策略</h4>
 * <p>DAG 内部 {@code TASK_START / TASK_PROGRESS / TASK_COMPLETE} 事件
 * <b>会</b>映射成 {@link ToolResultChunk#progress} 透传给 LLM 流。
 * 这样 LLM/前端能看到 pipeline 进度而不是黑盒。</p>
 *
 * <h4>3. 最终 ToolResult 聚合</h4>
 * <p>所有 SUCCESS task 的 {@link TaskResult#getContent()} 按拓扑顺序拼接，
 * 用 {@code "\n--- {taskName} ---\n"} 分隔。如有 ERROR，则整体 isError=true，
 * content 包含错误任务名 + errorType + 头几行 stacktrace。</p>
 *
 * <h4>4. 反应式纪律</h4>
 * <p>{@link #execute(Map)} 同步路径用 {@code subscribeOn(boundedElastic).blockLast()} 防止 BlockHangException
 * （PR #109 review M3）；调用方应优先使用 {@link #executeReactive(Map)}。</p>
 */
public final class TaskGraphTool implements Tool {

    private final String name;
    private final String description;
    private final ToolSchema schema;
    private final TaskGraph graph;

    public TaskGraphTool(String name, String description, ToolSchema schema, TaskGraph graph) {
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.schema = schema != null ? schema : ToolSchema.empty();
        this.graph = Objects.requireNonNull(graph, "graph");
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public ToolSchema getSchema() { return schema; }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        return executeReactive(args)
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE
                || c.getType() == ToolResultChunk.ChunkType.ERROR)
            .next()
            .map(c -> c.getType() == ToolResultChunk.ChunkType.ERROR
                ? ToolResult.error(textOf(c))
                : ToolResult.success(textOf(c)))
            .subscribeOn(Schedulers.boundedElastic())
            .block();
    }

    @Override
    public Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
        return Flux.defer(() -> {
            TaskContext ctx = new TaskContext();
            if (args != null) {
                args.forEach(ctx::setAttribute);
            }
            return graph.execute(ctx)
                .map(this::mapEvent)
                .concatWith(Flux.defer(() -> Flux.just(finalChunk(ctx))));
        });
    }

    private ToolResultChunk mapEvent(StreamEvent ev) {
        return switch (ev.getType()) {
            case TASK_START -> ToolResultChunk.progress(name,
                "▶ " + extractTaskName(ev), 0, 100);
            case TASK_COMPLETE -> ToolResultChunk.progress(name,
                "✓ " + extractTaskName(ev) + ": " + extractContent(ev), 100, 100);
            case TASK_SKIPPED -> ToolResultChunk.progress(name,
                "⊘ skipped " + extractTaskName(ev) + ": " + extractContent(ev), 100, 100);
            case TASK_ERROR -> ToolResultChunk.progress(name,
                "✗ " + extractTaskName(ev) + ": " + extractContent(ev), 100, 100);
            default -> ToolResultChunk.progress(name, ev.toString(), 0, 100);
        };
    }

    private ToolResultChunk finalChunk(TaskContext ctx) {
        Map<String, TaskResult> all = ctx.getAllResults();
        TaskResult firstError = all.values().stream()
            .filter(TaskResult::isError)
            .findFirst()
            .orElse(null);
        if (firstError != null) {
            String name = all.entrySet().stream()
                .filter(e -> e.getValue() == firstError)
                .map(Map.Entry::getKey)
                .findFirst().orElse("?");
            String stack = firstError.getErrorStackHead() != null ? firstError.getErrorStackHead() : "";
            return ToolResultChunk.error(this.name,
                "task '" + name + "' failed [" + firstError.getErrorType() + "]: "
                    + firstError.getContent() + "\n" + stack);
        }
        // 按拓扑顺序拼接 SUCCESS content
        StringBuilder sb = new StringBuilder();
        for (List<String> level : graph.getLevels()) {
            for (String taskName : level) {
                TaskResult r = all.get(taskName);
                if (r != null && r.isSuccess()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append("--- ").append(taskName).append(" ---\n").append(r.getContent());
                }
            }
        }
        return ToolResultChunk.complete(this.name,
            ToolResult.success(sb.toString()));
    }

    private static String extractTaskName(StreamEvent ev) {
        Map<String, Object> data = ev.getData();
        if (data == null) return "?";
        Object n = data.get("taskName");
        return n != null ? n.toString() : "?";
    }

    private static String extractContent(StreamEvent ev) {
        Map<String, Object> data = ev.getData();
        if (data == null) return "";
        Object r = data.get("taskResult");
        return r instanceof TaskResult tr ? tr.getContent() : "";
    }

    private static String textOf(ToolResultChunk c) {
        if (c.getResult() != null) return c.getResult().getContent();
        if (c.getMessage() != null) return c.getMessage();
        return "";
    }

    /** 暴露给 ToolRegistry 的 schema —— 默认仅一个 free-form input 字符串。 */
    public static ToolSchema defaultSchema() {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", "string");
        input.put("description", "Primary input passed to the task graph as attribute 'input'");
        return ToolSchema.withProperties(Map.of("input", input));
    }
}
