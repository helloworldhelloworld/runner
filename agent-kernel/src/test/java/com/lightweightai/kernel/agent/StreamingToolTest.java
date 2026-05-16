package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.core.ToolResultChunk.ChunkType;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.FluxSink;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StreamingTool 单元测试
 *
 * StreamingTool 是实现真实流式输出的 Tool 基类。
 * 子类实现 executeStreaming(args, emitter)，通过 emitter 推送 PROGRESS / COMPLETE / ERROR 事件。
 *
 * 测试覆盖：
 * 1. executeReactive 流式路径 — 验证 chunk 顺序和 payload 内容
 * 2. execute 同步回退路径 — 验证过滤逻辑和最终返回值
 * 3. 异常捕获 — 验证 executeStreaming 抛异常时自动转为 ERROR chunk
 */
@DisplayName("StreamingTool - 流式工具基类")
class StreamingToolTest {

    // ==================== 流式路径测试 ====================

    @Test
    @DisplayName("executeReactive 依次发射 PROGRESS 和 COMPLETE chunk，顺序和 payload 正确")
    void executeReactiveShouldEmitProgressThenCompleteInOrder() {
        // Given: 一个发射 1 个 progress + 1 个 complete 的 StreamingTool
        StreamingTool tool = createTool("test_stream", (args, emitter) -> {
            emitter.next(ToolResultChunk.progress("test_stream", "step 1", 1, 2));
            emitter.next(ToolResultChunk.complete("test_stream", ToolResult.success("done")));
            emitter.complete();
        });

        // When & Then: 验证 chunk 顺序和 payload
        StepVerifier.create(tool.executeReactive(Collections.emptyMap()))
            .assertNext(chunk -> {
                assertEquals(ChunkType.PROGRESS, chunk.getType());
                assertEquals("test_stream", chunk.getToolName());
                assertEquals("step 1", chunk.getMessage());
                assertEquals(1.0, chunk.getProgress());
                assertEquals(2.0, chunk.getTotal());
            })
            .assertNext(chunk -> {
                assertEquals(ChunkType.COMPLETE, chunk.getType());
                assertEquals("test_stream", chunk.getToolName());
                assertNotNull(chunk.getResult(), "COMPLETE chunk 必须携带 ToolResult");
                assertFalse(chunk.getResult().isError());
                assertEquals("done", chunk.getResult().getContent());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("多个 PROGRESS chunk 后接 COMPLETE — 全部按序发射")
    void executeReactiveShouldEmitMultipleProgressChunksFollowedByComplete() {
        // Given: 发射 3 个 progress + 1 个 complete
        StreamingTool tool = createTool("multi_progress", (args, emitter) -> {
            emitter.next(ToolResultChunk.progress("multi_progress", "downloading", 1, 3));
            emitter.next(ToolResultChunk.progress("multi_progress", "parsing", 2, 3));
            emitter.next(ToolResultChunk.progress("multi_progress", "indexing", 3, 3));
            emitter.next(ToolResultChunk.complete("multi_progress", ToolResult.success("indexed 100 files")));
            emitter.complete();
        });

        // When: 收集所有 chunk
        List<ToolResultChunk> chunks = tool.executeReactive(Collections.emptyMap())
            .collectList()
            .block();

        // Then: 验证数量、顺序和 payload
        assertNotNull(chunks);
        assertEquals(4, chunks.size());

        // 前 3 个都是 PROGRESS
        for (int i = 0; i < 3; i++) {
            assertEquals(ChunkType.PROGRESS, chunks.get(i).getType(),
                "chunk[" + i + "] 应为 PROGRESS");
        }
        assertEquals("downloading", chunks.get(0).getMessage());
        assertEquals("parsing", chunks.get(1).getMessage());
        assertEquals("indexing", chunks.get(2).getMessage());

        // 最后是 COMPLETE
        ToolResultChunk last = chunks.get(3);
        assertEquals(ChunkType.COMPLETE, last.getType());
        assertEquals("indexed 100 files", last.getResult().getContent());
        assertFalse(last.getResult().isError());
    }

    @Test
    @DisplayName("executeReactive 中 executeStreaming 抛异常 — 自动捕获并发射 ERROR chunk")
    void executeReactiveShouldCatchExceptionAndEmitErrorChunk() {
        // Given: executeStreaming 直接抛出 RuntimeException
        StreamingTool tool = createTool("error_tool", (args, emitter) -> {
            throw new RuntimeException("connection refused");
        });

        // When & Then: 应收到 1 个 ERROR chunk，stream 正常结束
        StepVerifier.create(tool.executeReactive(Collections.emptyMap()))
            .assertNext(chunk -> {
                assertEquals(ChunkType.ERROR, chunk.getType());
                assertEquals("error_tool", chunk.getToolName());
                assertEquals("connection refused", chunk.getMessage());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("executeReactive 中抛出 checked exception — 同样被捕获为 ERROR chunk")
    void executeReactiveShouldCatchCheckedExceptionAsErrorChunk() {
        // Given: executeStreaming 中途抛异常（不是在开始时）
        StreamingTool tool = createTool("mid_error", (args, emitter) -> {
            emitter.next(ToolResultChunk.progress("mid_error", "starting", 0, 1));
            throw new IllegalStateException("out of memory");
        });

        // When & Then: 应收到 PROGRESS + ERROR
        StepVerifier.create(tool.executeReactive(Collections.emptyMap()))
            .assertNext(chunk -> {
                assertEquals(ChunkType.PROGRESS, chunk.getType());
                assertEquals("starting", chunk.getMessage());
            })
            .assertNext(chunk -> {
                assertEquals(ChunkType.ERROR, chunk.getType());
                assertEquals("out of memory", chunk.getMessage());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("executeReactive 只发射 ERROR chunk — stream 正常结束")
    void executeReactiveShouldHandleOnlyErrorChunk() {
        // Given: 工具直接发射 ERROR
        StreamingTool tool = createTool("fail_fast", (args, emitter) -> {
            emitter.next(ToolResultChunk.error("fail_fast", "permission denied"));
            emitter.complete();
        });

        // When & Then
        StepVerifier.create(tool.executeReactive(Collections.emptyMap()))
            .assertNext(chunk -> {
                assertEquals(ChunkType.ERROR, chunk.getType());
                assertEquals("fail_fast", chunk.getToolName());
                assertEquals("permission denied", chunk.getMessage());
            })
            .verifyComplete();
    }

    // ==================== 同步 execute() 回退路径测试 ====================

    @Test
    @DisplayName("execute 同步调用 — 返回 COMPLETE chunk 的 ToolResult payload")
    void executeShouldReturnToolResultFromCompleteChunk() {
        // Given: 发射 progress + complete
        StreamingTool tool = createTool("sync_test", (args, emitter) -> {
            emitter.next(ToolResultChunk.progress("sync_test", "working", 1, 1));
            emitter.next(ToolResultChunk.complete("sync_test", ToolResult.success("final answer")));
            emitter.complete();
        });

        // When
        ToolResult result = tool.execute(Collections.emptyMap());

        // Then: 验证 payload 不只是 non-null，而是实际内容
        assertNotNull(result, "execute() 不应返回 null");
        assertFalse(result.isError(), "成功路径不应标记为 error");
        assertEquals("final answer", result.getContent(),
            "execute() 必须返回 COMPLETE chunk 中的实际 payload");
    }

    @Test
    @DisplayName("execute 忽略 PROGRESS chunk，仅从 COMPLETE chunk 提取结果")
    void executeShouldIgnoreProgressChunksAndReturnComplete() {
        // Given: 发射多个 PROGRESS 后跟 COMPLETE
        StreamingTool tool = createTool("filter_test", (args, emitter) -> {
            emitter.next(ToolResultChunk.progress("filter_test", "step 1", 1, 5));
            emitter.next(ToolResultChunk.progress("filter_test", "step 2", 2, 5));
            emitter.next(ToolResultChunk.progress("filter_test", "step 3", 3, 5));
            emitter.next(ToolResultChunk.progress("filter_test", "step 4", 4, 5));
            emitter.next(ToolResultChunk.progress("filter_test", "step 5", 5, 5));
            emitter.next(ToolResultChunk.complete("filter_test", ToolResult.success("all steps done")));
            emitter.complete();
        });

        // When
        ToolResult result = tool.execute(Collections.emptyMap());

        // Then: 只返回 COMPLETE 的 payload，PROGRESS 被过滤掉
        assertFalse(result.isError());
        assertEquals("all steps done", result.getContent(),
            "execute() 必须跳过所有 PROGRESS chunk，返回 COMPLETE payload");
    }

    @Test
    @DisplayName("execute 遇到 ERROR chunk — 返回 error ToolResult")
    void executeShouldReturnErrorToolResultFromErrorChunk() {
        // Given: 只发射 ERROR chunk
        StreamingTool tool = createTool("error_sync", (args, emitter) -> {
            emitter.next(ToolResultChunk.error("error_sync", "disk full"));
            emitter.complete();
        });

        // When
        ToolResult result = tool.execute(Collections.emptyMap());

        // Then: 必须是 error 结果且消息正确
        assertNotNull(result);
        assertTrue(result.isError(), "ERROR chunk 应转为 error ToolResult");
        assertEquals("disk full", result.getContent(),
            "error 消息必须传递到 ToolResult.content");
    }

    @Test
    @DisplayName("execute 中 executeStreaming 抛异常 — 返回 error ToolResult")
    void executeShouldReturnErrorToolResultWhenExceptionThrown() {
        // Given: executeStreaming 抛异常
        StreamingTool tool = createTool("exception_sync", (args, emitter) -> {
            throw new RuntimeException("segfault");
        });

        // When
        ToolResult result = tool.execute(Collections.emptyMap());

        // Then
        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals("segfault", result.getContent());
    }

    @Test
    @DisplayName("execute 无任何 chunk 发射 — 返回 fallback error ToolResult")
    void executeShouldReturnFallbackErrorWhenNoChunkEmitted() {
        // Given: executeStreaming 直接 complete，不发射任何 chunk
        StreamingTool tool = createTool("empty_stream", (args, emitter) -> {
            emitter.complete();
        });

        // When
        ToolResult result = tool.execute(Collections.emptyMap());

        // Then: 无 COMPLETE/ERROR chunk → blockFirst() 返回 null → fallback error
        assertNotNull(result);
        assertTrue(result.isError(), "无 chunk 时应返回 error");
        assertEquals("No result from streaming tool", result.getContent());
    }

    @Test
    @DisplayName("execute 只有 PROGRESS chunk 无 COMPLETE — 返回 fallback error ToolResult")
    void executeShouldReturnFallbackErrorWhenOnlyProgressChunks() {
        // Given: 只发射 PROGRESS，不发射 COMPLETE 或 ERROR
        StreamingTool tool = createTool("progress_only", (args, emitter) -> {
            emitter.next(ToolResultChunk.progress("progress_only", "working", 1, 2));
            emitter.next(ToolResultChunk.progress("progress_only", "still working", 2, 2));
            emitter.complete();
        });

        // When
        ToolResult result = tool.execute(Collections.emptyMap());

        // Then: filter 排除了所有 PROGRESS → blockFirst() 返回 null → fallback
        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals("No result from streaming tool", result.getContent());
    }

    @Test
    @DisplayName("execute 中 ERROR chunk 无消息 — 返回默认错误文案")
    void executeShouldReturnDefaultErrorMessageWhenErrorChunkHasNullMessage() {
        // Given: ERROR chunk 的 message 为 null
        // ToolResultChunk.error() 总是传入 message，但我们可以通过异常的 getMessage() 为 null 来触发
        StreamingTool tool = createTool("null_msg", (args, emitter) -> {
            // 抛出一个 getMessage() 返回 null 的异常
            throw new RuntimeException((String) null);
        });

        // When: 异常被捕获 → ToolResultChunk.error(name, null)
        // execute() 中 chunk.getMessage() == null → "Tool execution failed"
        ToolResult result = tool.execute(Collections.emptyMap());

        // Then
        assertNotNull(result);
        assertTrue(result.isError());
        assertEquals("Tool execution failed", result.getContent(),
            "当 ERROR chunk 的 message 为 null 时，应返回默认错误信息");
    }

    // ==================== 参数传递测试 ====================

    @Test
    @DisplayName("executeReactive 将参数正确传递给 executeStreaming")
    void executeReactiveShouldPassArgsToExecuteStreaming() {
        // Given: 工具使用 args 中的值构建结果
        StreamingTool tool = createTool("args_test", (args, emitter) -> {
            String city = (String) args.get("city");
            emitter.next(ToolResultChunk.complete("args_test",
                ToolResult.success("Weather in " + city + ": sunny")));
            emitter.complete();
        });

        Map<String, Object> args = Map.of("city", "Beijing");

        // When
        ToolResult result = tool.execute(args);

        // Then: 验证 args 被正确传入并使用
        assertFalse(result.isError());
        assertEquals("Weather in Beijing: sunny", result.getContent(),
            "参数必须从 execute()/executeReactive() 传递到 executeStreaming()");
    }

    // ==================== Tool 接口合规测试 ====================

    @Test
    @DisplayName("StreamingTool 实现 Tool 接口 — getName/getDescription/getSchema 正常工作")
    void streamingToolShouldImplementToolInterface() {
        // Given
        StreamingTool tool = createTool("my_tool", (args, emitter) -> {
            emitter.next(ToolResultChunk.complete("my_tool", ToolResult.success("ok")));
            emitter.complete();
        });

        // Then: 验证 Tool 接口方法
        assertEquals("my_tool", tool.getName());
        assertEquals("test tool", tool.getDescription());
        assertNotNull(tool.getSchema());
        assertEquals("object", tool.getSchema().getType());

        // 验证 instanceof
        assertTrue(tool instanceof Tool, "StreamingTool 必须是 Tool 的实例");
    }

    @Test
    @DisplayName("execute 优先返回第一个 COMPLETE/ERROR chunk — 忽略后续 chunk")
    void executeShouldReturnFirstCompleteOrErrorChunk() {
        // Given: ERROR 先于 COMPLETE 发射
        StreamingTool tool = createTool("error_first", (args, emitter) -> {
            emitter.next(ToolResultChunk.error("error_first", "first error"));
            emitter.next(ToolResultChunk.complete("error_first", ToolResult.success("late success")));
            emitter.complete();
        });

        // When: blockFirst() 拿到第一个匹配的 chunk
        ToolResult result = tool.execute(Collections.emptyMap());

        // Then: 应返回 ERROR（因为它先发射）
        assertTrue(result.isError());
        assertEquals("first error", result.getContent(),
            "execute() 使用 blockFirst()，应返回第一个 COMPLETE/ERROR chunk");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建一个可配置行为的 StreamingTool 测试子类
     */
    private StreamingTool createTool(String name,
                                     BiConsumer<Map<String, Object>, FluxSink<ToolResultChunk>> behavior) {
        return new StreamingTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return "test tool";
            }

            @Override
            public ToolSchema getSchema() {
                return ToolSchema.empty();
            }

            @Override
            protected void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter) {
                behavior.accept(args, emitter);
            }
        };
    }
}
