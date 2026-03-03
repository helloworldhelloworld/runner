package com.lightweightai.kernel.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 工具自动扫描器
 *
 * 使用 Java SPI（ServiceLoader）自动发现并加载 classpath 上的 Tool 实现。
 * 任何模块只需在 META-INF/services/com.lightweightai.kernel.agent.Tool 中
 * 声明自己的 Tool 实现类，即可被自动发现和注册。
 *
 * <p>使用方式：</p>
 * <pre>
 * // 基础用法：扫描所有 Tool 实现
 * List&lt;Tool&gt; tools = ToolScanner.scan();
 *
 * // 按分类过滤
 * List&lt;Tool&gt; mathTools = ToolScanner.scanByCategory("math");
 *
 * // 按标签过滤
 * List&lt;Tool&gt; webTools = ToolScanner.scanByTag("web");
 *
 * // 自定义过滤
 * List&lt;Tool&gt; filtered = ToolScanner.scan(tool -&gt; tool.getName().startsWith("my_"));
 *
 * // 扫描并直接注册到 ToolRegistry
 * ToolScanner.scanAndRegister(registry);
 *
 * // 使用自定义 ClassLoader
 * List&lt;Tool&gt; tools = ToolScanner.scan(myClassLoader);
 * </pre>
 *
 * <p>模块接入方式：</p>
 * <ol>
 *   <li>实现 {@link Tool} 接口</li>
 *   <li>确保有无参构造函数</li>
 *   <li>在 META-INF/services/com.lightweightai.kernel.agent.Tool 中添加全限定类名</li>
 * </ol>
 */
public class ToolScanner {

    private static final Logger logger = LoggerFactory.getLogger(ToolScanner.class);

    private ToolScanner() {
        // Utility class
    }

    /**
     * 扫描 classpath 上所有通过 SPI 注册的 Tool 实现
     *
     * @return 发现的 Tool 列表
     */
    public static List<Tool> scan() {
        return scan(Thread.currentThread().getContextClassLoader());
    }

    /**
     * 使用指定的 ClassLoader 扫描 Tool 实现
     *
     * @param classLoader 用于加载服务的 ClassLoader
     * @return 发现的 Tool 列表
     */
    public static List<Tool> scan(ClassLoader classLoader) {
        return scan(classLoader, tool -> true);
    }

    /**
     * 扫描并按条件过滤 Tool 实现
     *
     * @param filter 过滤条件
     * @return 满足条件的 Tool 列表
     */
    public static List<Tool> scan(Predicate<Tool> filter) {
        return scan(Thread.currentThread().getContextClassLoader(), filter);
    }

