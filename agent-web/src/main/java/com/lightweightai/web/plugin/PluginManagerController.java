package com.lightweightai.web.plugin;

import com.lightweightai.kernel.agent.DynamicPluginProvider;
import com.lightweightai.kernel.agent.ToolRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 插件管理 HTTP 接口
 *
 * POST   /plugins/load?url=...&name=...  — 从远程 URL 下载 jar 并加载
 * DELETE /plugins/{pluginId}             — 卸载插件
 * GET    /plugins                        — 列出已加载的插件
 * GET    /plugins/tools                  — 列出所有已注册的工具
 */
@RestController
@RequestMapping("/plugins")
@ConditionalOnBean(DynamicPluginProvider.class)
public class PluginManagerController {

    private static final Logger logger = LoggerFactory.getLogger(PluginManagerController.class);

    private final DynamicPluginProvider pluginProvider;
    private final ToolRegistry toolRegistry;
    private final OkHttpClient httpClient;

    @Value("${app.plugins.dir:plugins}")
    private String pluginDirPath;

    @Autowired
    public PluginManagerController(DynamicPluginProvider pluginProvider,
                                   ToolRegistry toolRegistry) {
        this.pluginProvider = pluginProvider;
        this.toolRegistry = toolRegistry;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 从远程 URL 下载 jar 并加载为插件
     *
     * POST /plugins/load?url=https://example.com/my-plugin.jar&name=my-plugin.jar
     */
    @PostMapping("/load")
    public Map<String, Object> loadFromUrl(
            @RequestParam("url") String url,
            @RequestParam(value = "name", required = false) String name) {

        // 从 URL 推断文件名
        if (name == null || name.isBlank()) {
            name = url.substring(url.lastIndexOf('/') + 1);
            if (!name.endsWith(".jar")) {
                name = name + ".jar";
            }
        }

        // 如果已加载，先卸载
        if (pluginProvider.getLoadedPlugins().contains(name)) {
            pluginProvider.unregisterPlugin(name, toolRegistry);
            logger.info("Unloaded existing plugin before reload: {}", name);
        }

        Path pluginDir = Path.of(pluginDirPath);
        Path targetPath = pluginDir.resolve(name);

        try {
            // 下载 jar
            downloadFile(url, targetPath);
            logger.info("Downloaded plugin jar: {} → {}", url, targetPath);

            // WatchService 会自动检测新文件并加载
            // 但为了即时生效，等一下让 WatchService 触发
            // 或者直接调用 loadJar（但它是 private）
            // 简单方案：等 WatchService 500ms + 处理时间
            Thread.sleep(1500);

            // 验证是否已加载
            boolean loaded = pluginProvider.getLoadedPlugins().contains(name);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", loaded);
            result.put("pluginId", name);
            result.put("source", url);
            result.put("loadedPlugins", pluginProvider.getLoadedPlugins());
            result.put("totalTools", toolRegistry.size());
            return result;

        } catch (Exception e) {
            logger.error("Failed to load plugin from URL: {}", url, e);
            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "pluginId", name
            );
        }
    }

    /**
     * 卸载插件
     *
     * DELETE /plugins/{pluginId}
     */
    @DeleteMapping("/{pluginId}")
    public Map<String, Object> unload(@PathVariable("pluginId") String pluginId) {
        boolean existed = pluginProvider.getLoadedPlugins().contains(pluginId);
        if (existed) {
            pluginProvider.unregisterPlugin(pluginId, toolRegistry);

            // 删除 jar 文件
            Path jarFile = Path.of(pluginDirPath).resolve(pluginId);
            try {
                Files.deleteIfExists(jarFile);
            } catch (IOException e) {
                logger.warn("Failed to delete jar file: {}", jarFile, e);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", existed);
        result.put("pluginId", pluginId);
        result.put("loadedPlugins", pluginProvider.getLoadedPlugins());
        result.put("totalTools", toolRegistry.size());
        return result;
    }

    /**
     * 列出已加载的插件
     *
     * GET /plugins
     */
    @GetMapping
    public Map<String, Object> listPlugins() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("loadedPlugins", pluginProvider.getLoadedPlugins());
        result.put("totalTools", toolRegistry.size());
        return result;
    }

    /**
     * 列出所有已注册的工具
     *
     * GET /plugins/tools
     */
    @GetMapping("/tools")
    public Map<String, Object> listTools() {
        List<Map<String, String>> tools = toolRegistry.getEnabled().stream()
            .map(t -> Map.of(
                "name", t.getName(),
                "description", t.getDescription()
            ))
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", tools);
        result.put("totalTools", tools.size());
        result.put("loadedPlugins", pluginProvider.getLoadedPlugins());
        return result;
    }

    // ==================== Internal ====================

    private void downloadFile(String url, Path target) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Download failed: HTTP " + response.code() + " from " + url);
            }
            if (response.body() == null) {
                throw new IOException("Empty response body from " + url);
            }
            try (InputStream in = response.body().byteStream()) {
                Files.createDirectories(target.getParent());
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
