package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI ToolSourceProvider — 扫描指定目录下的 CLI 工具包
 *
 * 目录结构约定：
 * cli-plugins/
 *   ├── tool-a/
 *   │   ├── cli-manifest.json
 *   │   └── bin/tool-a
 *   └── tool-b/
 *       ├── cli-manifest.json
 *       └── bin/tool-b
 *
 * 每个子目录含 cli-manifest.json 即为一个 CLI 工具。
 */
public class CliToolSourceProvider implements ToolSourceProvider {

    private static final Logger logger = LoggerFactory.getLogger(CliToolSourceProvider.class);
    private static final String MANIFEST_FILE = "cli-manifest.json";

    private final Path cliPluginsDir;
    private final CliExecutor executor;
    private final List<CliTool> registeredTools = new ArrayList<>();

    public CliToolSourceProvider(Path cliPluginsDir) {
        this(cliPluginsDir, new LocalProcessExecutor());
    }

    public CliToolSourceProvider(Path cliPluginsDir, CliExecutor executor) {
        this.cliPluginsDir = cliPluginsDir;
        this.executor = executor;
    }

    @Override
    public String sourceType() {
        return "cli";
    }

    @Override
    public void start(ToolRegistry registry) {
        if (!Files.isDirectory(cliPluginsDir)) {
            logger.debug("CLI plugins directory does not exist: {}", cliPluginsDir);
            return;
        }

        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(cliPluginsDir, Files::isDirectory)) {
            for (Path cliDir : dirs) {
                Path manifestPath = cliDir.resolve(MANIFEST_FILE);
                if (!Files.exists(manifestPath)) {
                    continue;
                }

                try {
                    CliManifest manifest = CliManifest.load(manifestPath);
                    CliTool tool = new CliTool(manifest, cliDir, executor);
                    registry.register(tool);
                    registeredTools.add(tool);
                    logger.info("Registered CLI tool: {} ({})", manifest.getName(), manifest.getDescription());
                } catch (IOException e) {
                    logger.error("Failed to load CLI manifest: {}", manifestPath, e);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to scan CLI plugins directory: {}", cliPluginsDir, e);
        }

        logger.info("CliToolSourceProvider: {} CLI tools registered from {}", registeredTools.size(), cliPluginsDir);
    }

    @Override
    public void stop(ToolRegistry registry) {
        for (CliTool tool : registeredTools) {
            registry.unregister(tool.getName());
        }
        registeredTools.clear();
        logger.info("CliToolSourceProvider stopped, tools unregistered");
    }
}