    /**
     * 使用指定 ClassLoader 扫描并过滤 Tool 实现
     *
     * 同时扫描两种 SPI：
     * - {@link Tool} — 接口方式定义的工具
     * - {@link ToolSource} — 工具来源（注解方式等批量提供工具）
     *
     * @param classLoader 用于加载服务的 ClassLoader
     * @param filter      过滤条件
     * @return 满足条件的 Tool 列表
     */
    public static List<Tool> scan(ClassLoader classLoader, Predicate<Tool> filter) {
        Objects.requireNonNull(filter, "Filter cannot be null");

        List<Tool> tools = new ArrayList<>();

        // 1. 扫描 Tool SPI（接口方式）
        ServiceLoader<Tool> toolLoader = classLoader != null
            ? ServiceLoader.load(Tool.class, classLoader)
            : ServiceLoader.load(Tool.class);

        for (Tool tool : toolLoader) {
            try {
                if (tool.getName() == null || tool.getName().trim().isEmpty()) {
                    logger.warn("Skipping tool with null/empty name: {}", tool.getClass().getName());
                    continue;
                }
                if (filter.test(tool)) {
                    tools.add(tool);
                    logger.debug("Discovered tool via Tool SPI: {} ({})", tool.getName(), tool.getClass().getName());
                }
            } catch (Exception e) {
                logger.warn("Failed to load tool {}: {}", tool.getClass().getName(), e.getMessage());
            }
        }

        // 2. 扫描 ToolSource SPI（注解方式等批量来源）
        ServiceLoader<ToolSource> sourceLoader = classLoader != null
            ? ServiceLoader.load(ToolSource.class, classLoader)
            : ServiceLoader.load(ToolSource.class);

        for (ToolSource source : sourceLoader) {
            try {
                List<Tool> discovered = source.discoverTools();
                if (discovered != null) {
                    for (Tool tool : discovered) {
                        if (tool.getName() == null || tool.getName().trim().isEmpty()) {
                            logger.warn("Skipping tool with null/empty name from source: {}",
                                source.getClass().getName());
                            continue;
                        }
                        if (filter.test(tool)) {
                            tools.add(tool);
                            logger.debug("Discovered tool via ToolSource SPI: {} (source: {})",
                                tool.getName(), source.getClass().getName());
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to load tools from source {}: {}",
                    source.getClass().getName(), e.getMessage());
            }
        }

        logger.info("ToolScanner discovered {} tools", tools.size());
        return tools;
    }

    /**
     * 按分类扫描 Tool
     *
     * @param category 分类名称
     * @return 属于该分类的 Tool 列表
     */
    public static List<Tool> scanByCategory(String category) {
        Objects.requireNonNull(category, "Category cannot be null");
        return scan(tool -> {
            if (tool instanceof ToolMetadata) {
                return category.equals(((ToolMetadata) tool).getCategory());
            }
            return "default".equals(category);
        });
    }

    /**
     * 按标签扫描 Tool
     *
     * @param tag 标签名称
     * @return 包含该标签的 Tool 列表
     */
    public static List<Tool> scanByTag(String tag) {
        Objects.requireNonNull(tag, "Tag cannot be null");
        return scan(tool -> {
            if (tool instanceof ToolMetadata) {
                return ((ToolMetadata) tool).getTags().contains(tag);
            }
            return false;
        });
    }

    /**
     * 扫描所有 Tool 并注册到 ToolRegistry
     *
     * @param registry 目标注册表
     * @return 注册的工具数量
     */
    public static int scanAndRegister(ToolRegistry registry) {
        return scanAndRegister(registry, tool -> true);
    }

    /**
     * 扫描 Tool 并按条件过滤后注册到 ToolRegistry
     *
     * @param registry 目标注册表
     * @param filter   过滤条件
     * @return 注册的工具数量
     */
    public static int scanAndRegister(ToolRegistry registry, Predicate<Tool> filter) {
        Objects.requireNonNull(registry, "ToolRegistry cannot be null");
        Objects.requireNonNull(filter, "Filter cannot be null");

        List<Tool> tools = scan(filter);
        registry.registerAll(tools);

        logger.info("Registered {} tools via SPI scanning", tools.size());
        return tools.size();
    }

    /**
     * 使用指定 ClassLoader 扫描 Tool 并注册到 ToolRegistry
     *
     * @param registry    目标注册表
     * @param classLoader 用于加载服务的 ClassLoader
     * @return 注册的工具数量
     */
    public static int scanAndRegister(ToolRegistry registry, ClassLoader classLoader) {
        return scanAndRegister(registry, classLoader, tool -> true);
    }

    /**
     * 使用指定 ClassLoader 扫描并过滤后注册到 ToolRegistry
     *
     * @param registry    目标注册表
     * @param classLoader 用于加载服务的 ClassLoader
     * @param filter      过滤条件
     * @return 注册的工具数量
     */
    public static int scanAndRegister(ToolRegistry registry, ClassLoader classLoader, Predicate<Tool> filter) {
        Objects.requireNonNull(registry, "ToolRegistry cannot be null");
        Objects.requireNonNull(filter, "Filter cannot be null");

        List<Tool> tools = scan(classLoader, filter);
        registry.registerAll(tools);

        logger.info("Registered {} tools via SPI scanning", tools.size());
        return tools.size();
    }

    /**
     * 获取所有已扫描工具的名称（用于诊断）
     *
     * @return 工具名称列表
     */
    public static List<String> scanToolNames() {
        return scan().stream()
            .map(Tool::getName)
            .collect(Collectors.toList());
    }
}
