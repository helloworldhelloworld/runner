package com.lightweightai.kernel.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 动态插件 Provider — 目录扫描 + WatchService 热加载。
 *
 * 监听指定目录中的 jar 文件变化：
 * - 新增 jar → URLClassLoader 加载 → SPI 扫描 → 注册工具
 * - 删除 jar → 注销该 jar 提供的所有工具
 *
 * 也支持通过 registerPlugin/unregisterPlugin 手动管理插件。
 */
public class DynamicPluginProvider implements ToolSourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(DynamicPluginProvider.class);

    private final Path pluginDir;
    private final ConcurrentHashMap<String, PluginEntry> loadedPlugins = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile WatchService watchService;
    private volatile Thread watchThread;

    public DynamicPluginProvider(Path pluginDir) {
        this.pluginDir = pluginDir;
    }

    @Override
    public String sourceType() {
        return "dynamic-plugin";
    }

    @Override
    public void start(ToolRegistry registry) {
        // 确保目录存在
        try {
            Files.createDirectories(pluginDir);
        } catch (IOException e) {
            logger.error("Failed to create plugin directory: {}", pluginDir, e);
            return;
        }

        running.set(true);

        // 扫描已有的 jar 文件
        scanExistingJars(registry);

        // 启动 WatchService 监听
        startWatching(registry);

        logger.info("DynamicPluginProvider started, watching: {}, loaded {} plugins",
            pluginDir, loadedPlugins.size());
    }

    @Override
    public void stop(ToolRegistry registry) {
        running.set(false);

        // 停止 WatchService
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {}
            watchService = null;
        }

        // 中断监听线程
        if (watchThread != null) {
            watchThread.interrupt();
            watchThread = null;
        }

        // 注销所有动态加载的工具
        for (Map.Entry<String, PluginEntry> entry : loadedPlugins.entrySet()) {
            PluginEntry plugin = entry.getValue();
            plugin.toolNames.forEach(registry::unregister);
            closeClassLoader(plugin.classLoader);
        }
        loadedPlugins.clear();

        logger.info("DynamicPluginProvider stopped");
    }

    /**
     * 手动注册一个 plugin 及其工具
     */
    public void registerPlugin(String pluginId, List<Tool> tools, ToolRegistry registry) {
        List<String> toolNames = new ArrayList<>();
        for (Tool tool : tools) {
            if (registry.has(tool.getName())) {
                logger.warn("Plugin {} skipped tool '{}': already registered",
                    pluginId, tool.getName());
                continue;
            }
            registry.register(tool);
            toolNames.add(tool.getName());
        }
        loadedPlugins.put(pluginId, new PluginEntry(toolNames, null));
        logger.info("Plugin registered: {} with {} tools: {}",
            pluginId, toolNames.size(), toolNames);
    }

    /**
     * 手动注销一个 plugin 及其工具
     */
    public void unregisterPlugin(String pluginId, ToolRegistry registry) {
        PluginEntry entry = loadedPlugins.remove(pluginId);
        if (entry != null) {
            entry.toolNames.forEach(registry::unregister);
            closeClassLoader(entry.classLoader);
            logger.info("Plugin unregistered: {} (removed {} tools)", pluginId, entry.toolNames.size());
        }
    }

    /**
     * 获取已加载的 plugin 名称列表
     */
    public Set<String> getLoadedPlugins() {
        return Collections.unmodifiableSet(loadedPlugins.keySet());
    }

    // ==================== Internal ====================

    private void scanExistingJars(ToolRegistry registry) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginDir, "*.jar")) {
            for (Path jarPath : stream) {
                loadJar(jarPath, registry);
            }
        } catch (IOException e) {
            logger.warn("Failed to scan plugin directory: {}", pluginDir, e);
        }
    }

    private void startWatching(ToolRegistry registry) {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            pluginDir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

            watchThread = new Thread(() -> watchLoop(registry), "plugin-watcher");
            watchThread.setDaemon(true);
            watchThread.start();
        } catch (IOException e) {
            logger.warn("Failed to start WatchService for plugin directory: {}", pluginDir, e);
        }
    }

    private void watchLoop(ToolRegistry registry) {
        while (running.get()) {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path fileName = pathEvent.context();

                    if (!fileName.toString().endsWith(".jar")) continue;

                    Path fullPath = pluginDir.resolve(fileName);
                    String pluginId = fileName.toString();

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE
                            || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        // 短暂等待文件写入完成
                        Thread.sleep(500);
                        // 热更新：先卸载旧版再加载新版
                        if (loadedPlugins.containsKey(pluginId)) {
                            unregisterPlugin(pluginId, registry);
                            logger.info("Hot-reload: unloaded old version of {}", pluginId);
                        }
                        loadJar(fullPath, registry);
                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        unregisterPlugin(pluginId, registry);
                    }
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            } catch (Exception e) {
                logger.warn("Error in plugin watch loop", e);
            }
        }
    }

    private void loadJar(Path jarPath, ToolRegistry registry) {
        String pluginId = jarPath.getFileName().toString();
        if (loadedPlugins.containsKey(pluginId)) {
            logger.debug("Plugin already loaded: {}", pluginId);
            return;
        }

        try {
            URL jarUrl = jarPath.toUri().toURL();
            URLClassLoader classLoader = new URLClassLoader(
                new URL[]{jarUrl}, getClass().getClassLoader());

            // 用 jar 的 ClassLoader 扫描 SPI
            List<Tool> allTools = ToolScanner.scan(classLoader, t -> true);

            // 过滤：跳过已存在的工具（冲突检测）
            List<String> toolNames = new ArrayList<>();
            for (Tool tool : allTools) {
                if (registry.has(tool.getName())) {
                    logger.warn("Plugin {} skipped tool '{}': already registered",
                        pluginId, tool.getName());
                    continue;
                }
                registry.register(tool);
                toolNames.add(tool.getName());
            }

            if (toolNames.isEmpty()) {
                classLoader.close();
                logger.debug("No new tools from jar: {}", pluginId);
                return;
            }

            loadedPlugins.put(pluginId, new PluginEntry(toolNames, classLoader));
            logger.info("Loaded plugin jar: {} with {} tools: {}",
                pluginId, toolNames.size(), toolNames);

        } catch (Exception e) {
            logger.error("Failed to load plugin jar: {}", jarPath, e);
        }
    }

    private void closeClassLoader(URLClassLoader classLoader) {
        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException ignored) {}
        }
    }

    // ==================== Internal Data ====================

    private static class PluginEntry {
        final List<String> toolNames;
        final URLClassLoader classLoader;

        PluginEntry(List<String> toolNames, URLClassLoader classLoader) {
            this.toolNames = toolNames;
            this.classLoader = classLoader;
        }
    }
}
