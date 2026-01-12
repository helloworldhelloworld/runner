package com.lightweightai.web.service;

import com.lightweightai.kernel.agent.SoulComfortAgent;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.memory.UserMemory;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 心灵引导聊天服务 - 使用SoulComfortAgent
 *
 * 特性：
 * - 具备记忆能力（记住用户的对话历史）
 * - 具备反思能力（分析情绪、识别话题）
 * - 提供温暖的心灵引导
 */
@Service
public class SoulComfortChatService {

    private static final Logger logger = LoggerFactory.getLogger(SoulComfortChatService.class);

    private final SoulComfortAgent agent;

    public SoulComfortChatService(LLMProvider llmProvider) {
        this.agent = new SoulComfortAgent(llmProvider);
        logger.info("SoulComfortChatService initialized with memory and reflection capabilities");
    }

    /**
     * 处理聊天请求（带记忆）
     */
    public ChatResponse chat(ChatRequest request) {
        try {
            String sessionId = request.getSessionId() != null ? request.getSessionId() : "default";
            logger.info("Processing soul comfort chat for session: {}", sessionId);

            // 使用心灵引导Agent处理消息
            String response = agent.chat(sessionId, request.getMessage());

            // 构建响应
            ChatResponse chatResponse = new ChatResponse();
            chatResponse.setResponse(response);
            chatResponse.setSkillsApplied(List.of("soul-comfort"));

            // 添加元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("mode", "soul-comfort");
            metadata.put("hasMemory", true);
            metadata.put("hasReflection", true);

            // 获取用户记忆信息
            UserMemory userMemory = agent.getUserMemory(sessionId);
            List<UserMemory.EmotionRecord> recentEmotions = userMemory.getRecentEmotions(3);
            if (!recentEmotions.isEmpty()) {
                metadata.put("recentEmotions", recentEmotions.stream()
                    .map(UserMemory.EmotionRecord::getEmotion)
                    .collect(Collectors.toList()));
            }

            List<String> topics = userMemory.getImportantTopics();
            if (!topics.isEmpty()) {
                metadata.put("importantTopics", topics);
            }

            chatResponse.setMetadata(metadata);

            logger.info("Soul comfort chat completed successfully");
            return chatResponse;

        } catch (Exception e) {
            logger.error("Soul comfort chat failed", e);
            return ChatResponse.error(e.getMessage());
        }
    }

    /**
     * 获取会话摘要
     */
    public Map<String, Object> getSessionSummary(String sessionId) {
        Map<String, Object> summary = new HashMap<>();

        try {
            // 获取对话摘要
            String conversationSummary = agent.getSessionSummary(sessionId);
            summary.put("summary", conversationSummary);

            // 获取用户记忆
            UserMemory userMemory = agent.getUserMemory(sessionId);

            // 最近的情绪
            List<UserMemory.EmotionRecord> emotions = userMemory.getRecentEmotions(5);
            summary.put("recentEmotions", emotions.stream()
                .map(e -> Map.of(
                    "emotion", e.getEmotion(),
                    "context", e.getContext(),
                    "timestamp", e.getTimestamp().toString()
                ))
                .collect(Collectors.toList()));

            // 重要话题
            summary.put("importantTopics", userMemory.getImportantTopics());

            // 基本信息
            summary.put("basicInfo", userMemory.getAllBasicInfo());

            // 互动信息
            summary.put("firstMeet", userMemory.getFirstMeetTime().toString());
            summary.put("lastInteraction", userMemory.getLastInteractionTime().toString());

        } catch (Exception e) {
            logger.error("Failed to get session summary", e);
            summary.put("error", e.getMessage());
        }

        return summary;
    }

    /**
     * 清空会话
     */
    public void clearSession(String sessionId) {
        agent.clearSession(sessionId);
        logger.info("Cleared session: {}", sessionId);
    }

    /**
     * 获取Agent实例（用于高级功能）
     */
    public SoulComfortAgent getAgent() {
        return agent;
    }
}
