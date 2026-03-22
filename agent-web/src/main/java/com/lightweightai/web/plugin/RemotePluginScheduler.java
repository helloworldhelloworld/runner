package com.lightweightai.web.plugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lightweightai.kernel.agent.DynamicPluginProvider;
import com.lightweightai.kernel.agent.ToolRegistry;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 定时远程插件拉取器
 *
 * 从配置的 manifest URL 定时拉取插件清单，增量下载新增/更新的 jar 到 plugins/ 目录。
 * DynamicPluginProvider 的 WatchService 会自动检测并加载。
 *
 * 配置:
 *   app.plugins.remote.enabled: true
 *   app.plugins.remote.manifest-url: https://bucket.oss.com/plugins/manifest.json
 *   app.plugins.remote.poll-interval: 300000  # 5分钟（毫秒）
 *
 * manifest.json 格式:
 * {
 *   "plugins": [
 *     {"name": "hello-plugin.jar", "url": "https://bucket.oss.com/hello-plugin.jar", "md5": "abc123"}
 *   ]
 * }
 */
@Component
@ConditionalOnBean(DynamicPluginProvider.class)
@ConditionalOnProperty(name = "app.plugins.remote.enabled", havingValue = "true")
public class RemotePluginScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RemotePluginScheduler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DynamicPluginProvider pluginProvider;
    private final ToolRegistry toolRegistry;
    private final OkHttpClient httpClient;

    @Value("${app.plugins.remote.manifest-url}")
    private String manifestUrl;

    @Value("${app.plugins.dir:plugins}")
    private String pluginDirPath;

    public RemotePluginScheduler(DynamicPluginProvider pluginProvider, ToolRegistry toolRegistry) {
        this.pluginProvider = pluginProvider;
        this.toolRegistry = toolRegistry;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 启动时立即拉取一次
     */
    @PostConstruct
    public void initialFetch() {
        logger.info("Remote plugin scheduler initialized, manifest: {}", manifestUrl);
        fetchAndSync();
    }

    /**
     * 定时拉取（默认 5 分钟）
     */
    @Scheduled(fixedDelayString = "${app.plugins.remote.poll-interval:300000}")
    public void scheduledFetch() {
        fetchAndSync();
    }

    /**
     * 拉取 manifest 并同步插件
     */
    public void fetchAndSync() {
        try {
            logger.debug("Fetching remote plugin manifest: {}", manifestUrl);

            String manifestJson = fetchUrl(manifestUrl);
            List<PluginEntry> entries = parseManifest(manifestJson);

            if (entries.isEmpty()) {
                logger.debug("Remote manifest is empty or invalid");
                return;
            }

            Path pluginDir = Path.of(pluginDirPath);
            Files.createDirectories(pluginDir);

            int downloaded = 0;
            for (PluginEntry entry : entries) {
                if (needsDownload(entry, pluginDir)) {
                    try {
                        downloadFile(entry.url, pluginDir.resolve(entry.name));
                        // 保存 md5 用于下次比对
                        if (entry.md5 != null) {
                            Files.writeString(pluginDir.resolve(entry.name + ".md5"), entry.md5);
                        }
                        downloaded++;
                        logger.info("Downloaded remote plugin: {} from {}", entry.name, entry.url);
                    } catch (Exception e) {
                        logger.error("Failed to download plugin: {}", entry.name, e);
                    }
                }
            }

            if (downloaded > 0) {
                logger.info("Remote plugin sync: {} new/updated plugins downloaded", downloaded);
            }

            // 清理本地多余的 jar（不在 manifest 中的）
            cleanRemovedPlugins(entries, pluginDir);

        } catch (Exception e) {
            logger.warn("Failed to fetch remote plugin manifest: {}", e.getMessage());
        }
    }

    // ==================== Internal ====================

    private void cleanRemovedPlugins(List<PluginEntry> manifest, Path pluginDir) {
        Set<String> manifestNames = manifest.stream()
            .map(PluginEntry::name)
            .collect(java.util.stream.Collectors.toSet());

        try (var stream = Files.list(pluginDir)) {
            stream.filter(p -> p.toString().endsWith(".jar"))
                .map(p -> p.getFileName().toString())
                .filter(name -> !manifestNames.contains(name))
                .forEach(name -> {
                    try {
                        Files.deleteIfExists(pluginDir.resolve(name));
                        Files.deleteIfExists(pluginDir.resolve(name + ".md5"));
                        logger.info("Removed plugin not in manifest: {}", name);
                        // WatchService DELETE 事件会自动触发 unregisterPlugin
                    } catch (IOException e) {
                        logger.warn("Failed to delete removed plugin: {}", name, e);
                    }
                });
        } catch (IOException e) {
            logger.warn("Failed to list plugin directory for cleanup: {}", pluginDir, e);
        }
    }

    static List<PluginEntry> parseManifest(String json) {
        try {
            Map<String, Object> manifest = MAPPER.readValue(json, new TypeReference<>() {});
            Object pluginsObj = manifest.get("plugins");
            if (pluginsObj == null) return List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, String>> pluginsList = (List<Map<String, String>>) pluginsObj;
            return pluginsList.stream()
                .map(m -> new PluginEntry(m.get("name"), m.get("url"), m.get("md5")))
                .filter(e -> e.name != null && e.url != null)
                .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    static boolean needsDownload(PluginEntry entry, Path pluginDir) {
        Path jarFile = pluginDir.resolve(entry.name);
        if (!Files.exists(jarFile)) return true;
        if (entry.md5 == null) return true;

        Path md5File = pluginDir.resolve(entry.name + ".md5");
        if (!Files.exists(md5File)) return true;

        try {
            String localMd5 = Files.readString(md5File).trim();
            return !localMd5.equals(entry.md5);
        } catch (IOException e) {
            return true;
        }
    }

    private String fetchUrl(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private void downloadFile(String url, Path target) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code() + " from " + url);
            }
            try (InputStream in = response.body().byteStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    record PluginEntry(String name, String url, String md5) {}
}
