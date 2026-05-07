package com.lightweightai.kernel.task.config;

import com.lightweightai.kernel.task.graph.TaskGraph;

import java.util.Collections;
import java.util.Map;

/**
 * Loader 的产物：图本体 + 节点级属性。
 */
public final class TaskGraphBundle {

    private final TaskGraph graph;
    private final Map<String, Map<String, Object>> nodeAttributes;

    public TaskGraphBundle(TaskGraph graph,
                           Map<String, Map<String, Object>> nodeAttributes) {
        this.graph = graph;
        this.nodeAttributes = Collections.unmodifiableMap(nodeAttributes);
    }

    public TaskGraph getGraph() { return graph; }
    public Map<String, Map<String, Object>> getNodeAttributes() { return nodeAttributes; }
}
