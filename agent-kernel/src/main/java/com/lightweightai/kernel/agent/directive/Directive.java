package com.lightweightai.kernel.agent.directive;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * 云→端指令信封
 *
 * 封装工具调用为标准 Directive 格式下发到端侧执行。
 * 类比 Alexa Skill Directives: namespace + name + payload。
 *
 * <pre>
 * {
 *   "directiveId": "uuid-123",
 *   "namespace": "Camera",
 *   "name": "TakePhoto",
 *   "payload": { "resolution": "1080p" },
 *   "timeoutMs": 30000
 * }
 * </pre>
 */
public class Directive {

    @JsonProperty("directive_id")
    private String directiveId;

    @JsonProperty("namespace")
    private String namespace;

    @JsonProperty("name")
    private String name;

    @JsonProperty("payload")
    private Map<String, Object> payload;

    @JsonProperty("timeout_ms")
    private long timeoutMs;

    public Directive() {
    }

    public Directive(String directiveId, String namespace, String name,
                     Map<String, Object> payload, long timeoutMs) {
        this.directiveId = Objects.requireNonNull(directiveId, "directiveId cannot be null");
        this.namespace = Objects.requireNonNull(namespace, "namespace cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.payload = payload != null ? payload : Collections.emptyMap();
        this.timeoutMs = timeoutMs;
    }

    /**
     * 从 tool 名称（"namespace.name" 格式）构建 Directive
     *
     * @param directiveId 指令 ID（== callId）
     * @param toolName    工具名，格式 "Namespace.Name" 或无命名空间的简单名称
     * @param args        参数
     * @param timeoutMs   超时
     */
    public static Directive fromToolName(String directiveId, String toolName,
                                          Map<String, Object> args, long timeoutMs) {
        int dotIndex = toolName.indexOf('.');
        String ns;
        String n;
        if (dotIndex > 0) {
            ns = toolName.substring(0, dotIndex);
            n = toolName.substring(dotIndex + 1);
        } else {
            ns = "Default";
            n = toolName;
        }
        return new Directive(directiveId, ns, n, args, timeoutMs);
    }

    /**
     * 组合为工具名格式 "namespace.name"
     */
    public String toToolName() {
        return namespace + "." + name;
    }

    // Getters and Setters

    public String getDirectiveId() {
        return directiveId;
    }

    public void setDirectiveId(String directiveId) {
        this.directiveId = directiveId;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String toString() {
        return "Directive{" +
            "directiveId='" + directiveId + '\'' +
            ", namespace='" + namespace + '\'' +
            ", name='" + name + '\'' +
            ", payload=" + payload +
            ", timeoutMs=" + timeoutMs +
            '}';
    }
}
