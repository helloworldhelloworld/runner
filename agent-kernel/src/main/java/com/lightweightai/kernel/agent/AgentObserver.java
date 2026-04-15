package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.PromptContext;

import java.util.List;
import java.util.Map;

/**
 * Agent 观察者接口（可观测性）
 *
 * 用于监控 AgentLoop 的执行过程：
 * - Prompt 构建
 * - LLM 请求/响应
 * - 工具调用
 *
 * 实现此接口可以：
 * - 调试和日志记录
 * - 性能监控
 * - 成本追踪
 * - 安全审计
 */
public interface AgentObserver {

    /**
     * Prompt 构建完成时调用
     *
     * @param context 构建好的 Prompt 上下文
     */
    default void onPromptBuilt(PromptContext context) {}

    /**
     * LLM 请求发送前调用
     *
     * @param messages 发送给 LLM 的消息列表
     */
    default void onLLMRequest(List<ConversationMessage> messages) {}

    /**
     * LLM 响应接收后调用
     *
     * @param response LLM 的响应
     */
    default void onLLMResponse(LLMResponse response) {}

    /**
     * 工具调用完成时调用
     *
     * @param toolName 工具名称
     * @param args 工具参数
     * @param result 工具执行结果
     */
    default void onToolCall(String toolName, Map<String, Object> args, ToolResult result) {}

    /**
     * 工具调用前拦截点
     *
     * 可用于：安全审计、参数校验、日志记录
     */
    default void onPreToolUse(String toolName, Map<String, Object> args) {}

    /**
     * 工具调用后拦截点
     *
     * 可用于：结果过滤、成本追踪、性能监控
     */
    default void onPostToolUse(String toolName, Map<String, Object> args, ToolResult result) {}

    /**
     * Agent 循环开始时调用
     *
     * @param input 用户输入
     * @param sessionId 会话 ID
     */
    default void onAgentStart(String input, String sessionId) {}

    /**
     * Agent 循环结束时调用
     *
     * @param response Agent 响应
     */
    default void onAgentComplete(AgentResponse response) {}

    /**
     * 发生错误时调用
     *
     * @param error 错误信息
     */
    default void onError(Throwable error) {}
}
