package com.lightweightai.kernel.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CliManifest - CLI 工具描述清单")
class CliManifestTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("从 JSON 文件加载 manifest")
    void loadFromJsonFile() throws IOException {
        Path manifest = tempDir.resolve("cli-manifest.json");
        Files.writeString(manifest, """
                {
                    "name": "git-search",
                    "version": "1.0.0",
                    "description": "Search git repositories",
                    "entry_point": "bin/git-search",
                    "output_format": "json"
                }
                """);

        CliManifest loaded = CliManifest.load(manifest);

        assertEquals("git-search", loaded.getName());
        assertEquals("1.0.0", loaded.getVersion());
        assertEquals("Search git repositories", loaded.getDescription());
        assertEquals("bin/git-search", loaded.getEntryPoint());
        assertEquals("json", loaded.getOutputFormat());
        assertTrue(loaded.isJsonOutput());
    }

    @Test
    @DisplayName("output_format 默认为 text")
    void defaultOutputFormatIsText() throws IOException {
        Path manifest = tempDir.resolve("cli-manifest.json");
        Files.writeString(manifest, """
                {
                    "name": "simple-tool",
                    "entry_point": "bin/run"
                }
                """);

        CliManifest loaded = CliManifest.load(manifest);

        assertEquals("text", loaded.getOutputFormat());
        assertFalse(loaded.isJsonOutput());
    }

    @Test
    @DisplayName("忽略未知 JSON 字段")
    void ignoresUnknownFields() throws IOException {
        Path manifest = tempDir.resolve("cli-manifest.json");
        Files.writeString(manifest, """
                {
                    "name": "tool",
                    "entry_point": "bin/tool",
                    "unknown_field": "should be ignored",
                    "another": 42
                }
                """);

        CliManifest loaded = CliManifest.load(manifest);
        assertEquals("tool", loaded.getName());
    }

    @Test
    @DisplayName("文件不存在时抛出 IOException")
    void throwsOnMissingFile() {
        Path missing = tempDir.resolve("nonexistent.json");
        assertThrows(IOException.class, () -> CliManifest.load(missing));
    }

    @Test
    @DisplayName("isJsonOutput 大小写不敏感")
    void isJsonOutputCaseInsensitive() {
        CliManifest manifest = new CliManifest();
        manifest.setOutputFormat("JSON");
        assertTrue(manifest.isJsonOutput());

        manifest.setOutputFormat("Json");
        assertTrue(manifest.isJsonOutput());
    }

    @Test
    @DisplayName("setter/getter 正常工作")
    void settersAndGettersWork() {
        CliManifest manifest = new CliManifest();
        manifest.setName("test");
        manifest.setVersion("2.0");
        manifest.setDescription("desc");
        manifest.setEntryPoint("bin/test");
        manifest.setOutputFormat("auto");

        assertEquals("test", manifest.getName());
        assertEquals("2.0", manifest.getVersion());
        assertEquals("desc", manifest.getDescription());
        assertEquals("bin/test", manifest.getEntryPoint());
        assertEquals("auto", manifest.getOutputFormat());
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringContainsKeyInfo() {
        CliManifest manifest = new CliManifest();
        manifest.setName("my-tool");
        manifest.setVersion("1.0");
        manifest.setDescription("A tool");

        String str = manifest.toString();
        assertTrue(str.contains("my-tool"));
        assertTrue(str.contains("1.0"));
        assertTrue(str.contains("A tool"));
    }
}
