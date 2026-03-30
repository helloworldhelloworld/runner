package com.lightweightai.kernel.task.config;

import com.lightweightai.kernel.task.TaskGraph;
import com.lightweightai.kernel.task.TaskGraphTool;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * YAML 加载结果封装
 *
 * 包含所有解析出的 TaskGraph、暴露为 Tool 的 TaskGraphTool、以及编排配置。
 */
public class TaskGraphBundle {

    private final Map<String, TaskGraph> graphs;
    private final List<TaskGraphTool> tools;
    private final String preProcessGraphName;
    private final String postProcessGraphName;

    public TaskGraphBundle(Map<String, TaskGraph> graphs, List<TaskGraphTool> tools,
                           String preProcessGraphName, String postProcessGraphName) {
        this.graphs = Collections.unmodifiableMap(graphs);
        this.tools = Collections.unmodifiableList(tools);
        this.preProcessGraphName = preProcessGraphName;
        this.postProcessGraphName = postProcessGraphName;
    }

    public Map<String, TaskGraph> getGraphs() { return graphs; }

    public Optional<TaskGraph> getGraph(String name) {
        return Optional.ofNullable(graphs.get(name));
    }

    public List<TaskGraphTool> getExposedTools() { return tools; }

    public Optional<TaskGraph> getPreProcessGraph() {
        return preProcessGraphName != null
                ? Optional.ofNullable(graphs.get(preProcessGraphName))
                : Optional.empty();
    }

    public Optional<TaskGraph> getPostProcessGraph() {
        return postProcessGraphName != null
                ? Optional.ofNullable(graphs.get(postProcessGraphName))
                : Optional.empty();
    }
}
