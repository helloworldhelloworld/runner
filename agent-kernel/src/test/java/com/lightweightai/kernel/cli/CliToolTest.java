package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CliTool - CLI 工具执行")
class CliToolTest {

    @TempDir
    Path tempDir;

    private CliManifest createManifest(String name, String entryPoint) {
        CliManifest m = new CliManifest();
        m.setName(name);
        m.setVersion("1.0.0");
        m.setDescription("Test tool: " + name);
        m.setEntryPoint(entryPoint);
        m.setOutputFormat("text");
        return m;
    }

    private Path createScript(String filename, String content) throws IOException {
        Path script = tempDir.resolve(filename);
        Files.writeString(script, content);
        script.toFile().setExecutable(true);
        return script;
    }

    @Nested
    @DisplayName("基本属性")
    class BasicPropertyTests {

        @Test
        @DisplayName("getName 返回 manifest 中的名称")
        void shouldReturnManifestName() {
            CliManifest manifest = createManifest("my-tool", "run.sh");
            CliTool tool = new CliTool(manifest, tempDir);
            assertEquals("my-tool", tool.getName());
        }

        @Test
        @DisplayName("getDescription 返回 manifest 中的描述")
        void shouldReturnManifestDescription() {
            CliManifest manifest = createManifest("my-tool", "run.sh");
            CliTool tool = new CliTool(manifest, tempDir);
            assertEquals("Test tool: my-tool", tool.getDescription());
        }

        @Test
        @DisplayName("getSchema 返回包含 args 参数的 schema")
        void shouldReturnSchema() {
            CliManifest manifest = createManifest("my-tool", "run.sh");
            CliTool tool = new CliTool(manifest, tempDir);
            assertNotNull(tool.getSchema());
        }
    }

    @Nested
    @DisplayName("成功执行")
    class SuccessfulExecutionTests {

        @Test
        @DisplayName("执行成功的脚本返回 stdout")
        void shouldReturnStdoutOnSuccess() throws IOException {
            createScript("hello.sh", "#!/bin/sh\necho 'Hello World'");

            CliManifest manifest = createManifest("hello", "hello.sh");
            CliTool tool = new CliTool(manifest, tempDir);

            ToolResult result = tool.execute(Map.of());
            assertFalse(result.isError());
            assertEquals("Hello World", result.getContent());
        }

        @Test
        @DisplayName("带参数执行")
        void shouldPassArgsToScript() throws IOException {
            createScript("echo-args.sh", "#!/bin/sh\necho \"$@\"");

            CliManifest manifest = createManifest("echo-args", "echo-args.sh");
            CliTool tool = new CliTool(manifest, tempDir);

            ToolResult result = tool.execute(Map.of("args", "hello world"));
            assertFalse(result.isError());
            assertTrue(result.getContent().contains("hello"));
            assertTrue(result.getContent().contains("world"));
        }

        @Test
        @DisplayName("key=value 参数传递")
        void shouldPassKeyValueArgs() throws IOException {
            createScript("kv.sh", "#!/bin/sh\necho \"$@\"");

            CliManifest manifest = createManifest("kv", "kv.sh");
            CliTool tool = new CliTool(manifest, tempDir);

            ToolResult result = tool.execute(Map.of("city", "Beijing", "unit", "celsius"));
            assertFalse(result.isError());
            String content = result.getContent();
            assertTrue(content.contains("--city"));
            assertTrue(content.contains("Beijing"));
        }
    }

    @Nested
    @DisplayName("错误处理")
    class ErrorHandlingTests {

        @Test
        @DisplayName("脚本不存在时返回错误")
        void shouldReturnErrorForMissingEntryPoint() {
            CliManifest manifest = createManifest("missing", "nonexistent.sh");
            CliTool tool = new CliTool(manifest, tempDir);

            ToolResult result = tool.execute(Map.of());
            assertTrue(result.isError());
            assertTrue(result.getContent().contains("not found"));
        }

        @Test
        @DisplayName("exit code != 0 时返回 stderr")
        void shouldReturnStderrOnFailure() throws IOException {
            createScript("fail.sh", "#!/bin/sh\necho 'error msg' >&2\nexit 1");

            CliManifest manifest = createManifest("fail", "fail.sh");
            CliTool tool = new CliTool(manifest, tempDir);

            ToolResult result = tool.execute(Map.of());
            assertTrue(result.isError());
            assertTrue(result.getContent().contains("error msg"));
        }

        @Test
        @DisplayName("超时时返回超时错误")
        void shouldReturnTimeoutError() throws IOException {
            createScript("slow.sh", "#!/bin/sh\nsleep 60");

            CliManifest manifest = createManifest("slow", "slow.sh");
            CliTool tool = new CliTool(manifest, tempDir);
            tool.setTimeoutSeconds(1);

            ToolResult result = tool.execute(Map.of());
            assertTrue(result.isError());
            assertTrue(result.getContent().contains("timed out"));
        }
    }

    @Nested
    @DisplayName("流式执行")
    class StreamingExecutionTests {

        @Test
        @DisplayName("逐行输出 PROGRESS 事件")
        void shouldEmitProgressPerLine() throws IOException {
            createScript("lines.sh", "#!/bin/sh\necho 'line1'\necho 'line2'\necho 'line3'");

            CliManifest manifest = createManifest("lines", "lines.sh");
            CliTool tool = new CliTool(manifest, tempDir);

            List<ToolResultChunk> chunks = tool.executeReactive(Map.of())
                    .collectList().block();

            assertNotNull(chunks);
            long progressCount = chunks.stream()
                    .filter(c -> c.getType() == ToolResultChunk.ChunkType.PROGRESS)
                    .count();
            assertTrue(progressCount >= 3, "Should have at least 3 PROGRESS chunks for 3 lines");

            long completeCount = chunks.stream()
                    .filter(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE)
                    .count();
            assertEquals(1, completeCount, "Should have exactly 1 COMPLETE chunk");
        }

        @Test
        @DisplayName("流式错误产生 ERROR chunk")
        void shouldEmitErrorChunkOnFailure() throws IOException {
            createScript("stream-fail.sh", "#!/bin/sh\nexit 1");

            CliManifest manifest = createManifest("stream-fail", "stream-fail.sh");
            CliTool tool = new CliTool(manifest, tempDir);

            List<ToolResultChunk> chunks = tool.executeReactive(Map.of())
                    .collectList().block();

            assertNotNull(chunks);
            boolean hasError = chunks.stream()
                    .anyMatch(c -> c.getType() == ToolResultChunk.ChunkType.ERROR);
            assertTrue(hasError, "Should have an ERROR chunk");
        }
    }
}
