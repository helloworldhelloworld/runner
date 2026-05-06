package com.lightweightai.kernel.task.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * YAML DAG 配置 DTO。
 *
 * <h3>Schema 选择：GitHub Actions 风格的 {@code needs}</h3>
 * <p>每个 task 节点显式声明 {@code needs: [a, b]} 表达依赖（PR #109 review §三.9）。
 * 不使用线性 {@code steps} 隐式依赖，因为 DAG 需要显式表达。</p>
 *
 * <p><b>注意</b>：YAML 不能声明「把这个 graph 暴露成 Tool」—— Task 与 Tool 是不同本体，
 * 命名空间独立，禁止桥接。详见 {@code docs/architecture/task-vs-tool.md}。</p>
 *
 * <p>所有 DTO 都<b>不</b>启用 {@code @JsonIgnoreProperties(ignoreUnknown = true)}；
 * 由 {@link TaskGraphLoader} 在 ObjectMapper 上启用 {@code FAIL_ON_UNKNOWN_PROPERTIES}（M4）。</p>
 *
 * <pre>
 * version: 1
 * tasks:
 *   - name: classify
 *     ref: intent.classifier
 *   - name: retrieve
 *     ref: rag.retrieval
 *     needs: [classify]
 *     join: ALL_SUCCESS
 *   - name: filter
 *     ref: safety.filter
 *     needs: [retrieve]
 *     guard: status==SUCCESS
 * </pre>
 */
public final class TaskGraphConfig {

    @JsonProperty("version")
    public int version = 1;

    @JsonProperty("tasks")
    public List<TaskNode> tasks = List.of();

    public static final class TaskNode {
        @JsonProperty("name")
        public String name;
        @JsonProperty("ref")
        public String ref;
        @JsonProperty("needs")
        public List<String> needs = List.of();
        @JsonProperty("join")
        public String join;        // ALL_SUCCESS | ALL_COMPLETE | ANY_SUCCESS | FIRST_COMPLETE | quorum:N
        @JsonProperty("guard")
        public String guard;       // 简单 DSL，见 GuardExpression.parse
        @JsonProperty("attributes")
        public Map<String, Object> attributes = Map.of();
    }
}
