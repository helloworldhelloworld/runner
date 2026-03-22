package com.lightweightai.kernel.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Predicate;

/**
 * 遗留扫描提供者 — 包装现有 ToolScanner SPI 扫描流程
 *
 * 将 ToolScanner.scan() + registry.register() 适配为 ToolSourceProvider 接口，
 * 零行为变更，纯适配器。
 *
 * <pre>
 * LegacyScanProvider provider = new LegacyScanProvider();
 * provider.start(registry);   // 等价于 registry.scanAndRegister()
 * provider.stop(registry);    // 注销所有 SPI 扫描注册的工具
 * </pre>
 */
public class LegacyScanProvider implements ToolSourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(LegacyScanProvider.class);

    private final Predicate<Tool> filter;
    private volatile boolean running = false;
    private List<String> registeredToolNames = List.of();

    /**
     * 创建无过滤的遗留扫描提供者
     */
    public LegacyScanProvider() {
        this(tool -> true);
    }

    /**
     * 创建带过滤条件的遗留扫描提供者
     *
     * @param filter 工具过滤条件，返回 true 表示接受
     */
    public LegacyScanProvider(Predicate<Tool> filter) {
        this.filter = filter;
    }

    @Override
    public String getName() {
        return "legacy-scan";
    }

    @Override
    public void start(ToolRegistry registry) {
        if (running) {
            logger.warn("LegacyScanProvider already running, ignoring start()");
            return;
        }

        List<Tool> tools = ToolScanner.scan(filter);
        for (Tool tool : tools) {
            registry.register(tool);
        }
        registeredToolNames = tools.stream().map(Tool::getName).toList();
        running = true;
        logger.info("LegacyScanProvider started, registered {} tools", tools.size());
    }

    @Override
    public void stop(ToolRegistry registry) {
        if (!running) {
            return;
        }
        for (String name : registeredToolNames) {
            registry.unregister(name);
        }
        registeredToolNames = List.of();
        running = false;
        logger.info("LegacyScanProvider stopped");
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public List<Tool> discoverTools() {
        return ToolScanner.scan(filter);
    }
}
