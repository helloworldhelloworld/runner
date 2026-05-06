package com.lightweightai.kernel.task.config;

import com.lightweightai.kernel.task.graph.TaskGraph;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Loader 的产物：图本体 + 节点级属性 + 暴露为 Tool 的声明。
 */
public final class TaskGraphBundle {

    private final TaskGraph graph;
    private final Map<String, Map<String, Object>> nodeAttributes;
    private final List<TaskGraphConfig.ExposedTool> exposedTools;

    public TaskGraphBundle(TaskGraph graph,
                           Map<String, Map<String, Object>> nodeAttributes,
                           List<TaskGraphConfig.ExposedTool> exposedTools) {
        this.graph = graph;
        this.nodeAttributes = Collections.unmodifiableMap(nodeAttributes);
        this.exposedTools = exposedTools == null ? List.of() : List.copyOf(exposedTools);
    }

    public TaskGraph getGraph() { return graph; }
    public Map<String, Map<String, Object>> getNodeAttributes() { return nodeAttributes; }
    public List<TaskGraphConfig.ExposedTool> getExposedTools() { return exposedTools; }
}
