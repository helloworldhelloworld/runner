package com.lightweightai.kernel.task.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.task.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Predicate;

/**
 * YAML 任务编排加载器
 *
 * 从 YAML 配置加载 TaskGraph。
 * Steps 列表转换为 DAG：Step N 的所有任务依赖 Step N-1 的所有任务。
 */
public class TaskGraphLoader {

    private static final Logger logger = LoggerFactory.getLogger(TaskGraphLoader.class);

    private final TaskRegistry taskRegistry;
    private final ObjectMapper yamlMapper;

    public TaskGraphLoader(TaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    /**
     * 从 InputStream 加载
     */
    public TaskGraphBundle load(InputStream yamlStream) throws IOException {
        TaskGraphConfig config = yamlMapper.readValue(yamlStream, TaskGraphConfig.class);
        return buildBundle(config);
    }

    /**
     * 从 classpath 资源加载
     */
    public TaskGraphBundle loadFromClasspath(String resource) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IOException("Classpath resource not found: " + resource);
            }
            return load(is);
        }
    }

    private TaskGraphBundle buildBundle(TaskGraphConfig config) {
        Map<String, TaskGraph> graphs = new LinkedHashMap<>();
        List<TaskGraphTool> tools = new ArrayList<>();

        if (config.getTaskGraphs() != null) {
            for (Map.Entry<String, TaskGraphConfig.GraphConfig> entry : config.getTaskGraphs().entrySet()) {
                String name = entry.getKey();
                TaskGraphConfig.GraphConfig graphConfig = entry.getValue();

                TaskGraph graph = buildGraph(name, graphConfig);
                graphs.put(name, graph);

                // 处理 expose-as-tool
                if (graphConfig.getExposeAsTool() != null) {
                    tools.add(buildTool(graphConfig.getExposeAsTool(), graph));
                }
            }
        }

        String preName = null, postName = null;
        if (config.getOrchestration() != null) {
            preName = config.getOrchestration().getPreProcess();
            postName = config.getOrchestration().getPostProcess();
        }

        logger.info("加载了 {} 个 TaskGraph, {} 个 Tool 暴露", graphs.size(), tools.size());
        return new TaskGraphBundle(graphs, tools, preName, postName);
    }

    /**
     * Steps → DAG 转换核心
     *
     * 规则：Step N 的每个任务 depends-on Step N-1 的所有任务。
     * parallel 组内的任务互相无依赖（并行执行）。
     */
    private TaskGraph buildGraph(String name, TaskGraphConfig.GraphConfig config) {
        TaskGraph.Builder builder = TaskGraph.builder().name(name);
        List<String> previousStepTaskNames = Collections.emptyList();

        if (config.getSteps() == null) return builder.build();

        for (TaskGraphConfig.StepConfig step : config.getSteps()) {
            List<String> currentStepTaskNames = new ArrayList<>();

            if (step.isSingleTask()) {
                Task task = resolveTask(step.getTask(), config);
                builder.addTask(task, previousStepTaskNames.toArray(String[]::new));
                currentStepTaskNames.add(task.getName());

                if (step.getCondition() != null) {
                    builder.withCondition(task.getName(), buildCondition(step.getCondition()));
                }

            } else if (step.isParallel()) {
                TaskGraphConfig.ParallelConfig parallel = step.getParallel();
                JoinStrategy join = parallel.getJoin() != null
                        ? JoinStrategy.valueOf(parallel.getJoin())
                        : JoinStrategy.ALL_SUCCESS;

                for (Object taskEntry : parallel.getTasks()) {
                    String taskTypeName;
                    TaskGraphConfig.ConditionConfig taskCondition = null;

                    if (taskEntry instanceof String) {
                        taskTypeName = (String) taskEntry;
                    } else if (taskEntry instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> entryMap = (Map<String, Object>) taskEntry;
                        taskTypeName = (String) entryMap.get("name");
                        if (entryMap.containsKey("condition")) {
                            taskCondition = yamlMapper.convertValue(
                                    entryMap.get("condition"),
                                    TaskGraphConfig.ConditionConfig.class);
                        }
                    } else {
                        throw new IllegalArgumentException(
                                "Invalid parallel task entry: " + taskEntry);
                    }

                    Task task = resolveTask(taskTypeName, config);
                    builder.addTask(task, previousStepTaskNames.toArray(String[]::new));
                    currentStepTaskNames.add(task.getName());

                    TaskGraphConfig.ConditionConfig cond = taskCondition != null
                            ? taskCondition : parallel.getCondition();
                    if (cond != null) {
                        builder.withCondition(task.getName(), buildCondition(cond));
                    }

                    builder.withJoinStrategy(task.getName(), join);
                }
            }

            previousStepTaskNames = currentStepTaskNames;
        }

        return builder.build();
    }

    private Task resolveTask(String typeName, TaskGraphConfig.GraphConfig config) {
        Task task = taskRegistry.get(typeName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Task '" + typeName + "' 未在 TaskRegistry 中注册"));

        if (config.getConfig() != null && config.getConfig().containsKey(typeName)) {
            task = new ConfiguredTask(task, config.getConfig().get(typeName));
        }
        return task;
    }

    private Predicate<TaskContext> buildCondition(TaskGraphConfig.ConditionConfig config) {
        if (config.getStatus() != null) {
            return ctx -> ctx.getResult(config.getTask())
                    .map(r -> r.getStatus().name().equals(config.getStatus()))
                    .orElse(false);
        } else if (config.getOutputMatch() != null) {
            return ctx -> ctx.getResult(config.getTask())
                    .map(r -> {
                        Object val = r.getData().get(config.getOutputMatch().getKey());
                        return config.getOutputMatch().getValue().equals(String.valueOf(val));
                    })
                    .orElse(false);
        }
        return ctx -> true;
    }

    private TaskGraphTool buildTool(TaskGraphConfig.ToolExposeConfig toolConfig, TaskGraph graph) {
        ToolSchema schema = toolConfig.getSchema() != null
                ? new ToolSchema(Map.of("type", "object", "properties", toolConfig.getSchema()))
                : ToolSchema.empty();

        return new TaskGraphTool(
                toolConfig.getName(),
                toolConfig.getDescription(),
                schema,
                graph);
    }
}
