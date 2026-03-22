package com.lightweightai.web.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RemotePluginScheduler 逻辑测试")
class RemotePluginSchedulerTest {

    @TempDir
    Path pluginDir;

    @Test
    @DisplayName("解析 manifest JSON")
    void parseManifest() {
        String json = """
            {
              "plugins": [
                {"name": "hello.jar", "url": "https://oss.com/hello.jar", "md5": "abc"},
                {"name": "weather.jar", "url": "https://oss.com/weather.jar", "md5": "def"}
              ]
            }
            """;

        List<RemotePluginScheduler.PluginEntry> entries = RemotePluginScheduler.parseManifest(json);
        assertEquals(2, entries.size());
        assertEquals("hello.jar", entries.get(0).name());
        assertEquals("https://oss.com/hello.jar", entries.get(0).url());
        assertEquals("abc", entries.get(0).md5());
    }

    @Test
    @DisplayName("空 manifest")
    void parseEmptyManifest() {
        assertTrue(RemotePluginScheduler.parseManifest("{}").isEmpty());
        assertTrue(RemotePluginScheduler.parseManifest("{\"plugins\":[]}").isEmpty());
    }

    @Test
    @DisplayName("无效 JSON")
    void parseInvalidJson() {
        assertTrue(RemotePluginScheduler.parseManifest("not json").isEmpty());
    }

    @Test
    @DisplayName("过滤无 name/url 的条目")
    void parseManifestFiltersInvalid() {
        String json = """
            {"plugins": [
                {"name": "good.jar", "url": "https://oss.com/good.jar"},
                {"name": null, "url": "https://oss.com/bad.jar"},
                {"name": "no-url.jar"}
            ]}
            """;
        List<RemotePluginScheduler.PluginEntry> entries = RemotePluginScheduler.parseManifest(json);
        assertEquals(1, entries.size());
        assertEquals("good.jar", entries.get(0).name());
    }

    @Test
    @DisplayName("需要下载：文件不存在")
    void needsDownload_notExists() {
        var entry = new RemotePluginScheduler.PluginEntry("new.jar", "url", "md5");
        assertTrue(RemotePluginScheduler.needsDownload(entry, pluginDir));
    }

    @Test
    @DisplayName("需要下载：md5 变化")
    void needsDownload_md5Changed() throws IOException {
        Files.writeString(pluginDir.resolve("p.jar"), "old");
        Files.writeString(pluginDir.resolve("p.jar.md5"), "old_md5");

        var entry = new RemotePluginScheduler.PluginEntry("p.jar", "url", "new_md5");
        assertTrue(RemotePluginScheduler.needsDownload(entry, pluginDir));
    }

    @Test
    @DisplayName("不需要下载：md5 相同")
    void needsDownload_md5Same() throws IOException {
        Files.writeString(pluginDir.resolve("p.jar"), "data");
        Files.writeString(pluginDir.resolve("p.jar.md5"), "same");

        var entry = new RemotePluginScheduler.PluginEntry("p.jar", "url", "same");
        assertFalse(RemotePluginScheduler.needsDownload(entry, pluginDir));
    }

    @Test
    @DisplayName("需要下载：无 md5 文件")
    void needsDownload_noMd5File() throws IOException {
        Files.writeString(pluginDir.resolve("p.jar"), "data");

        var entry = new RemotePluginScheduler.PluginEntry("p.jar", "url", "any");
        assertTrue(RemotePluginScheduler.needsDownload(entry, pluginDir));
    }

    @Test
    @DisplayName("需要下载：entry 无 md5")
    void needsDownload_nullMd5() throws IOException {
        Files.writeString(pluginDir.resolve("p.jar"), "data");

        var entry = new RemotePluginScheduler.PluginEntry("p.jar", "url", null);
        assertTrue(RemotePluginScheduler.needsDownload(entry, pluginDir));
    }
}
