package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliToolSourceProviderTest {

    @TempDir
    Path tempDir;

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    void sourceTypeIsCli() {
        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        assertEquals("cli", provider.sourceType());
    }

    @Test
    void nonExistentDirectoryDoesNotThrow() {
        Path noDir = tempDir.resolve("nonexistent");
        CliToolSourceProvider provider = new CliToolSourceProvider(noDir);
        assertDoesNotThrow(() -> provider.start(registry));
        assertEquals(0, registry.getAll().size());
    }

    @Test
    void emptyDirectoryRegistersNoTools() {
        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);
        assertEquals(0, registry.getAll().size());
    }

    @Test
    void subdirWithManifestRegistered() throws IOException {
        Path toolDir = Files.createDirectory(tempDir.resolve("my-tool"));
        Files.writeString(toolDir.resolve("cli-manifest.json"), """
                {
                    "name": "my-tool",
                    "version": "1.0",
                    "description": "A test tool",
                    "entry_point": "bin/run.sh"
                }
                """);

        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);

        assertTrue(registry.has("my-tool"));
        Tool tool = registry.get("my-tool").orElseThrow();
        assertEquals("my-tool", tool.getName());
        assertEquals("A test tool", tool.getDescription());
    }

    @Test
    void subdirWithoutManifestSkipped() throws IOException {
        Files.createDirectory(tempDir.resolve("no-manifest-dir"));

        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);
        assertEquals(0, registry.getAll().size());
    }

    @Test
    void malformedManifestSkippedOthersRegistered() throws IOException {
        Path bad = Files.createDirectory(tempDir.resolve("bad-tool"));
        Files.writeString(bad.resolve("cli-manifest.json"), "not json");

        Path good = Files.createDirectory(tempDir.resolve("good-tool"));
        Files.writeString(good.resolve("cli-manifest.json"), """
                {"name": "good-tool", "entry_point": "bin/run"}
                """);

        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);

        assertTrue(registry.has("good-tool"));
        assertFalse(registry.has("bad-tool"));
    }

    @Test
    void stopUnregistersAllTools() throws IOException {
        Path toolDir = Files.createDirectory(tempDir.resolve("tool-a"));
        Files.writeString(toolDir.resolve("cli-manifest.json"), """
                {"name": "tool-a", "entry_point": "bin/a"}
                """);

        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);
        assertTrue(registry.has("tool-a"));

        provider.stop(registry);
        assertFalse(registry.has("tool-a"));
    }

    @Test
    void multipleToolsRegistered() throws IOException {
        for (int i = 1; i <= 3; i++) {
            Path dir = Files.createDirectory(tempDir.resolve("tool-" + i));
            Files.writeString(dir.resolve("cli-manifest.json"),
                    String.format("""
                            {"name": "tool-%d", "entry_point": "bin/run"}
                            """, i));
        }

        CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
        provider.start(registry);

        assertTrue(registry.has("tool-1"));
        assertTrue(registry.has("tool-2"));
        assertTrue(registry.has("tool-3"));
    }
}
