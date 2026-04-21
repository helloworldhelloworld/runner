package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.agent.ToolRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CliToolSourceProvider - CLI 工具目录扫描与注册")
class CliToolSourceProviderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("扫描包含 manifest 的子目录并注册工具")
    void scansAndRegistersToolsFromSubdirectories() throws IOException {
        Path toolDir = tempDir.resolve("tool-a");
        Files.createDirectories(toolDir);
        Files.writeString(toolDir.resolve("cli-manifest.json"), """
                {
                    "name": "tool-a",
                    "description": "Tool A",
                    "entry_point": "bin/run"
                }
                """);

        ToolRegistry registry = new ToolRegistry();
        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);

        assertTrue(registry.get("tool-a").isPresent());
        assertEquals("cli", provider.sourceType());
    }

    @Test
    @DisplayName("跳过没有 manifest 的子目录")
    void skipsDirectoriesWithoutManifest() throws IOException {
        Path toolDir = tempDir.resolve("no-manifest");
        Files.createDirectories(toolDir);
        Files.writeString(toolDir.resolve("readme.txt"), "not a tool");

        ToolRegistry registry = new ToolRegistry();
        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);

        assertEquals(0, registry.getAll().size());
    }

    @Test
    @DisplayName("扫描多个 CLI 工具")
    void scansMultipleTools() throws IOException {
        for (String name : new String[]{"tool-x", "tool-y", "tool-z"}) {
            Path dir = tempDir.resolve(name);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("cli-manifest.json"),
                    "{\"name\":\"" + name + "\",\"entry_point\":\"bin/run\"}");
        }

        ToolRegistry registry = new ToolRegistry();
        new CliToolSourceProvider(tempDir).start(registry);

        assertEquals(3, registry.getAll().size());
    }

    @Test
    @DisplayName("目录不存在时不报错")
    void nonexistentDirectoryDoesNotThrow() {
        Path missing = tempDir.resolve("nonexistent-dir");
        ToolRegistry registry = new ToolRegistry();

        assertDoesNotThrow(() ->
                new CliToolSourceProvider(missing).start(registry));
        assertEquals(0, registry.getAll().size());
    }

    @Test
    @DisplayName("stop() 反注册所有 CLI 工具")
    void stopUnregistersAllTools() throws IOException {
        Path toolDir = tempDir.resolve("tool-a");
        Files.createDirectories(toolDir);
        Files.writeString(toolDir.resolve("cli-manifest.json"), """
                {"name":"tool-a","entry_point":"bin/run"}
                """);

        ToolRegistry registry = new ToolRegistry();
        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);
        assertTrue(registry.get("tool-a").isPresent());

        provider.stop(registry);
        assertFalse(registry.get("tool-a").isPresent());
    }

    @Test
    @DisplayName("manifest 解析失败时跳过该工具不影响其他工具")
    void malformedManifestSkipped() throws IOException {
        Path goodDir = tempDir.resolve("good");
        Files.createDirectories(goodDir);
        Files.writeString(goodDir.resolve("cli-manifest.json"),
                "{\"name\":\"good-tool\",\"entry_point\":\"bin/run\"}");

        Path badDir = tempDir.resolve("bad");
        Files.createDirectories(badDir);
        Files.writeString(badDir.resolve("cli-manifest.json"), "NOT VALID JSON!!!");

        ToolRegistry registry = new ToolRegistry();
        new CliToolSourceProvider(tempDir).start(registry);

        assertTrue(registry.get("good-tool").isPresent());
        assertEquals(1, registry.getAll().size());
    }
}
