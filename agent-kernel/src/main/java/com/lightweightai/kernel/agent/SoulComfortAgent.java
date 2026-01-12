package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.memory.ConversationMemory;
import com.lightweightai.kernel.memory.UserMemory;

import java.util.ArrayList;
import java.util.List;

/**
 * 心灵引导Agent - 解忧杂货铺的核心AI
 *
 * 具备能力：
 * 1. 记忆能力 - 记住用户的对话历史和个人信息
 * 2. 反思能力 - 深度理解用户的情绪和需求
 * 3. 共情能力 - 提供温暖、包容的回应
 * 4. 引导能力 - 帮助用户理清思路，找到答案
 */
public class SoulComfortAgent {

    private final LLMProvider llmProvider;
    private final ConversationMemory memory;
    private final ReflectionService reflectionService;

    // 心灵引导的核心系统提示
    private static final String SOUL_COMFORT_SYSTEM_PROMPT =
        "你是「解忧杂货铺」的店主，一位温暖、富有同理心的心灵引导者。\n" +
        "\n" +
        "你的使命：\n" +
        "- 用心倾听每一个来访者的心声\n" +
        "- 提供温暖、包容、不评判的陪伴\n" +
        "- 帮助他们理清思路，找到内心的答案\n" +
        "- 给予希望和力量，而不是简单的建议\n" +
        "\n" +
        "你的风格：\n" +
        "- 语气温和、真诚、不做作\n" +
        "- 善于提出启发性的问题\n" +
        "- 会适当分享智慧，但不说教\n" +
        "- 尊重对方的感受和选择\n" +
        "- 回应简洁而有力量（通常2-4句话）\n" +
        "\n" +
        "重要原则：\n" +
        "1. 永远不要轻视对方的感受\n" +
        "2. 不要给出具体的行动建议（除非对方明确请求）\n" +
        "3. 帮助对方自己找到答案，而不是替他们决定\n" +
        "4. 当对方情绪激动时，先接纳情绪，再探讨问题\n" +
        "5. 保持希望的态度，但不过度乐观\n" +
        "\n" +
        "你就像夜晚的灯笼，不刺眼，但能照亮前行的路。🏮";

    public SoulComfortAgent(LLMProvider llmProvider) {
        this.llmProvider = llmProvider;
        this.memory = new ConversationMemory();
        this.reflectionService = new ReflectionService(llmProvider);
    }

    public SoulComfortAgent(LLMProvider llmProvider, ConversationMemory memory) {
        this.llmProvider = llmProvider;
        this.memory = memory;
        this.reflectionService = new ReflectionService(llmProvider);
    }

    /**
     * 处理用户消息并生成回应
     */
    public String chat(String sessionId, String userMessage) {
        // 1. 保存用户消息到记忆
        ConversationMessage userMsg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(userMessage)
            .build();
        memory.addMessage(sessionId, userMsg);

        // 2. 分析用户情绪并记录
        String emotion = reflectionService.analyzeEmotion(userMessage);
        UserMemory userMemory = memory.getUserMemory(sessionId);
        userMemory.addEmotionRecord(emotion, userMessage);

        // 3. 识别并记录重要话题
        List<String> topics = reflectionService.identifyImportantTopics(userMessage);
        for (String topic : topics) {
            userMemory.addImportantTopic(topic);
        }

        // 4. 构建包含记忆的对话上下文
        List<ConversationMessage> messages = buildContextWithMemory(sessionId, userMessage);

        // 5. 调用LLM生成回应
        LLMOptions options = LLMOptions.builder()
            .maxTokens(500)
            .temperature(0.8) // 稍高的温度，让回应更有人性化
            .build();

        LLMResponse response = llmProvider.complete(messages, options);
        String assistantResponse = response.getMessage().getTextContent();

        // 6. 保存助手回应到记忆
        ConversationMessage assistantMsg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .textContent(assistantResponse)
            .build();
        memory.addMessage(sessionId, assistantMsg);

        return assistantResponse;
    }

    /**
     * 构建包含记忆的对话上下文
     */
    private List<ConversationMessage> buildContextWithMemory(String sessionId, String currentMessage) {
        List<ConversationMessage> messages = new ArrayList<>();

        // 1. 添加核心系统提示
        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.SYSTEM)
            .textContent(SOUL_COMFORT_SYSTEM_PROMPT)
            .build());

        // 2. 添加用户记忆上下文
        UserMemory userMemory = memory.getUserMemory(sessionId);
        String memoryContext = buildMemoryContext(userMemory);
        if (!memoryContext.isEmpty()) {
            messages.add(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.SYSTEM)
                .textContent("关于这位访客的记忆：\n" + memoryContext)
                .build());
        }

        // 3. 添加最近的对话历史（最多10轮）
        List<ConversationMessage> recentHistory = memory.getRecentMessages(sessionId, 20);

        // 排除当前消息（因为会单独添加）
        for (ConversationMessage msg : recentHistory) {
            if (msg.getRole() != ConversationMessage.MessageRole.SYSTEM) {
                messages.add(msg);
            }
        }

        return messages;
    }

    /**
     * 构建用户记忆的上下文描述
     */
    private String buildMemoryContext(UserMemory userMemory) {
        StringBuilder context = new StringBuilder();

        // 添加基本信息
        String userName = userMemory.get("userName");
        if (userName != null) {
            context.append("- 称呼：").append(userName).append("\n");
        }

        // 添加最近的情绪状态
        List<UserMemory.EmotionRecord> recentEmotions = userMemory.getRecentEmotions(3);
        if (!recentEmotions.isEmpty()) {
            context.append("- 最近情绪：");
            for (UserMemory.EmotionRecord record : recentEmotions) {
                context.append(record.getEmotion()).append("、");
            }
            context.setLength(context.length() - 1); // 移除最后的顿号
            context.append("\n");
        }

        // 添加关注的话题
        List<String> topics = userMemory.getImportantTopics();
        if (!topics.isEmpty()) {
            context.append("- 关注话题：").append(String.join("、", topics)).append("\n");
        }

        // 添加其他重要信息
        String concerns = userMemory.get("mainConcerns");
        if (concerns != null) {
            context.append("- 主要困扰：").append(concerns).append("\n");
        }

        return context.toString();
    }

    /**
     * 获取会话摘要
     */
    public String getSessionSummary(String sessionId) {
        List<ConversationMessage> history = memory.getRecentMessages(sessionId, 10);
        if (history.isEmpty()) {
            return "暂无对话历史";
        }

        return reflectionService.extractKeyPoints(history);
    }

    /**
     * 清空会话
     */
    public void clearSession(String sessionId) {
        memory.clearHistory(sessionId);
    }

    /**
     * 获取用户记忆
     */
    public UserMemory getUserMemory(String sessionId) {
        return memory.getUserMemory(sessionId);
    }

    /**
     * 获取会话记忆（用于调试）
     */
    public ConversationMemory getMemory() {
        return memory;
    }
}
