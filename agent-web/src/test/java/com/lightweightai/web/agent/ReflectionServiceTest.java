package com.lightweightai.web.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.StreamEventHandler;
import com.lightweightai.kernel.memory.UserMemory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ReflectionService 测试
 *
 * 覆盖：
 * - 情绪分析
 * - 对话要点提取
 * - 反思性回应生成
 * - 重要话题识别
 * - 异常处理（LLM 调用失败时的优雅降级）
 */
@DisplayName("ReflectionService - 反思服务")
class ReflectionServiceTest {

    private MockLLMProvider llmProvider;
    private ReflectionService reflectionService;

    @BeforeEach
    void setUp() {
        llmProvider = new MockLLMProvider();
        reflectionService = new ReflectionService(llmProvider);
    }

    @Test
    @DisplayName("分析用户情绪 - 返回情绪词")
    void shouldAnalyzeEmotion() {
        llmProvider.setNextResponse("焦虑");

        String emotion = reflectionService.analyzeEmotion("我最近压力好大，总是睡不着觉");

        assertEquals("焦虑", emotion);

        // 验证发送给 LLM 的消息包含正确的系统提示
        List<ConversationMessage> sent = llmProvider.getLastMessages();
        assertEquals(2, sent.size());
        assertEquals(ConversationMessage.MessageRole.SYSTEM, sent.get(0).getRole());
        assertTrue(sent.get(0).getTextContent().contains("情绪状态"));
        assertEquals(ConversationMessage.MessageRole.USER, sent.get(1).getRole());
    }

    @Test
    @DisplayName("情绪分析 - LLM 异常时返回'未知'")
    void shouldReturnUnknownWhenLLMFails() {
        llmProvider.setThrowOnComplete(new RuntimeException("API 超时"));

        String emotion = reflectionService.analyzeEmotion("你好");

        assertEquals("未知", emotion);
    }

    @Test
    @DisplayName("提取对话要点")
    void shouldExtractKeyPoints() {
        llmProvider.setNextResponse("用户关心工作压力问题，讨论了失眠和焦虑，情绪从焦躁逐渐趋于平静。");

        List<ConversationMessage> history = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("我最近工作压力很大")
                .build(),
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("听起来你承受了不少压力")
                .build()
        );

        String keyPoints = reflectionService.extractKeyPoints(history);

        assertNotNull(keyPoints);
        assertTrue(keyPoints.contains("工作压力"));
    }

    @Test
    @DisplayName("空历史消息返回空字符串")
    void shouldReturnEmptyForEmptyHistory() {
        String keyPoints = reflectionService.extractKeyPoints(List.of());

        assertEquals("", keyPoints);
    }

    @Test
    @DisplayName("提取要点 - LLM 异常时返回空字符串")
    void shouldReturnEmptyWhenExtractFails() {
        llmProvider.setThrowOnComplete(new RuntimeException("API 错误"));

        List<ConversationMessage> history = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("你好")
                .build()
        );

        String result = reflectionService.extractKeyPoints(history);
        assertEquals("", result);
    }

    @Test
    @DisplayName("生成反思性回应建议")
    void shouldGenerateReflectiveGuidance() {
        llmProvider.setNextResponse("看起来你一直在为家人担忧，试着先关注自己的感受如何？");

        UserMemory userMemory = new UserMemory();
        userMemory.addEmotionRecord("焦虑", "工作问题");
        userMemory.addEmotionRecord("担忧", "家庭问题");
        userMemory.addImportantTopic("家庭关系");

        String guidance = reflectionService.generateReflectiveGuidance("我又开始担心我妈了", userMemory);

        assertNotNull(guidance);
        assertTrue(guidance.length() > 0);

        // 验证 LLM 收到的系统提示包含用户记忆上下文
        List<ConversationMessage> sent = llmProvider.getLastMessages();
        String systemText = sent.get(0).getTextContent();
        assertTrue(systemText.contains("情绪状态") || systemText.contains("焦虑"));
        assertTrue(systemText.contains("家庭关系"));
    }

    @Test
    @DisplayName("生成反思性回应 - 无 UserMemory")
    void shouldGenerateGuidanceWithoutUserMemory() {
        llmProvider.setNextResponse("你现在感觉怎么样？");

        String guidance = reflectionService.generateReflectiveGuidance("我今天心情不好", null);

        assertEquals("你现在感觉怎么样？", guidance);
    }

    @Test
    @DisplayName("识别重要话题 - 解析逗号分隔结果")
    void shouldIdentifyImportantTopics() {
        llmProvider.setNextResponse("工作压力，人际关系，自我价值");

        List<String> topics = reflectionService.identifyImportantTopics("我最近工作很累，和同事关系也不好，觉得自己很没用");

        assertEquals(3, topics.size());
        assertTrue(topics.contains("工作压力"));
        assertTrue(topics.contains("人际关系"));
        assertTrue(topics.contains("自我价值"));
    }

    @Test
    @DisplayName("识别话题 - 支持多种分隔符")
    void shouldParseDifferentSeparators() {
        llmProvider.setNextResponse("睡眠问题、焦虑情绪，家庭矛盾");

        List<String> topics = reflectionService.identifyImportantTopics("测试文本");

        assertEquals(3, topics.size());
    }

    @Test
    @DisplayName("识别话题 - LLM 异常时返回空列表")
    void shouldReturnEmptyListOnError() {
        llmProvider.setThrowOnComplete(new RuntimeException("网络错误"));

        List<String> topics = reflectionService.identifyImportantTopics("测试");

        assertTrue(topics.isEmpty());
    }

    // ==================== 辅助类 ====================

    static class MockLLMProvider implements LLMProvider {
        private String nextResponse;
        private List<ConversationMessage> lastMessages;
        private RuntimeException throwOnComplete;

        void setNextResponse(String response) {
            this.nextResponse = response;
            this.throwOnComplete = null;
        }

        void setThrowOnComplete(RuntimeException e) {
            this.throwOnComplete = e;
        }

        List<ConversationMessage> getLastMessages() {
            return lastMessages;
        }

        @Override
        public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
            this.lastMessages = new ArrayList<>(messages);

            if (throwOnComplete != null) {
                throw throwOnComplete;
            }

            ConversationMessage msg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent(nextResponse != null ? nextResponse : "")
                .build();
            return LLMResponse.builder()
                .message(msg)
                .stopReason("stop")
                .build();
        }

        @Override
        public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public CompletableFuture<LLMResponse> completeStream(
                List<ConversationMessage> messages, LLMOptions options,
                StreamEventHandler handler) {
            return CompletableFuture.completedFuture(complete(messages, options));
        }

        @Override
        public String getProviderName() {
            return "mock";
        }
        @Override
        public ModelCapability getModelCapability() {
            return null;
        }
    }
}
