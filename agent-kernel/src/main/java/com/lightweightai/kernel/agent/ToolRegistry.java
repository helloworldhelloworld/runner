package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.agent.annotation.AnnotatedToolScanner;
import com.lightweightai.kernel.agent.annotation.AnnotatedToolWrapper;
import com.lightweightai.kernel.agent.annotation.ClientTool;
import com.lightweightai.kernel.agent.directive.DirectiveDescriptor;
import com.lightweightai.kernel.agent.directive.DirectiveRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
    private final DirectiveRegistry directiveRegistry;
    private final List<ToolRegistryListener> listeners = new CopyOnWriteArrayList<>();

    public ToolRegistry() {
        this.tools = new ConcurrentHashMap<>();
        this.disabledTools = ConcurrentHashMap.newKeySet();
        this.directiveRegistry = new DirectiveRegistry();
    }

    // ==================== 监听器 ====================

    /**
     * 添加工具变更监听器
     */
    public void addListener(ToolRegistryListener listener) {
        Objects.requireNonNull(listener, "Listener cannot be null");
        listeners.add(listener);
    }

    /**
     * 移除工具变更监听器
     */
    public void removeListener(ToolRegistryListener listener) {
        listeners.remove(listener);
    }

    // ==================== 注册/注销 ====================

    /**
     * 注册工具
     *
     * 如果工具是 AnnotatedToolWrapper 且声明了 deviceType，自动走设备感知注册。
     * 如果目标位已是 DispatchingTool，则设为其 default。
     */
    public void register(Tool tool) {
        Objects.requireNonNull(tool, "Tool cannot be null");
        Objects.requireNonNull(tool.getName(), "Tool name cannot be null");

        // 注解声明了 deviceType 的工具，走设备感知注册
        if (tool instanceof AnnotatedToolWrapper atw && atw.hasDeviceBinding()) {
            registerWithDevice(tool, atw.getDeviceType(),
                VersionRange.parse(atw.getVersionRange()), 0);
            return;
        }

        // 普通注册：如果目标位已是 DispatchingTool，设为 default
        tools.compute(tool.getName(), (name, existing) -> {
            if (existing instanceof DispatchingTool dt) {
                dt.setDefaultTool(tool);
                return dt;
            }
            return tool;
        });
        registerDirectiveIfPresent(tool);
        fireToolRegistered(tool);
        registerClientToolAliasesIfPresent(tool);
    }

    /**
     * 注册设备专用工具变体
     *
     * @param tool         工具实现
     * @param deviceType   设备类型（如 "car", "phone"）
     * @param versionRange 版本范围
     */
    public void register(Tool tool, String deviceType, VersionRange versionRange) {
        registerWithDevice(tool, deviceType, versionRange, 0);
    }

    /**
     * 注册设备专用工具变体（带优先级）
     *
     * @param tool         工具实现
     * @param deviceType   设备类型
     * @param versionRange 版本范围
     * @param priority     优先级（高优先级优先匹配）
     */
    public void register(Tool tool, String deviceType, VersionRange versionRange, int priority) {
        registerWithDevice(tool, deviceType, versionRange, priority);
    }

    /**
     * 注销指定设备类型在某工具名下的所有绑定
     *
     * 如果 DispatchingTool 变空，则整体移除。
     */
    public void unregisterDevice(String toolName, String deviceType) {
        tools.computeIfPresent(toolName, (name, existing) -> {
            if (existing instanceof DispatchingTool dt) {
                dt.removeBindingsForDevice(deviceType);
                if (dt.isEmpty()) {
                    disabledTools.remove(name);
                    directiveRegistry.unregister(name);
                    return null; // 移除
                }
                return dt;
            }
            return existing;
        });
    }

    private void registerWithDevice(Tool tool, String deviceType, VersionRange versionRange, int priority) {
        Objects.requireNonNull(tool, "Tool cannot be null");
        Objects.requireNonNull(tool.getName(), "Tool name cannot be null");
        Objects.requireNonNull(deviceType, "deviceType cannot be null");
        Objects.requireNonNull(versionRange, "versionRange cannot be null");

        DeviceToolBinding binding = new DeviceToolBinding(deviceType, versionRange, tool, priority);

        tools.compute(tool.getName(), (name, existing) -> {
            if (existing instanceof DispatchingTool dt) {
                dt.addBinding(binding);
                return dt;
            } else if (existing != null) {
                // 升级已有普通 Tool 为 DispatchingTool，旧 tool 变 default
                DispatchingTool dt = new DispatchingTool(name, existing);
                dt.addBinding(binding);
                return dt;
            } else {
                // 首次注册
                DispatchingTool dt = new DispatchingTool(name);
                dt.addBinding(binding);
                return dt;
            }
        });
        registerDirectiveIfPresent(tool);
        fireToolRegistered(tool);
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
        Tool removed = tools.remove(name);
        disabledTools.remove(name);
        directiveRegistry.unregister(name);
        if (removed != null) {
            fireToolUnregistered(name);
        }
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
            fireToolDisabled(name);
        }
    }

    /**
     * 启用工具
     */
    public void enable(String name) {
        if (disabledTools.remove(name) && tools.containsKey(name)) {
            fireToolEnabled(name);
        }
    }

    /**
     * 检查工具是否启用
     */
    public boolean isEnabled(String name) {
        return tools.containsKey(name) && !disabledTools.contains(name);
    }


    /**
     * 获取 Directive 注解注册表。
     */
    public DirectiveRegistry getDirectiveRegistry() {
        return directiveRegistry;
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


    // ==================== 监听器触发 ====================

    private void fireToolRegistered(Tool tool) {
        for (ToolRegistryListener l : listeners) {
            l.onToolRegistered(tool);
        }
    }

    private void fireToolUnregistered(String toolName) {
        for (ToolRegistryListener l : listeners) {
            l.onToolUnregistered(toolName);
        }
    }

    private void fireToolEnabled(String toolName) {
        for (ToolRegistryListener l : listeners) {
            l.onToolEnabled(toolName);
        }
    }

    private void fireToolDisabled(String toolName) {
        for (ToolRegistryListener l : listeners) {
            l.onToolDisabled(toolName);
        }
    }

    private void registerDirectiveIfPresent(Tool tool) {
        ClientTool directive = tool.getClass().getAnnotation(ClientTool.class);
        if (directive == null) {
            return;
        }
        DirectiveDescriptor descriptor = DirectiveDescriptor.from(directive);
        directiveRegistry.register(descriptor);
        // 别名复用同一描述符，使得通过别名查询也能取到 down/up action。
        for (String alias : descriptor.getAliases()) {
            directiveRegistry.register(
                new DirectiveDescriptor(
                    alias,
                    java.util.Collections.emptyList(),
                    descriptor.getDownAction(),
                    descriptor.getUpAction(),
                    descriptor.getNamespace(),
                    descriptor.getTimeoutMs(),
                    descriptor.getVersion()
                )
            );
        }
    }

    private void registerClientToolAliasesIfPresent(Tool tool) {
        if (!(tool instanceof com.lightweightai.kernel.agent.ClientTool<?> clientTool)) {
            return;
        }
        for (Tool alias : clientTool.getAliasTools()) {
            tools.compute(alias.getName(), (name, existing) -> {
                if (existing instanceof DispatchingTool dt) {
                    dt.setDefaultTool(alias);
                    return dt;
                }
                return alias;
            });
            fireToolRegistered(alias);
        }
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
        directiveRegistry.clear();
    }

    /**
     * 按关键词搜索工具 — 匹配 name 或 description
     *
     * 用于 cli_discover 等场景：LLM 按能力需求描述搜索可用工具。
     * 简单实现：关键词不区分大小写，匹配 name 或 description 的子串。
     */
    public List<Tool> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getEnabled();
        }
        String lower = keyword.toLowerCase();
        return getEnabled().stream()
                .filter(t -> (t.getName() != null && t.getName().toLowerCase().contains(lower))
                        || (t.getDescription() != null && t.getDescription().toLowerCase().contains(lower)))
                .toList();
    }

    @Override
    public String toString() {
        return String.format("ToolRegistry{total=%d, enabled=%d}",
            tools.size(), enabledCount());
    }
}
