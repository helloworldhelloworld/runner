package com.lightweightai.web.observer;

import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.prompt.PromptContext;
import org.slf4j.Logger;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;

/**
 * 调试观察器 - 收集 LLM 调用信息用于前端展示
 *
 * 每个请求创建一个实例，收集该请求的所有调试信息
 */
public class DebugAgentObserver implements AgentObserver {

    private static final Logger logger = LoggerFactory.getLogger("LLM-DEBUG");

    private final DebugInfo debugInfo;
    private long toolCallStartTime;

    public DebugAgentObserver() {
        this.debugInfo = new DebugInfo();
    }

    @Override
    public void onAgentStart(String input, String sessionId) {
        debugInfo.setStartTime(System.currentTimeMillis());
        debugInfo.setSessionId(sessionId);
        debugInfo.setUserInput(input);

        logger.debug("=== Agent Start: session={}, input={} ===", sessionId, truncate(input, 100));
    }

    @Override
    public void onPromptBuilt(PromptContext context) {
        debugInfo.setActiveSkills(context.getActiveSkillNames());

        String systemPrompt = context.getSystemPrompt();
        if (systemPrompt != null) {
            debugInfo.setSystemPromptPreview(truncate(systemPrompt, 500));
        }

        logger.debug("Prompt built: skills={}, tools={}", context.getActiveSkillNames(), context.getTools().size());
    }

    @Override
    public void onLLMRequest(List<ConversationMessage> messages) {
        for (int i = 0; i < messages.size(); i++) {
            ConversationMessage msg = messages.get(i);
            debugInfo.addRequestMessage(i, msg.getRole().toString(), msg.getTextContent());
        }

        logger.debug("LLM Request: {} messages", messages.size());
        for (int i = 0; i < messages.size(); i++) {
            ConversationMessage msg = messages.get(i);
            logger.debug("  [{}] {}: {}", i, msg.getRole(), truncate(msg.getTextContent(), 100));
        }
    }

    @Override
    public void onLLMResponse(LLMResponse response) {
        if (response.getMessage() != null) {
            debugInfo.setResponseText(response.getMessage().getTextContent());
        }
        debugInfo.setStopReason(response.getStopReason());

        if (response.getUsage() != null) {
            debugInfo.setResponseTokens(response.getUsage().getOutputTokens());
            debugInfo.setTotalRequestTokens(response.getUsage().getInputTokens());
        }

        logger.debug("LLM Response: stopReason={}, responseLen={}",
            response.getStopReason(),
            response.getMessage() != null ? response.getMessage().getTextContent().length() : 0);
    }

    @Override
    public void onToolCall(String toolName, Map<String, Object> args, ToolResult result) {
        long duration = System.currentTimeMillis() - toolCallStartTime;
        debugInfo.addToolCall(toolName, args, result.getContent(), duration);

        logger.debug("Tool Call: {} -> {} ({}ms)", toolName, truncate(result.getContent(), 100), duration);
    }

    /**
     * 标记工具调用开始（用于计算耗时）
     */
    public void onToolCallStart(String toolName) {
        toolCallStartTime = System.currentTimeMillis();
    }

    @Override
    public void onAgentComplete(AgentResponse response) {
        debugInfo.setEndTime(System.currentTimeMillis());

        logger.debug("=== Agent Complete: {}ms, response={} chars ===",
            debugInfo.getDuration(), response.getText().length());
    }

    @Override
    public void onError(Throwable error) {
        debugInfo.setEndTime(System.currentTimeMillis());
        debugInfo.setError(error.getMessage());

        logger.error("Agent Error: {}", error.getMessage());
    }

    /**
     * 获取收集到的调试信息
     */
    public DebugInfo getDebugInfo() {
        return debugInfo;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "(null)";
        }
        text = text.replace("\n", " ").trim();
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
