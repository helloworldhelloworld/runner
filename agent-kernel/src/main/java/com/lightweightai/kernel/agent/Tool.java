package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;

import java.util.Map;

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
     * 是否为 AI 自主调用（默认 true）
     *
     * 如果为 false，需要用户确认后才能执行
     */
    default boolean isAutoExecute() {
        return true;
    }
}
