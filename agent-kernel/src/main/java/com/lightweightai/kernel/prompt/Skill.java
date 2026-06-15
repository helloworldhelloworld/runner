package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.agent.Tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Skill 规范 (OpenClaw 风格)
 *
 * Skill 是独立的能力单元，包含：
 * - 名称和描述
 * - System Prompt 片段
 * - 关联的工具定义（可直接包装 Tool 实例）
 * - 触发词（用于自动检测）
 * - 优先级（用于渐进式加载排序）
 */
public class Skill {

    private final String name;
    private final String description;
    private final Supplier<String> systemPromptLoader;
    private volatile String cachedSystemPrompt;  // 首次 getSystemPrompt() 后填充
    private final List<ToolDefinition> tools;
    private final List<Tool> toolInstances; // 原始 Tool 实例
    private final List<String> triggers;
    private final int priority;
    private final Map<String, Object> metadata;

    private Skill(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "Skill name required");
        this.description = builder.description != null ? builder.description : "";
        this.systemPromptLoader = builder.systemPromptLoader != null
                ? builder.systemPromptLoader
                : () -> builder.systemPrompt != null ? builder.systemPrompt : "";
        this.tools = new ArrayList<>(builder.tools);
        this.toolInstances = new ArrayList<>(builder.toolInstances);
        this.triggers = new ArrayList<>(builder.triggers);
        this.priority = builder.priority;
        this.metadata = new HashMap<>(builder.metadata);
    }

    // ==================== Getters ====================

    public String getName() { return name; }
    public String getDescription() { return description; }

    /**
     * 获取 System Prompt 主体。
     *
     * 若 Skill 用 {@link Builder#lazyBody(Supplier)} 注册,首次调用时才执行 supplier;
     * 结果缓存以保证幂等。eager 入口 {@link Builder#systemPrompt(String)} 表现等价。
     */
    public String getSystemPrompt() {
        if (cachedSystemPrompt == null) {
            String loaded = systemPromptLoader.get();
            cachedSystemPrompt = loaded != null ? loaded : "";
        }
        return cachedSystemPrompt;
    }
    public List<ToolDefinition> getTools() { return new ArrayList<>(tools); }
    public List<Tool> getToolInstances() { return new ArrayList<>(toolInstances); }
    public List<String> getTriggers() { return new ArrayList<>(triggers); }
    public int getPriority() { return priority; }
    public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }

    /**
     * 检查消息是否触发此 Skill
     */
    public boolean matchesTrigger(String message) {
        if (triggers.isEmpty()) {
            return false;
        }
        String lowerMessage = message.toLowerCase();
        return triggers.stream()
            .anyMatch(trigger -> lowerMessage.contains(trigger.toLowerCase()));
    }

    // ==================== Factory Methods ====================

    /**
     * 从单个 Tool 创建 Skill
     */
    public static Skill fromTool(Tool tool) {
        return builder()
            .name(tool.getName())
            .description(tool.getDescription())
            .addTool(tool)
            .build();
    }

    /**
     * 从多个 Tool 创建 Skill
     */
    public static Skill fromTools(String name, String description, List<Tool> tools) {
        Builder builder = builder()
            .name(name)
            .description(description);
        tools.forEach(builder::addTool);
        return builder.build();
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private String systemPrompt;
        private Supplier<String> systemPromptLoader;  // 优先于 systemPrompt(若非 null)
        private List<ToolDefinition> tools = new ArrayList<>();
        private List<Tool> toolInstances = new ArrayList<>();
        private List<String> triggers = new ArrayList<>();
        private int priority = 100; // 默认中等优先级
        private Map<String, Object> metadata = new HashMap<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        /**
         * 用 Supplier 提供 System Prompt 主体,仅在 Skill 被实际激活(显式或触发)时
         * 才会调用。首次调用结果会被 Skill 内部缓存。
         *
         * 适合大型 markdown 文档、远程拉取等需要延迟加载的场景。
         */
        public Builder lazyBody(Supplier<String> loader) {
            this.systemPromptLoader = loader;
            return this;
        }

        public Builder addTool(String name, String description, Map<String, Object> parameters) {
            this.tools.add(new ToolDefinition(name, description, parameters));
            return this;
        }

        public Builder addTool(ToolDefinition tool) {
            this.tools.add(tool);
            return this;
        }

        /**
         * 添加 Tool 实例
         *
         * 自动从 Tool 创建 ToolDefinition，并保存原始实例引用
         */
        public Builder addTool(Tool tool) {
            // 创建 ToolDefinition
            this.tools.add(new ToolDefinition(
                tool.getName(),
                tool.getDescription(),
                tool.getSchema().getProperties()
            ));
            // 保存原始实例
            this.toolInstances.add(tool);
            return this;
        }

        /**
         * 批量添加 Tool 实例
         */
        public Builder addTools(List<Tool> toolList) {
            toolList.forEach(this::addTool);
            return this;
        }

        public Builder triggers(List<String> triggers) {
            this.triggers = new ArrayList<>(triggers);
            return this;
        }

        public Builder addTrigger(String trigger) {
            this.triggers.add(trigger);
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Skill build() {
            return new Skill(this);
        }
    }

    // ==================== Tool Definition ====================

    /**
     * Skill 内的工具定义
     */
    public static class ToolDefinition {
        private final String name;
        private final String description;
        private final Map<String, Object> parameters;

        public ToolDefinition(String name, String description, Map<String, Object> parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Map<String, Object> getParameters() { return new HashMap<>(parameters); }

        /**
         * 转换为 JSON Schema 格式
         */
        public Map<String, Object> toSchema() {
            Map<String, Object> schema = new HashMap<>();
            schema.put("name", name);
            schema.put("description", description);
            schema.put("input_schema", Map.of(
                "type", "object",
                "properties", parameters
            ));
            return schema;
        }
    }

    @Override
    public String toString() {
        return String.format("Skill{name='%s', tools=%d, priority=%d}",
            name, tools.size(), priority);
    }
}
