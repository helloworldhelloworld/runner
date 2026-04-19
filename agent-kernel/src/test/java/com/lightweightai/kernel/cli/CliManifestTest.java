package com.lightweightai.kernel.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CliManifest - CLI 工具清单")
class CliManifestTest {

    @TempDir
    Path tempDir;

    private Path writeManifest(String json) throws IOException {
        Path file = tempDir.resolve("cli-manifest.json");
        Files.writeString(file, json);
        return file;
    }

    @Nested
    @DisplayName("加载 manifest")
    class LoadTests {

        @Test
        @DisplayName("加载完整 manifest")
        void shouldLoadCompleteManifest() throws IOException {
            Path file = writeManifest("""
                {
                    "name": "my-tool",
                    "version": "1.0.0",
                    "description": "A test CLI tool",
                    "entry_point": "bin/run.sh",
                    "output_format": "json"
                }
                """);

            CliManifest manifest = CliManifest.load(file);

            assertEquals("my-tool", manifest.getName());
            assertEquals("1.0.0", manifest.getVersion());
            assertEquals("A test CLI tool", manifest.getDescription());
            assertEquals("bin/run.sh", manifest.getEntryPoint());
            assertEquals("json", manifest.getOutputFormat());
            assertTrue(manifest.isJsonOutput());
        }

        @Test
        @DisplayName("output_format 默认为 text")
        void shouldDefaultToTextOutput() throws IOException {
            Path file = writeManifest("""
                {
                    "name": "simple-tool",
                    "entry_point": "run.sh"
                }
                """);

            CliManifest manifest = CliManifest.load(file);
            assertEquals("text", manifest.getOutputFormat());
            assertFalse(manifest.isJsonOutput());
        }

        @Test
        @DisplayName("忽略未知字段")
        void shouldIgnoreUnknownFields() throws IOException {
            Path file = writeManifest("""
                {
                    "name": "tool",
                    "entry_point": "run.sh",
                    "unknown_field": "value",
                    "extra": 42
                }
                """);

            CliManifest manifest = CliManifest.load(file);
            assertEquals("tool", manifest.getName());
        }

        @Test
        @DisplayName("文件不存在时抛出 IOException")
        void shouldThrowForMissingFile() {
            Path missing = tempDir.resolve("nonexistent.json");
            assertThrows(IOException.class, () -> CliManifest.load(missing));
        }

        @Test
        @DisplayName("无效 JSON 抛出异常")
        void shouldThrowForInvalidJson() throws IOException {
            Path file = writeManifest("not valid json {{{");
            assertThrows(IOException.class, () -> CliManifest.load(file));
        }
    }

    @Nested
    @DisplayName("isJsonOutput")
    class JsonOutputTests {

        @Test
        @DisplayName("output_format=json 时返回 true（大小写不敏感）")
        void shouldBeCaseInsensitive() throws IOException {
            Path file = writeManifest("""
                {"name": "t", "entry_point": "r", "output_format": "JSON"}
                """);
            CliManifest manifest = CliManifest.load(file);
            assertTrue(manifest.isJsonOutput());
        }

        @Test
        @DisplayName("output_format=text 时返回 false")
        void shouldReturnFalseForText() throws IOException {
            Path file = writeManifest("""
                {"name": "t", "entry_point": "r", "output_format": "text"}
                """);
            CliManifest manifest = CliManifest.load(file);
            assertFalse(manifest.isJsonOutput());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString 包含名称和版本")
        void shouldIncludeNameAndVersion() throws IOException {
            Path file = writeManifest("""
                {"name": "my-cli", "version": "2.0", "description": "desc", "entry_point": "run"}
                """);
            CliManifest manifest = CliManifest.load(file);
            String str = manifest.toString();
            assertTrue(str.contains("my-cli"));
            assertTrue(str.contains("2.0"));
        }
    }
}
