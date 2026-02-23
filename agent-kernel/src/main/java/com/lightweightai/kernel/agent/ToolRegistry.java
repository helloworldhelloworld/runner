package com.lightweightai.kernel.agent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一工具注册表
 *
 * 负责管理所有可用工具：
 * - 注册/注销工具
 * - 按名称/分类/标签查询
 * - 启用/禁用工具
 * - 生成 LLM 工具定义
 */
public class ToolRegistry {

    private final Map<String, Tool> tools;
    private final Set<String> disabledTools;

    public ToolRegistry() {
        this.tools = new ConcurrentHashMap<>();
        this.disabledTools = ConcurrentHashMap.newKeySet();
    }

    // ==================== 注册/注销 ====================

    /**
     * 注册工具
     */
    public void register(Tool tool) {
        Objects.requireNonNull(tool, "Tool cannot be null");
        Objects.requireNonNull(tool.getName(), "Tool name cannot be null");
        tools.put(tool.getName(), tool);
    }

    /**
     * 批量注册工具
     */
    public void registerAll(Collection<Tool> toolCollection) {
        toolCollection.forEach(this::register);
    }

    /**
     * 注销工具
     */
    public void unregister(String name) {
        tools.remove(name);
        disabledTools.remove(name);
    }

    // ==================== 查询 ====================

    /**
     * 检查是否存在工具
     */
    public boolean has(String name) {
        return tools.containsKey(name);
    }

    /**
     * 获取工具
     */
    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /**
     * 获取所有工具
     */
    public List<Tool> getAll() {
        return new ArrayList<>(tools.values());
    }

    /**
     * 获取所有启用的工具
     */
    public List<Tool> getEnabled() {
        return tools.values().stream()
            .filter(t -> !disabledTools.contains(t.getName()))
            .collect(Collectors.toList());
    }

    /**
     * 按分类获取工具
     */
    public List<Tool> getByCategory(String category) {
        return tools.values().stream()
            .filter(t -> {
                if (t instanceof ToolMetadata) {
                    return category.equals(((ToolMetadata) t).getCategory());
                }
                return "default".equals(category);
            })
            .collect(Collectors.toList());
    }

    /**
     * 按标签获取工具
     */
    public List<Tool> getByTag(String tag) {
        return tools.values().stream()
            .filter(t -> {
                if (t instanceof ToolMetadata) {
                    return ((ToolMetadata) t).getTags().contains(tag);
                }
                return false;
            })
            .collect(Collectors.toList());
    }

    // ==================== 启用/禁用 ====================

    /**
     * 禁用工具
     */
    public void disable(String name) {
        if (tools.containsKey(name)) {
            disabledTools.add(name);
        }
    }

    /**
     * 启用工具
     */
    public void enable(String name) {
        disabledTools.remove(name);
    }

    /**
     * 检查工具是否启用
     */
    public boolean isEnabled(String name) {
        return tools.containsKey(name) && !disabledTools.contains(name);
    }

    // ==================== LLM 集成 ====================

    /**
     * 获取工具定义列表（供 LLM 使用）
     *
     * 返回格式兼容 Claude API 的 tools 参数
     */
    public List<Map<String, Object>> getToolDefinitions() {
        return getEnabled().stream()
            .map(this::toToolDefinition)
            .collect(Collectors.toList());
    }

    /**
     * 将 Tool 转换为 LLM 工具定义
     */
    private Map<String, Object> toToolDefinition(Tool tool) {
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("name", tool.getName());
        definition.put("description", tool.getDescription());
        definition.put("input_schema", tool.getSchema().toMap());
        return definition;
    }

    // ==================== 统计信息 ====================

    /**
     * 获取工具数量
     */
    public int size() {
        return tools.size();
    }

    /**
     * 获取启用的工具数量
     */
    public int enabledCount() {
        return tools.size() - disabledTools.size();
    }

    /**
     * 清空所有工具
     */
    public void clear() {
        tools.clear();
        disabledTools.clear();
    }

    @Override
    public String toString() {
        return String.format("ToolRegistry{total=%d, enabled=%d}",
            tools.size(), enabledCount());
    }
}
