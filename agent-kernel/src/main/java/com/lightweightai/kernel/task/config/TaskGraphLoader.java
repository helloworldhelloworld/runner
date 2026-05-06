package com.lightweightai.kernel.task.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lightweightai.kernel.task.core.JoinStrategy;
import com.lightweightai.kernel.task.core.Task;
import com.lightweightai.kernel.task.core.TaskRegistry;
import com.lightweightai.kernel.task.graph.TaskGraph;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从 YAML 加载 TaskGraph。
 *
 * <h3>严格模式</h3>
 * <p>启用 {@link DeserializationFeature#FAIL_ON_UNKNOWN_PROPERTIES}（PR #109 review M4）：
 * 用户写错 {@code step}/{@code steps}、{@code expose-as-tool}/{@code expose-as-tools}
 * 之类拼写时<b>立即抛错</b>，不会静默忽略。</p>
 *
 * <h3>解析流程</h3>
 * <ol>
 *   <li>YAML → {@link TaskGraphConfig}（POJO）</li>
 *   <li>每个 {@code TaskNode.ref} 在 {@link TaskRegistry} 中查找；找不到立即抛错</li>
 *   <li>{@code needs} → {@link TaskGraph.Builder#addTask(Task, String...)}（依赖名验证由 TaskGraph 完成）</li>
 *   <li>{@code join} → {@link JoinStrategy} 常量；{@code guard} → {@link GuardExpression}</li>
 * </ol>
 */
public final class TaskGraphLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory())
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final TaskRegistry registry;

    public TaskGraphLoader(TaskRegistry registry) {
        this.registry = registry;
    }

    public TaskGraphBundle load(InputStream in) throws IOException {
        return build(MAPPER.readValue(in, TaskGraphConfig.class));
    }

    public TaskGraphBundle load(Reader reader) throws IOException {
        return build(MAPPER.readValue(reader, TaskGraphConfig.class));
    }

    public TaskGraphBundle load(String yaml) throws IOException {
        return build(MAPPER.readValue(yaml, TaskGraphConfig.class));
    }

    private TaskGraphBundle build(TaskGraphConfig cfg) {
        if (cfg.version != 1) {
            throw new IllegalArgumentException("unsupported task-graph version: " + cfg.version);
        }
        TaskGraph.Builder b = TaskGraph.builder();
        Map<String, Map<String, Object>> attrs = new LinkedHashMap<>();
        for (TaskGraphConfig.TaskNode node : cfg.tasks) {
            if (node.name == null || node.name.isBlank()) {
                throw new IllegalArgumentException("task.name is required");
            }
            if (node.ref == null || node.ref.isBlank()) {
                throw new IllegalArgumentException("task.ref is required (task: " + node.name + ")");
            }
            Task task = registry.get(node.ref).orElseThrow(() ->
                new IllegalArgumentException("ref '" + node.ref + "' not found in TaskRegistry"
                    + " (referenced by task '" + node.name + "')"));
            // ConfiguredTask 仅在 task 节点 name 与 ref 不同时使用别名；这里直接用 ref 作为图内 name
            b.addTask(task, node.needs.toArray(String[]::new));
            if (node.join != null && !node.join.isBlank()) {
                b.withJoin(task.getName(), parseJoin(node.join));
            }
            if (node.guard != null && !node.guard.isBlank()) {
                b.withGuard(task.getName(), GuardExpression.parse(node.guard));
            }
            if (node.attributes != null && !node.attributes.isEmpty()) {
                attrs.put(task.getName(), node.attributes);
            }
        }
        return new TaskGraphBundle(b.build(), attrs, cfg.exposeAsTools);
    }

    private static JoinStrategy parseJoin(String s) {
        String t = s.trim();
        return switch (t) {
            case "ALL_SUCCESS" -> JoinStrategy.ALL_SUCCESS;
            case "ALL_COMPLETE" -> JoinStrategy.ALL_COMPLETE;
            case "ANY_SUCCESS" -> JoinStrategy.ANY_SUCCESS;
            case "FIRST_COMPLETE" -> JoinStrategy.FIRST_COMPLETE;
            default -> {
                if (t.startsWith("quorum:")) {
                    int n;
                    try {
                        n = Integer.parseInt(t.substring("quorum:".length()).trim());
                    } catch (NumberFormatException nfe) {
                        throw new IllegalArgumentException("invalid quorum: " + t);
                    }
                    yield JoinStrategy.quorum(n);
                }
                throw new IllegalArgumentException("unknown join strategy: " + t);
            }
        };
    }
}
