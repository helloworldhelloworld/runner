package com.lightweightai.agent.plugin;

import com.lightweightai.kernel.plugin.PluginFunction;
import com.lightweightai.kernel.plugin.TypedPluginFunction;

import java.util.*;
import java.util.function.Function;

/**
 * 插件抽象类 - 简化版
 *
 * 用户只需继承这个类并实现getName()和getDescription()
 */
public abstract class Plugin {

    private final Map<String, PluginFunction> functions = new LinkedHashMap<>();

    /**
     * 获取插件名称
     *
     * @return 插件名称（例如："math", "weather"）
     */
    public abstract String getName();

    /**
     * 获取插件描述
     *
     * @return 插件功能描述
     */
    public abstract String getDescription();

    /**
     * 注册函数（供子类在构造函数中调用）- 简化版本
     *
     * @param name    函数名
     * @param handler 函数处理器
     */
    @SuppressWarnings("unchecked")
    protected void registerFunction(String name, Function<Map<String, Object>, Object> handler) {
        // For untyped functions, we use a wrapper that passes the Map directly
        registerFunction(name, "", (Class<Map<String, Object>>)(Class<?>)Map.class,
            (Function<Map<String, Object>, Object>) handler);
    }

    /**
     * 注册函数（类型安全版本）
     *
     * @param name           函数名
     * @param description    函数描述
     * @param parameterType  参数类型（使用Jackson注解定义）
     * @param handler        函数处理器
     */
    protected <T> void registerFunction(
        String name,
        String description,
        Class<T> parameterType,
        Function<T, Object> handler
    ) {
        if (functions.containsKey(name)) {
            throw new IllegalArgumentException(
                "Function '" + name + "' is already registered in plugin '" + getName() + "'"
            );
        }

        TypedPluginFunction function = new TypedPluginFunction(
            name,
            description,
            parameterType,
            handler
        );

        functions.put(name, function);
    }

    /**
     * 获取所有注册的函数
     *
     * @return 函数映射（函数名 -> PluginFunction）
     */
    public Map<String, PluginFunction> getFunctions() {
        return Collections.unmodifiableMap(functions);
    }

    /**
     * 获取指定名称的函数
     *
     * @param name 函数名
     * @return PluginFunction，如果不存在则返回null
     */
    public PluginFunction getFunction(String name) {
        return functions.get(name);
    }

    /**
     * 转换为 Claude API 工具定义列表
     *
     * @return Claude 工具定义列表
     */
    public List<Map<String, Object>> toClaudeTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (PluginFunction function : functions.values()) {
            tools.add(function.toJsonSchema());
        }
        return tools;
    }

    @Override
    public String toString() {
        return "Plugin{name='" + getName() + "', functions=" + functions.size() + "}";
    }
}
