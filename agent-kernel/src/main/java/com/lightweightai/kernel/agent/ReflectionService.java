package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.memory.UserMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * 反思服务 - 提供深度思考和情绪分析能力
 */
public class ReflectionService {

    private final LLMProvider llmProvider;

    public ReflectionService(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
    }

    /**
     * 分析用户情绪
     */
    public String analyzeEmotion(String userMessage) {
        System.out.println("[ReflectionService] analyzeEmotion called");
        List<ConversationMessage> messages = new ArrayList<>();

        // 系统提示
        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.SYSTEM)
            .textContent("你是一位专业的心理分析师。请分析用户消息中的情绪状态，用一个词概括（如：焦虑、悲伤、快乐、困惑、愤怒、平静等）。只返回情绪词，不要其他解释。")
            .build());

        // 用户消息
        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(userMessage)
            .build());

        try {
            System.out.println("[ReflectionService] Building LLM options...");
            LLMOptions options = LLMOptions.builder()
                .maxTokens(50)
                .temperature(0.3)
                .build();

            System.out.println("[ReflectionService] Calling llmProvider.complete()...");
            LLMResponse response = llmProvider.complete(messages, options);
            System.out.println("[ReflectionService] Got LLM response");
            return response.getMessage().getTextContent().trim();
        } catch (Exception e) {
            System.out.println("[ReflectionService] Error: " + e.getMessage());
            e.printStackTrace();
            return "未知";
        }
    }

    /**
     * 提取对话要点
     */
    public String extractKeyPoints(List<ConversationMessage> recentMessages) {
        if (recentMessages.isEmpty()) {
            return "";
        }

        List<ConversationMessage> messages = new ArrayList<>();

        // 系统提示
        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.SYSTEM)
            .textContent("请总结以下对话的核心要点，包括：1)用户主要关心的问题 2)讨论的关键话题 3)用户的情感状态变化。用简洁的语言概括，不超过100字。")
            .build());

        // 添加最近的对话
        StringBuilder conversationText = new StringBuilder();
        for (ConversationMessage msg : recentMessages) {
            conversationText.append(msg.getRole()).append(": ")
                .append(msg.getTextContent()).append("\n");
        }

        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(conversationText.toString())
            .build());

        try {
            LLMOptions options = LLMOptions.builder()
                .maxTokens(200)
                .temperature(0.5)
                .build();

            LLMResponse response = llmProvider.complete(messages, options);
            return response.getMessage().getTextContent();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 生成反思性回应的建议
     */
    public String generateReflectiveGuidance(String userMessage, UserMemory userMemory) {
        List<ConversationMessage> messages = new ArrayList<>();

        // 系统提示
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("你是一位富有同理心的心灵引导者。");
        systemPrompt.append("基于用户的历史情绪和话题，提供简短的反思性回应建议（不超过50字）。");

        // 添加用户记忆上下文
        if (userMemory != null) {
            List<UserMemory.EmotionRecord> recentEmotions = userMemory.getRecentEmotions(3);
            if (!recentEmotions.isEmpty()) {
                systemPrompt.append("\n用户最近的情绪状态：");
                for (UserMemory.EmotionRecord record : recentEmotions) {
                    systemPrompt.append(record.getEmotion()).append("、");
                }
            }

            List<String> topics = userMemory.getImportantTopics();
            if (!topics.isEmpty()) {
                systemPrompt.append("\n用户关注的话题：").append(String.join("、", topics));
            }
        }

        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.SYSTEM)
            .textContent(systemPrompt.toString())
            .build());

        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(userMessage)
            .build());

        try {
            LLMOptions options = LLMOptions.builder()
                .maxTokens(100)
                .temperature(0.7)
                .build();

            LLMResponse response = llmProvider.complete(messages, options);
            return response.getMessage().getTextContent();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 识别重要话题
     */
    public List<String> identifyImportantTopics(String conversation) {
        List<ConversationMessage> messages = new ArrayList<>();

        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.SYSTEM)
            .textContent("从对话中提取3个最重要的话题关键词，用逗号分隔，不要其他解释。")
            .build());

        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(conversation)
            .build());

        try {
            LLMOptions options = LLMOptions.builder()
                .maxTokens(50)
                .temperature(0.3)
                .build();

            LLMResponse response = llmProvider.complete(messages, options);
            String result = response.getMessage().getTextContent();

            // 解析逗号分隔的话题
            String[] topics = result.split("[,，、]");
            List<String> topicList = new ArrayList<>();
            for (String topic : topics) {
                String trimmed = topic.trim();
                if (!trimmed.isEmpty()) {
                    topicList.add(trimmed);
                }
            }
            return topicList;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
