package com.lightweightai.kernel.task;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * TaskGraph → Tool 桥接
 *
 * 将 TaskGraph 包装为 Tool，让 LLM 可以通过工具调用触发任务编排。
 */
public class TaskGraphTool implements Tool {

    private final String name;
    private final String description;
    private final ToolSchema schema;
    private final TaskGraph graph;
    private final TaskResultAggregator aggregator;

    public TaskGraphTool(String name, String description, ToolSchema schema, TaskGraph graph) {
        this(name, description, schema, graph, TaskResultAggregator.DEFAULT);
    }

    public TaskGraphTool(String name, String description, ToolSchema schema,
                         TaskGraph graph, TaskResultAggregator aggregator) {
        this.name = name;
        this.description = description;
        this.schema = schema;
        this.graph = graph;
        this.aggregator = aggregator;
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public ToolSchema getSchema() { return schema; }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        TaskContext context = TaskContext.builder()
                .userInput(args.getOrDefault("query", "").toString())
                .attribute("toolArgs", args)
                .build();
        graph.execute(context).blockLast();
        return aggregator.aggregate(context);
    }

    @Override
    public Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
        TaskContext context = TaskContext.builder()
                .userInput(args.getOrDefault("query", "").toString())
                .attribute("toolArgs", args)
                .build();

        return graph.execute(context)
                .filter(event -> event.getType() == StreamEvent.EventType.TOOL_PROGRESS
                        || event.getType() == StreamEvent.EventType.TRACE)
                .map(event -> ToolResultChunk.progress(name,
                        event.getTraceMessage() != null ? event.getTraceMessage() : "progress", 0, 0))
                .concatWith(Flux.defer(() -> {
                    ToolResult result = aggregator.aggregate(context);
                    return Flux.just(ToolResultChunk.complete(name, result));
                }));
    }

    /**
     * 聚合所有 TaskResult 为单个 ToolResult
     */
    @FunctionalInterface
    public interface TaskResultAggregator {

        ToolResult aggregate(TaskContext context);

        /** 默认：拼接所有成功任务的 content */
        TaskResultAggregator DEFAULT = context -> {
            StringBuilder sb = new StringBuilder();
            context.getAllResults().forEach((taskName, result) -> {
                if (result.isSuccess()) {
                    sb.append("[").append(taskName).append("] ")
                            .append(result.getContent()).append("\n\n");
                }
            });
            String content = sb.toString().trim();
            return content.isEmpty()
                    ? ToolResult.error("All tasks failed or were skipped")
                    : ToolResult.success(content);
        };
    }
}
