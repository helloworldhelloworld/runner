package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.AnnotatedToolScanner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
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

    // ==================== 注解注册 ====================

    /**
     * 扫描对象中所有 @ToolFunction 注解方法并注册为工具
     *
     * <p>使用示例：</p>
     * <pre>
     * public class MyTools {
     *     &#64;ToolFunction(name = "greet", description = "Say hello")
     *     public String greet(&#64;ToolParam(name = "name", required = true) String name) {
     *         return "Hello, " + name;
     *     }
     * }
     *
     * registry.registerObject(new MyTools());
     * </pre>
     *
     * @param target 包含 @ToolFunction 方法的对象
     * @return 注册的工具数量
     */
    public int registerObject(Object target) {
        Objects.requireNonNull(target, "Target object cannot be null");
        List<Tool> discovered = AnnotatedToolScanner.scan(target);
        registerAll(discovered);
        return discovered.size();
    }

    // ==================== 工具来源（ToolSource）====================

    /**
     * 从 ToolSource 发现并注册工具
     *
     * 支持多种来源：MCP 服务端、SPI 扫描、自定义实现等。
     *
     * @param source 工具来源
     * @return 注册的工具数量
     */
    public int registerFrom(ToolSource source) {
        Objects.requireNonNull(source, "ToolSource cannot be null");
        List<Tool> discovered = source.discoverTools();
        if (discovered != null) {
            registerAll(discovered);
            return discovered.size();
        }
        return 0;
    }

    // ==================== SPI 自动扫描 ====================

    /**
     * 通过 Java SPI 扫描 classpath 上所有 Tool 实现并注册
     *
     * 模块只需在 META-INF/services/com.lightweightai.kernel.agent.Tool 中
     * 声明 Tool 实现类即可被自动发现。
     *
     * @return 注册的工具数量
     */
    public int scanAndRegister() {
        return ToolScanner.scanAndRegister(this);
    }

    /**
     * 通过 Java SPI 扫描并按条件过滤后注册
     *
     * @param filter 过滤条件
     * @return 注册的工具数量
     */
    public int scanAndRegister(Predicate<Tool> filter) {
        return ToolScanner.scanAndRegister(this, filter);
    }

    /**
     * 使用指定 ClassLoader 通过 SPI 扫描并注册
     *
     * @param classLoader 用于加载服务的 ClassLoader
     * @return 注册的工具数量
     */
    public int scanAndRegister(ClassLoader classLoader) {
        return ToolScanner.scanAndRegister(this, classLoader);
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
