package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Map;

/**
 * 支持真实流式输出的 Tool 基类
 *
 * 解决 Tool.executeReactive() 默认实现将同步 execute() 包装为单个 COMPLETE chunk 的"假流式"问题。
 *
 * 子类只需实现 executeStreaming(args, emitter)，通过 emitter 按块推送进度/结果。
 *
 * 适用场景：文件读取、HTTP 请求、Shell 命令、长时间运算等。
 *
 * 用法示例：
 * <pre>
 * public class ShellTool extends StreamingTool {
 *     protected void executeStreaming(Map&lt;String, Object&gt; args, FluxSink&lt;ToolResultChunk&gt; emitter) {
 *         Process p = Runtime.getRuntime().exec(cmd);
 *         BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
 *         String line;
 *         while ((line = reader.readLine()) != null) {
 *             emitter.next(ToolResultChunk.progress(getName(), 0, line));
 *         }
 *         emitter.next(ToolResultChunk.complete(getName(), ToolResult.success("done")));
 *         emitter.complete();
 *     }
 * }
 * </pre>
 */
public abstract class StreamingTool implements Tool {

    /**
     * 同步执行 — 收集流式结果的最终输出
     *
     * 子类通常不需 override 此方法；如需纯同步路径可 override。
     */
    @Override
    public ToolResult execute(Map<String, Object> args) {
        ToolResultChunk chunk = executeReactive(args)
            .filter(c -> c.getType() == ToolResultChunk.ChunkType.COMPLETE
                      || c.getType() == ToolResultChunk.ChunkType.ERROR)
            .blockFirst();
        if (chunk == null) {
            return ToolResult.error("No result from streaming tool");
        }
        if (chunk.getType() == ToolResultChunk.ChunkType.ERROR) {
            return ToolResult.error(chunk.getMessage() != null ? chunk.getMessage() : "Tool execution failed");
        }
        return chunk.getResult();
    }

    /**
     * 真正的流式执行 — 通过 Flux.create 驱动
     */
    @Override
    public Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
        return Flux.create(emitter -> {
            try {
                executeStreaming(args, emitter);
            } catch (Exception e) {
                emitter.next(ToolResultChunk.error(getName(), e.getMessage()));
                emitter.complete();
            }
        });
    }

    /**
     * 子类实现此方法进行流式输出
     *
     * 通过 emitter 推送 ToolResultChunk：
     * - progress(name, percent, message): 进度
     * - complete(name, result): 最终结果
     * - error(name, message): 错误
     *
     * 最后必须调用 emitter.complete()。
     */
    protected abstract void executeStreaming(Map<String, Object> args, FluxSink<ToolResultChunk> emitter);
}
