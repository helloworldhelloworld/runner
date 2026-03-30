package com.lightweightai.kernel.task.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * YAML 任务编排配置模型
 *
 * 对应 task-graph.yml 的结构，Jackson 反序列化用。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaskGraphConfig {

    @JsonProperty("task-graphs")
    private Map<String, GraphConfig> taskGraphs;

    private OrchestrationConfig orchestration;

    public Map<String, GraphConfig> getTaskGraphs() { return taskGraphs; }
    public void setTaskGraphs(Map<String, GraphConfig> taskGraphs) { this.taskGraphs = taskGraphs; }
    public OrchestrationConfig getOrchestration() { return orchestration; }
    public void setOrchestration(OrchestrationConfig orchestration) { this.orchestration = orchestration; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GraphConfig {
        private String description;
        private List<StepConfig> steps;
        @JsonProperty("expose-as-tool")
        private ToolExposeConfig exposeAsTool;
        private Map<String, Map<String, Object>> config;

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public List<StepConfig> getSteps() { return steps; }
        public void setSteps(List<StepConfig> steps) { this.steps = steps; }
        public ToolExposeConfig getExposeAsTool() { return exposeAsTool; }
        public void setExposeAsTool(ToolExposeConfig exposeAsTool) { this.exposeAsTool = exposeAsTool; }
        public Map<String, Map<String, Object>> getConfig() { return config; }
        public void setConfig(Map<String, Map<String, Object>> config) { this.config = config; }
    }

    /**
     * 一个 Step：单任务 或 并行组（二选一）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StepConfig {
        private String task;
        private ParallelConfig parallel;
        private ConditionConfig condition;

        public String getTask() { return task; }
        public void setTask(String task) { this.task = task; }
        public ParallelConfig getParallel() { return parallel; }
        public void setParallel(ParallelConfig parallel) { this.parallel = parallel; }
        public ConditionConfig getCondition() { return condition; }
        public void setCondition(ConditionConfig condition) { this.condition = condition; }
        public boolean isSingleTask() { return task != null; }
        public boolean isParallel() { return parallel != null; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParallelConfig {
        private List<Object> tasks;  // String 或 ParallelTaskEntry
        private String join;
        private ConditionConfig condition;

        public List<Object> getTasks() { return tasks; }
        public void setTasks(List<Object> tasks) { this.tasks = tasks; }
        public String getJoin() { return join; }
        public void setJoin(String join) { this.join = join; }
        public ConditionConfig getCondition() { return condition; }
        public void setCondition(ConditionConfig condition) { this.condition = condition; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParallelTaskEntry {
        private String name;
        private ConditionConfig condition;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public ConditionConfig getCondition() { return condition; }
        public void setCondition(ConditionConfig condition) { this.condition = condition; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConditionConfig {
        private String task;
        private String status;
        @JsonProperty("output-match")
        private OutputMatchConfig outputMatch;

        public String getTask() { return task; }
        public void setTask(String task) { this.task = task; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public OutputMatchConfig getOutputMatch() { return outputMatch; }
        public void setOutputMatch(OutputMatchConfig outputMatch) { this.outputMatch = outputMatch; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OutputMatchConfig {
        private String key;
        private String value;

        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolExposeConfig {
        private String name;
        private String description;
        private Map<String, Object> schema;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public Map<String, Object> getSchema() { return schema; }
        public void setSchema(Map<String, Object> schema) { this.schema = schema; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrchestrationConfig {
        @JsonProperty("pre-process")
        private String preProcess;
        @JsonProperty("post-process")
        private String postProcess;

        public String getPreProcess() { return preProcess; }
        public void setPreProcess(String preProcess) { this.preProcess = preProcess; }
        public String getPostProcess() { return postProcess; }
        public void setPostProcess(String postProcess) { this.postProcess = postProcess; }
    }
}
