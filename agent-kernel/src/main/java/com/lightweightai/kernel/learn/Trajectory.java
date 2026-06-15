package com.lightweightai.kernel.learn;

import com.lightweightai.kernel.llm.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * Agent 一次完整运行的轨迹 — 由 {@link SkillGeneratorObserver} 边走边记。
 *
 * 字段语义:
 * - {@code userInput}: 用户原始消息(从 {@code onAgentStart})
 * - {@code sessionId}: 会话标识
 * - {@code steps}: 工具调用序列(顺序由 {@code onPostToolUse} 触发顺序决定)
 * - {@code finalText}: agent 最终回复(从 {@code AgentResponse.text})
 * - {@code stopReason}: agent 停止原因(end_turn / max_iterations / tool_use 等)
 * - {@code errored}: 中途是否触发过 {@code onError}
 *
 * 不可变。每个 step 携带原始 {@code Map<String, Object>} args 与 {@code ToolResult},
 * extractor 可以零反序列化地决策。
 */
public record Trajectory(
        String userInput,
        String sessionId,
        List<Step> steps,
        String finalText,
        String stopReason,
        boolean errored
) {
    public Trajectory {
        steps = steps != null ? List.copyOf(steps) : List.of();
    }

    /**
     * 一次工具调用。
     */
    public record Step(String toolName, Map<String, Object> arguments, ToolResult result) {
        public Step {
            arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        }

        public boolean isError() {
            return result == null || result.isError();
        }
    }
}
