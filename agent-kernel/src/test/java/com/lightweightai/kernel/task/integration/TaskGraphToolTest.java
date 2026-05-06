package com.lightweightai.kernel.task.integration;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.task.core.AbstractTask;
import com.lightweightai.kernel.task.core.Task;
import com.lightweightai.kernel.task.core.TaskContext;
import com.lightweightai.kernel.task.core.TaskResult;
import com.lightweightai.kernel.task.graph.TaskGraph;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskGraphToolTest {

    private static Task echo(String name) {
        return new AbstractTask(name) {
            @Override
            protected Mono<TaskResult> doExecute(TaskContext ctx) {
                Object input = ctx.getAttribute("input");
                return Mono.just(TaskResult.success(name + ":" + input));
            }
        };
    }

    private static Task fail(String name, String err) {
        return new AbstractTask(name) {
            @Override
            protected Mono<TaskResult> doExecute(TaskContext ctx) {
                return Mono.error(new IllegalStateException(err));
            }
        };
    }

    @Test
    void argsForwardedToContextAttributes() {
        TaskGraph g = TaskGraph.builder().addTask(echo("a")).build();
        TaskGraphTool tool = new TaskGraphTool("pipeline", "demo",
            TaskGraphTool.defaultSchema(), g);

        ToolResult r = tool.execute(Map.of("input", "hello"));
        assertFalse(r.isError());
        assertTrue(r.getContent().contains("a:hello"),
            "args.input must reach Task via ctx.getAttribute, got: " + r.getContent());
    }

    @Test
    void streamEmitsProgressForEachTask() {
        TaskGraph g = TaskGraph.builder()
            .addTask(echo("a"))
            .addTask(echo("b"), "a")
            .build();
        TaskGraphTool tool = new TaskGraphTool("p", "d", null, g);

        List<ToolResultChunk> chunks = tool.executeReactive(Map.of("input", "x"))
            .collectList().block();

        // 4 progress (start+complete for a,b) + 1 complete
        assertNotNull(chunks);
        long progressCount = chunks.stream()
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.PROGRESS)
            .count();
        assertTrue(progressCount >= 4, "expected ≥4 progress chunks, got " + progressCount);

        ToolResultChunk last = chunks.get(chunks.size() - 1);
        assertEquals(ToolResultChunk.ChunkType.COMPLETE, last.getType());
        // payload assertion: aggregated content includes both task contents
        assertTrue(last.getResult().getContent().contains("a:x"));
        assertTrue(last.getResult().getContent().contains("b:x"));
    }

    @Test
    void taskErrorFlowsToToolErrorWithStackHead() {
        TaskGraph g = TaskGraph.builder().addTask(fail("bad", "kaboom")).build();
        TaskGraphTool tool = new TaskGraphTool("p", "d", null, g);

        ToolResult r = tool.execute(Map.of("input", "x"));
        assertTrue(r.isError());
        assertTrue(r.getContent().contains("kaboom"));
        assertTrue(r.getContent().contains("IllegalStateException"),
            "error must preserve exception type, got: " + r.getContent());
    }

    /**
     * Producer → consumer chain: TaskGraphTool 注册到 ToolRegistry 后
     * {@code getToolDefinitions()} 必须包含其 schema —— 防止 PR #109 评论中
     * 提到的 toolDefinitions 历史 bug 重演（CLAUDE.md UT Rule #5）。
     */
    @Test
    void toolDefinitionsChainCarriesSchema() {
        TaskGraph g = TaskGraph.builder().addTask(echo("a")).build();
        TaskGraphTool tool = new TaskGraphTool("my_pipeline", "demo",
            TaskGraphTool.defaultSchema(), g);

        ToolRegistry registry = new ToolRegistry();
        registry.register(tool);

        var defs = registry.getToolDefinitions();
        assertEquals(1, defs.size());
        Map<String, Object> def = defs.get(0);
        assertEquals("my_pipeline", def.get("name"));
        assertEquals("demo", def.get("description"));

        @SuppressWarnings("unchecked")
        Map<String, Object> inputSchema = (Map<String, Object>) def.get("input_schema");
        assertNotNull(inputSchema);
        assertEquals("object", inputSchema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) inputSchema.get("properties");
        assertTrue(props.containsKey("input"),
            "schema must carry 'input' property end-to-end, got: " + props);
    }

    @Test
    void worksWithEmptyArgs() {
        TaskGraph g = TaskGraph.builder().addTask(echo("a")).build();
        TaskGraphTool tool = new TaskGraphTool("p", "d", ToolSchema.empty(), g);
        ToolResult r = tool.execute(Map.of());
        assertFalse(r.isError());
    }
}
