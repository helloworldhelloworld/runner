package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import java.util.Map;
import reactor.core.publisher.Flux;

/**
 * 统一的工具接口
 *
 * 替代 Skill/Instruction/Plugin 三层概念，提供简洁的工具抽象。
 */
public interface Tool {

    /**
     * 工具名称（唯一标识）
     */
    String getName();

    /**
     * 工具描述（供 LLM 理解）
     */
    String getDescription();

    /**
     * 工具参数 Schema（JSON Schema 格式）
     */
    ToolSchema getSchema();

    /**
     * 执行工具
     *
     * @param args 参数（从 LLM 的 tool_use 解析）
     * @return 执行结果
     */
    ToolResult execute(Map<String, Object> args);

    /**
     * Reactive 流式执行
     *
     * 默认实现将同步 execute() 包装为单个 COMPLETE 事件。
     * MCP 工具可 override 此方法来实现真正的流式（进度+日志+结果）。
     *
     * @param args 参数
     * @return 工具结果事件流
     */
    default Flux<ToolResultChunk> executeReactive(Map<String, Object> args) {
        return Flux.defer(() -> {
            try {
                ToolResult result = execute(args);
                return Flux.just(ToolResultChunk.complete(getName(), result));
            } catch (Exception e) {
                return Flux.just(ToolResultChunk.error(getName(), e.getMessage()));
            }
        });
    }

    /**
     * 是否为 AI 自主调用（默认 true）
     *
     * 如果为 false，需要用户确认后才能执行
     */
    default boolean isAutoExecute() {
        return true;
    }
}
