package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.agent.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CliToolSourceProvider - CLI 工具目录扫描")
class CliToolSourceProviderTest {

    @TempDir
    Path tempDir;

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    private void createCliPlugin(String name) throws IOException {
        Path pluginDir = tempDir.resolve(name);
        Files.createDirectories(pluginDir);

        Path entryPoint = pluginDir.resolve("run.sh");
        Files.writeString(entryPoint, "#!/bin/sh\necho hello");

        Path manifest = pluginDir.resolve("cli-manifest.json");
        Files.writeString(manifest, String.format("""
            {
                "name": "%s",
                "version": "1.0.0",
                "description": "Test tool %s",
                "entry_point": "run.sh"
            }
            """, name, name));
    }

    @Nested
    @DisplayName("工具发现")
    class DiscoveryTests {

        @Test
        @DisplayName("sourceType 返回 cli")
        void shouldReturnCliSourceType() {
            CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
            assertEquals("cli", provider.sourceType());
        }

        @Test
        @DisplayName("扫描并注册 CLI 工具")
        void shouldDiscoverAndRegisterTools() throws IOException {
            createCliPlugin("weather-cli");
            createCliPlugin("math-cli");

            CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
            provider.start(registry);

            assertTrue(registry.has("weather-cli"));
            assertTrue(registry.has("math-cli"));
            assertEquals(2, registry.getAll().size());
        }

        @Test
        @DisplayName("没有 manifest 的目录被忽略")
        void shouldSkipDirectoriesWithoutManifest() throws IOException {
            createCliPlugin("valid-tool");

            Path noManifestDir = tempDir.resolve("no-manifest");
            Files.createDirectories(noManifestDir);

            CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
            provider.start(registry);

            assertEquals(1, registry.getAll().size());
            assertTrue(registry.has("valid-tool"));
        }

        @Test
        @DisplayName("不存在的目录不抛异常")
        void shouldHandleNonexistentDirectory() {
            Path nonexistent = tempDir.resolve("does-not-exist");
            CliToolSourceProvider provider = new CliToolSourceProvider(nonexistent);

            assertDoesNotThrow(() -> provider.start(registry));
            assertEquals(0, registry.getAll().size());
        }

        @Test
        @DisplayName("无效 manifest 不影响其他工具注册")
        void shouldContinueAfterInvalidManifest() throws IOException {
            createCliPlugin("valid-tool");

            Path invalidDir = tempDir.resolve("invalid-tool");
            Files.createDirectories(invalidDir);
            Files.writeString(invalidDir.resolve("cli-manifest.json"), "{{invalid json}}");

            CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
            provider.start(registry);

            assertEquals(1, registry.getAll().size());
            assertTrue(registry.has("valid-tool"));
        }
    }

    @Nested
    @DisplayName("��命周期管理")
    class LifecycleTests {

        @Test
        @DisplayName("stop 注销所有已注册的工具")
        void shouldUnregisterOnStop() throws IOException {
            createCliPlugin("tool-a");
            createCliPlugin("tool-b");

            CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
            provider.start(registry);
            assertEquals(2, registry.getAll().size());

            provider.stop(registry);
            assertFalse(registry.has("tool-a"));
            assertFalse(registry.has("tool-b"));
        }

        @Test
        @DisplayName("stop 后 start 可重新注册")
        void shouldReregisterAfterRestart() throws IOException {
            createCliPlugin("tool-a");

            CliToolSourceProvider provider = new CliToolSourceProvider(tempDir);
            provider.start(registry);
            assertEquals(1, registry.getAll().size());

            provider.stop(registry);
            assertEquals(0, registry.getAll().size());

            provider.start(registry);
            assertEquals(1, registry.getAll().size());
        }
    }
}
