package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.RiskLevel;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.memory.tools.MemoryToolkit;
import com.lightweightai.kernel.memory.tools.WriteMemoryTool;

import java.util.List;
import java.util.Map;

/**
 * 把 kernel-memory 的 {@link WriteMemoryTool} 适配成内核 {@link Tool}，注册进全局 ToolRegistry，
 * 让 agent（如 minion）的 LLM 能**主动调用** {@code write_memory} 把用户信息（名字、喜好、约定）
 * 写入长期记忆——之前这些记忆工具只挂在 WS 消息上、LLM 调不到，所以"名字记不住"。
 *
 * 风险 {@link RiskLevel#WRITE}（≤ SYSTEM，过 minion 的 {@code byRiskLevel(SYSTEM)} 闸）。
 * 写进 durable 后，AgentLoop 每轮的 {@code memoryProvider.search(input)} 会把它捞回上文 → 跨会话记得。
 */
class MemoryWriteTool implements Tool {

    private final WriteMemoryTool delegate;

    MemoryWriteTool(MemoryToolkit toolkit) {
        this.delegate = toolkit.getWriteTool();
    }

    @Override
    public String getName() {
        return WriteMemoryTool.TOOL_NAME; // "write_memory"
    }

    @Override
    public String getDescription() {
        return "记住用户的重要信息（名字、喜好、约定等）以便以后用。"
            + "长期信息用 type=durable（跨会话持久），临时用 ephemeral。";
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.withRequired(Map.of(
            "content", Map.of("type", "string", "description", "要记住的内容"),
            "type", Map.of(
                "type", "string",
                "enum", List.of("durable", "ephemeral"),
                "description", "durable=长期跨会话, ephemeral=当日临时"),
            "section", Map.of("type", "string", "description", "长期记忆分区名，如 用户信息")
        ), "content");
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        WriteMemoryTool.WriteMemoryResult r = delegate.execute(args);
        return r.success() ? ToolResult.success(r.message()) : ToolResult.error(r.error());
    }

    @Override
    public RiskLevel riskLevel() {
        return RiskLevel.WRITE;
    }
}
