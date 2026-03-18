package com.lightweightai.kernel.agent.directive;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * 端侧能力声明
 *
 * 描述端侧支持的单个 Directive 能力（如拍照、定位、签名等）。
 * 端侧连接时通过 {@link ClientManifest} 上报所有能力，
 * 云端通过 {@link DirectiveToolBridge} 自动注册为 Tool。
 *
 * <pre>
 * {
 *   "namespace": "Camera",
 *   "name": "TakePhoto",
 *   "description": "Take a photo using the device camera",
 *   "input_schema": { "type": "object", "properties": { "resolution": { "type": "string" } } },
 *   "default_timeout_ms": 30000
 * }
 * </pre>
 */
public class ClientCapability {

    @JsonProperty("namespace")
    private String namespace;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("input_schema")
    private Map<String, Object> inputSchema;

    @JsonProperty("default_timeout_ms")
    private long defaultTimeoutMs;

    /** 默认超时 60 秒 */
    private static final long DEFAULT_TIMEOUT = 60_000;

    public ClientCapability() {
    }

    public ClientCapability(String namespace, String name, String description,
                            Map<String, Object> inputSchema, long defaultTimeoutMs) {
        this.namespace = namespace;
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.defaultTimeoutMs = defaultTimeoutMs > 0 ? defaultTimeoutMs : DEFAULT_TIMEOUT;
    }

    /**
     * 组合为工具名格式 "namespace.name"
     */
    public String toToolName() {
        return namespace + "." + name;
    }

    // Getters and Setters

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getInputSchema() {
        return inputSchema;
    }

    public void setInputSchema(Map<String, Object> inputSchema) {
        this.inputSchema = inputSchema;
    }

    public long getDefaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    public void setDefaultTimeoutMs(long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    @Override
    public String toString() {
        return "ClientCapability{" +
            "namespace='" + namespace + '\'' +
            ", name='" + name + '\'' +
            ", description='" + description + '\'' +
            '}';
    }
}
