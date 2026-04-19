package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Exited;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Failed;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Stderr;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.Stdout;
import com.lightweightai.kernel.cli.CliExecutor.ExecEvent.TimedOut;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in Acceptance Test: CliTool + CliExecutor seam
 *
 * 验证 CliTool 不再直接依赖真实子进程。注入 FakeCliExecutor 即可完整
 * 驱动 Stdout/Stderr/Exited/TimedOut/Failed 对应的 ToolResult 映射。
 */
@DisplayName("Acceptance: CliTool 通过 CliExecutor seam 解耦子进程")
class CliToolSeamAcceptanceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Stdout + Exited(0) → ToolResult.success(stdout 拼接)")
    void stdoutAndZeroExitYieldsSuccess() throws IOException {
        CliManifest manifest = sampleManifest("ok");
        Path cliDir = writeManifestDir(manifest);
        FakeCliExecutor fake = FakeCliExecutor.of(
                new Stdout("line1"),
                new Stdout("line2"),
                new Exited(0)
        );

        CliTool tool = new CliTool(manifest, cliDir, fake);
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError(), "exit 0 应为 success");
        assertTrue(result.getContent().contains("line1"));
        assertTrue(result.getContent().contains("line2"));
    }

    @Test
    @DisplayName("Stderr + Exited(非零) → ToolResult.error(stderr 内容)")
    void stderrAndNonZeroExitYieldsError() throws IOException {
        CliManifest manifest = sampleManifest("fail");
        Path cliDir = writeManifestDir(manifest);
        FakeCliExecutor fake = FakeCliExecutor.of(
                new Stderr("something broke"),
                new Exited(1)
        );

        CliTool tool = new CliTool(manifest, cliDir, fake);
        ToolResult result = tool.execute(Map.of());

        assertTrue(result.isError(), "非零 exit 应为 error");
        assertTrue(result.getContent().contains("something broke"),
                "错误消息应含 stderr: " + result.getContent());
    }

    @Test
    @DisplayName("TimedOut → ToolResult.error(含 timeout 字样)")
    void timedOutYieldsTimeoutError() throws IOException {
        CliManifest manifest = sampleManifest("slow");
        Path cliDir = writeManifestDir(manifest);
        FakeCliExecutor fake = FakeCliExecutor.of(new TimedOut());

        CliTool tool = new CliTool(manifest, cliDir, fake);
        ToolResult result = tool.execute(Map.of());

        assertTrue(result.isError());
        String msg = result.getContent().toLowerCase();
        assertTrue(msg.contains("timeout") || msg.contains("timed out"),
                "错误应提到 timeout: " + result.getContent());
    }

    @Test
    @DisplayName("Failed(Throwable) → ToolResult.error(异常消息)")
    void failedYieldsError() throws IOException {
        CliManifest manifest = sampleManifest("lost");
        Path cliDir = writeManifestDir(manifest);
        FakeCliExecutor fake = FakeCliExecutor.of(
                new Failed(new IllegalStateException("binary gone"))
        );

        CliTool tool = new CliTool(manifest, cliDir, fake);
        ToolResult result = tool.execute(Map.of());

        assertTrue(result.isError());
        assertTrue(result.getContent().contains("binary gone"),
                "错误应含 cause.message: " + result.getContent());
    }

    @Test
    @DisplayName("CliTool 传给 executor 的 ExecRequest 含正确 command(含 entry_point)")
    void executorReceivesCommandWithEntryPoint() throws IOException {
        CliManifest manifest = sampleManifest("echo-tool");
        Path cliDir = writeManifestDir(manifest);
        FakeCliExecutor fake = FakeCliExecutor.of(new Exited(0));

        CliTool tool = new CliTool(manifest, cliDir, fake);
        tool.execute(Map.of("args", "hello"));

        assertEquals(1, fake.received().size(), "应仅调 executor 一次");
        var req = fake.received().get(0);
        assertTrue(req.command().get(0).contains("echo-tool"),
                "command[0] 应是 entry_point: " + req.command());
        assertEquals(cliDir, req.workingDir());
    }

    private CliManifest sampleManifest(String name) {
        CliManifest m = new CliManifest();
        m.setName(name);
        m.setDescription(name + " tool");
        m.setEntryPoint("bin/" + name);
        m.setOutputFormat("text");
        return m;
    }

    private Path writeManifestDir(CliManifest manifest) throws IOException {
        Path dir = tempDir.resolve(manifest.getName());
        Files.createDirectories(dir.resolve("bin"));
        // entry_point 文件存在即可(executor 是 fake,不会真去跑)
        Path bin = dir.resolve(manifest.getEntryPoint());
        Files.writeString(bin, "#!/bin/bash\nexit 0\n");
        bin.toFile().setExecutable(true);
        return dir;
    }
}
