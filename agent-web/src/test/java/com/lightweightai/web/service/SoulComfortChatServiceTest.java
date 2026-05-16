package com.lightweightai.web.service;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.file.SessionTranscript;
import com.lightweightai.kernel.memory.model.TranscriptEntry;
import com.lightweightai.kernel.memory.queue.LaneQueueManager;
import com.lightweightai.kernel.memory.tools.MemoryToolkit;
import com.lightweightai.kernel.prompt.PromptContext;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.prompt.PromptRequest;
import com.lightweightai.safety.ContentFilter;
import com.lightweightai.safety.CrisisDetector;
import com.lightweightai.safety.CrisisResource;
import com.lightweightai.safety.SafetyResult;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SoulComfortChatService 单元测试
 *
 * 覆盖关键路径：
 * - 正常聊天流程（LLM 调用 + 响应构建）
 * - 危机检测前置拦截
 * - 内容过滤应用
 * - 用户姓名提取写入持久记忆
 * - 会话历史获取
 * - 会话清除
 * - 异常处理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SoulComfortChatService - 心灵引导聊天服务")
class SoulComfortChatServiceTest {

    @Mock
    private LLMProvider llmProvider;

    @Mock
    private PromptEngine promptEngine;

    @Mock
    private FileMemoryManager memoryManager;

    @Mock
    private LaneQueueManager laneManager;

    @Mock
    private MemoryToolkit memoryToolkit;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ToolCallingLoop toolCallingLoop;

    @Mock
    private MemoryProvider memoryProvider;

    @Mock
    private CrisisDetector crisisDetector;

    @Mock
    private ContentFilter contentFilter;

    private SoulComfortChatService service;

    @BeforeEach
    void setUp() throws Exception {
        // ToolRegistry.getEnabled() returns empty list
        when(toolRegistry.getEnabled()).thenReturn(List.of());

        // PromptEngine.getMemoryProvider() returns mock
        lenient().when(promptEngine.getMemoryProvider()).thenReturn(memoryProvider);

        // PromptEngine.build() returns a minimal PromptContext so SoulComfortAgent can build messages
        lenient().when(promptEngine.build(any(PromptRequest.class))).thenAnswer(inv -> {
            PromptRequest req = inv.getArgument(0);
            return PromptContext.builder()
                    .systemPrompt("你是心灵引导者。")
                    .userMessage(req.getUserMessage())
                    .historyMessages(List.of(Message.user(req.getUserMessage())))
                    .build();
        });

        // LaneQueueManager.submit() executes synchronously
        lenient().when(laneManager.submit(anyString(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Supplier<Object> supplier = inv.getArgument(1);
            return CompletableFuture.completedFuture(supplier.get());
        });

        // FileMemoryManager.getSession() returns a mock SessionTranscript by default
        SessionTranscript emptyTranscript = mock(SessionTranscript.class);
        lenient().when(emptyTranscript.readAll()).thenReturn(List.of());
        lenient().when(memoryManager.getSession(anyString())).thenReturn(emptyTranscript);

        // Construct the service
        service = new SoulComfortChatService(
                llmProvider, promptEngine, memoryManager,
                laneManager, memoryToolkit, toolRegistry, toolCallingLoop
        );

        // Inject optional safety components via reflection
        injectField(service, "crisisDetector", crisisDetector);
        injectField(service, "contentFilter", contentFilter);
        injectField(service, "crisisDetectionEnabled", true);
    }

    // ==================== 正常聊天流程 ====================

    @Test
    @DisplayName("正常聊天 - 返回 LLM 响应文本、技能列表和元数据")
    void chat_normalFlow_returnsResponseWithSkillsAndMetadata() {
        // Arrange
        String expectedResponse = "我理解你的感受，这很正常。";
        stubLLMResponse(expectedResponse);
        stubSafetyPass();
        when(contentFilter.filter(expectedResponse)).thenReturn(expectedResponse);

        ChatRequest request = createRequest("今天心情不好", "session-1");

        // Act
        ChatResponse response = service.chat(request);

        // Assert - verify payload, not just ceremony
        assertEquals(expectedResponse, response.getResponse(),
                "Response text must match LLM output");
        assertNotNull(response.getSkillsApplied(),
                "skillsApplied must not be null");
        assertTrue(response.getSkillsApplied().contains("soul-comfort"),
                "skillsApplied must contain 'soul-comfort'");
        assertTrue(response.getSkillsApplied().contains("openclaw-memory"),
                "skillsApplied must contain 'openclaw-memory'");

        // Metadata payload assertions
        Map<String, Object> metadata = response.getMetadata();
        assertNotNull(metadata, "metadata must not be null");
        assertEquals("soul-comfort", metadata.get("mode"));
        assertEquals(true, metadata.get("hasMemory"));
        assertEquals("openclaw-promptengine", metadata.get("memorySystem"));

        // No error
        assertNull(response.getError(), "error must be null on success");
    }

    @Test
    @DisplayName("正常聊天 - 对话写入 Ephemeral 记忆索引")
    void chat_normalFlow_indexesConversationToEphemeralMemory() {
        // Arrange
        String userMsg = "我很焦虑";
        String llmReply = "焦虑是一种自然的情绪反应。";
        stubLLMResponse(llmReply);
        stubSafetyPass();
        when(contentFilter.filter(llmReply)).thenReturn(llmReply);

        ChatRequest request = createRequest(userMsg, "session-2");

        // Act
        service.chat(request);

        // Assert - verify ephemeral memory write happened with actual content
        verify(memoryManager).appendEphemeral(argThat(content ->
                content.contains(userMsg) && content.contains(llmReply)
        ));
    }

    // ==================== 危机检测 ====================

    @Test
    @DisplayName("危机检测 - 检测到危机时直接返回安全响应，不调用 LLM")
    void chat_crisisDetected_returnsSafetyResponseWithoutLLMCall() {
        // Arrange
        String crisisMessage = "我不想活了";
        CrisisResource resource = new CrisisResource("生命热线", "400-821-1215", "24小时");
        SafetyResult crisisResult = SafetyResult.crisis(
                List.of("不想活"), List.of(resource)
        );
        when(crisisDetector.check(crisisMessage)).thenReturn(crisisResult);

        ChatRequest request = createRequest(crisisMessage, "session-crisis");

        // Act
        ChatResponse response = service.chat(request);

        // Assert - verify crisis response payload
        assertNotNull(response.getResponse(), "Crisis response must not be null");
        assertTrue(response.getResponse().contains("生命很宝贵"),
                "Crisis response must contain comfort text");
        assertTrue(response.getResponse().contains("生命热线"),
                "Crisis response must include resource name");
        assertTrue(response.getResponse().contains("400-821-1215"),
                "Crisis response must include resource phone number");

        // Skills applied must indicate crisis-safety
        assertNotNull(response.getSkillsApplied());
        assertTrue(response.getSkillsApplied().contains("crisis-safety"));

        // LLM must NOT be called
        verifyNoInteractions(llmProvider);
    }

    @Test
    @DisplayName("危机检测 - 安全消息不触发危机，正常走 LLM 路径")
    void chat_safeMessage_proceedsToLLM() {
        // Arrange
        stubSafetyPass();
        String reply = "你好！有什么想聊的吗？";
        stubLLMResponse(reply);
        when(contentFilter.filter(reply)).thenReturn(reply);

        ChatRequest request = createRequest("你好", "session-safe");

        // Act
        ChatResponse response = service.chat(request);

        // Assert - LLM was called (indirectly verified by getting its response)
        assertEquals(reply, response.getResponse());
        verify(llmProvider).complete(anyList(), any(LLMOptions.class));
    }

    // ==================== 内容过滤 ====================

    @Test
    @DisplayName("内容过滤 - ContentFilter 应用于 LLM 响应")
    void chat_contentFilter_appliedToResponse() {
        // Arrange
        String rawResponse = "你患有抑郁症，建议立即就医。";
        String filteredResponse = "你描述的这些感受值得被认真对待。";
        stubLLMResponse(rawResponse);
        stubSafetyPass();
        when(contentFilter.filter(rawResponse)).thenReturn(filteredResponse);

        ChatRequest request = createRequest("我每天都很难过", "session-filter");

        // Act
        ChatResponse response = service.chat(request);

        // Assert - response contains filtered text, not raw
        assertEquals(filteredResponse, response.getResponse(),
                "Response must be the filtered version, not the raw LLM output");
        verify(contentFilter).filter(rawResponse);
    }

    // ==================== 用户姓名提取 ====================

    @Test
    @DisplayName("用户姓名提取 - '我叫小明' 模式写入 Durable 记忆")
    void chat_extractUserName_woJiao_writesDurableMemory() {
        // Arrange
        String userMsg = "我叫小明，今天心情不好";
        stubLLMResponse("你好小明");
        stubSafetyPass();
        when(contentFilter.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));

        ChatRequest request = createRequest(userMsg, "session-name");

        // Act
        service.chat(request);

        // Assert - verify durable memory write captures the extracted name
        verify(memoryManager).appendDurable(
                eq("用户信息"),
                argThat(content -> content.contains("小明"))
        );
    }

    @Test
    @DisplayName("用户姓名提取 - '叫我' 模式也能提取姓名")
    void chat_extractUserName_jiaoWo_writesDurableMemory() {
        // Arrange
        String userMsg = "叫我阿华就好";
        stubLLMResponse("好的阿华");
        stubSafetyPass();
        when(contentFilter.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));

        ChatRequest request = createRequest(userMsg, "session-name-2");

        // Act
        service.chat(request);

        // Assert
        verify(memoryManager).appendDurable(
                eq("用户信息"),
                argThat(content -> content.contains("阿华"))
        );
    }

    @Test
    @DisplayName("用户姓名提取 - 无姓名模式时不写入 Durable 记忆")
    void chat_noNamePattern_doesNotWriteDurable() {
        // Arrange
        String userMsg = "今天天气真好";
        stubLLMResponse("是啊，好天气让人心情愉快。");
        stubSafetyPass();
        when(contentFilter.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));

        ChatRequest request = createRequest(userMsg, "session-no-name");

        // Act
        service.chat(request);

        // Assert - appendDurable should NOT be called with user info
        verify(memoryManager, never()).appendDurable(eq("用户信息"), anyString());
    }

    // ==================== 会话历史 ====================

    @Test
    @DisplayName("获取会话历史 - 返回 user/assistant 消息的 role 和 content")
    void getSessionHistory_returnsFilteredRoleContentPairs() {
        // Arrange
        String sessionId = "session-history";
        SessionTranscript transcript = mock(SessionTranscript.class);
        when(memoryManager.getSession(sessionId)).thenReturn(transcript);
        when(transcript.readAll()).thenReturn(List.of(
                TranscriptEntry.userMessage("你好"),
                TranscriptEntry.assistantMessage("你好！有什么想聊的吗？"),
                TranscriptEntry.systemEvent("session start", null),
                TranscriptEntry.userMessage("我最近压力很大"),
                TranscriptEntry.assistantMessage("压力大的时候，找人倾诉是很好的选择。")
        ));

        // Act
        List<Map<String, String>> history = service.getSessionHistory(sessionId);

        // Assert - system events should be filtered out
        assertEquals(4, history.size(), "Should only include user and assistant messages");

        // Verify first pair
        assertEquals("user", history.get(0).get("role"));
        assertEquals("你好", history.get(0).get("content"));
        assertEquals("assistant", history.get(1).get("role"));
        assertEquals("你好！有什么想聊的吗？", history.get(1).get("content"));

        // Verify second pair
        assertEquals("user", history.get(2).get("role"));
        assertEquals("我最近压力很大", history.get(2).get("content"));
        assertEquals("assistant", history.get(3).get("role"));
        assertEquals("压力大的时候，找人倾诉是很好的选择。", history.get(3).get("content"));
    }

    @Test
    @DisplayName("获取会话历史 - 空会话返回空列表")
    void getSessionHistory_emptySession_returnsEmptyList() {
        // Arrange
        String sessionId = "session-empty";
        SessionTranscript transcript = mock(SessionTranscript.class);
        when(memoryManager.getSession(sessionId)).thenReturn(transcript);
        when(transcript.readAll()).thenReturn(List.of());

        // Act
        List<Map<String, String>> history = service.getSessionHistory(sessionId);

        // Assert
        assertTrue(history.isEmpty());
    }

    // ==================== 会话清除 ====================

    @Test
    @DisplayName("清空会话 - 默认 Agent 和模型 Agent 全部清除")
    void clearSession_clearsDefaultAndModelAgents() {
        // Arrange - first do a chat to ensure session exists
        String sessionId = "session-clear";
        stubSafetyPass();
        stubLLMResponse("你好");
        when(contentFilter.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));

        ChatRequest request = createRequest("你好", sessionId);
        service.chat(request);

        // Act
        service.clearSession(sessionId);

        // Assert - the service should not throw and the session should be removed
        // Verify by getting history from a fresh transcript (session was removed from activeSessions)
        // The next chat would create a new session
        assertDoesNotThrow(() -> service.clearSession(sessionId),
                "Clearing a session should not throw even if called again");
    }

    // ==================== 模型选择 ====================

    @Test
    @DisplayName("模型选择 - 空模型使用默认 Agent")
    void chat_nullModel_usesDefaultAgent() {
        // Arrange
        stubSafetyPass();
        String reply = "默认模型回复";
        stubLLMResponse(reply);
        when(contentFilter.filter(reply)).thenReturn(reply);

        ChatRequest request = createRequest("你好", "session-default-model");
        request.setModel(null);

        // Act
        ChatResponse response = service.chat(request);

        // Assert
        assertEquals(reply, response.getResponse());
        // metadata should NOT contain "model" key when model is null
        assertFalse(response.getMetadata().containsKey("model"),
                "metadata should not contain 'model' when no model is specified");
    }

    @Test
    @DisplayName("模型选择 - 指定模型时元数据包含模型名")
    void chat_specificModel_metadataContainsModel() throws Exception {
        // Arrange
        stubSafetyPass();
        String reply = "指定模型回复";
        stubLLMResponse(reply);
        when(contentFilter.filter(reply)).thenReturn(reply);

        // Set openRouterApiKey to empty so getAgentForModel falls back to default
        injectField(service, "openRouterApiKey", "");

        ChatRequest request = createRequest("你好", "session-model");
        request.setModel("anthropic/claude-3.5-sonnet");

        // Act
        ChatResponse response = service.chat(request);

        // Assert
        assertEquals(reply, response.getResponse());
        assertEquals("anthropic/claude-3.5-sonnet", response.getMetadata().get("model"),
                "metadata must contain the specified model name");
    }

    // ==================== 异常处理 ====================

    @Test
    @DisplayName("异常处理 - LLM 调用失败返回 error 响应")
    void chat_llmException_returnsErrorResponse() {
        // Arrange
        stubSafetyPass();
        when(llmProvider.complete(anyList(), any(LLMOptions.class)))
                .thenThrow(new RuntimeException("LLM service unavailable"));

        ChatRequest request = createRequest("你好", "session-error");

        // Act
        ChatResponse response = service.chat(request);

        // Assert - verify error payload
        assertNotNull(response.getError(), "error field must be set");
        assertTrue(response.getError().contains("LLM service unavailable"),
                "error must contain the exception message");
        assertNull(response.getResponse(),
                "response text must be null when error occurs");
    }

    @Test
    @DisplayName("异常处理 - 调试模式下异常也返回 debug 信息")
    void chat_exceptionInDebugMode_returnsDebugInfo() {
        // Arrange
        stubSafetyPass();
        when(llmProvider.complete(anyList(), any(LLMOptions.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        ChatRequest request = createRequest("你好", "session-debug-error");
        request.setDebug(true);

        // Act
        ChatResponse response = service.chat(request);

        // Assert
        assertNotNull(response.getError());
        // Debug info should be populated even on error
        assertNotNull(response.getDebug(),
                "debug info must be present in debug mode even on error");
    }

    // ==================== sessionId 默认值 ====================

    @Test
    @DisplayName("会话ID - null sessionId 使用 'default' 作为默认值")
    void chat_nullSessionId_usesDefault() {
        // Arrange
        stubSafetyPass();
        String reply = "你好";
        stubLLMResponse(reply);
        when(contentFilter.filter(reply)).thenReturn(reply);

        ChatRequest request = createRequest("你好", null);

        // Act
        ChatResponse response = service.chat(request);

        // Assert
        assertEquals(reply, response.getResponse());
        // Verify laneManager was called with "default" sessionId
        verify(laneManager).submit(eq("default"), any());
    }

    // ==================== 调试模式 ====================

    @Test
    @DisplayName("调试模式 - debug=true 时返回 debug 信息")
    void chat_debugMode_returnsDebugInfo() {
        // Arrange
        stubSafetyPass();
        String reply = "调试模式回复";
        stubLLMResponse(reply);
        when(contentFilter.filter(reply)).thenReturn(reply);

        ChatRequest request = createRequest("测试", "session-debug");
        request.setDebug(true);

        // Act
        ChatResponse response = service.chat(request);

        // Assert
        assertEquals(reply, response.getResponse());
        assertNotNull(response.getDebug(),
                "debug info must be present when debug mode is enabled");
    }

    @Test
    @DisplayName("调试模式 - debug=false 时不返回 debug 信息")
    void chat_nonDebugMode_noDebugInfo() {
        // Arrange
        stubSafetyPass();
        String reply = "普通模式回复";
        stubLLMResponse(reply);
        when(contentFilter.filter(reply)).thenReturn(reply);

        ChatRequest request = createRequest("测试", "session-no-debug");
        request.setDebug(false);

        // Act
        ChatResponse response = service.chat(request);

        // Assert
        assertEquals(reply, response.getResponse());
        assertNull(response.getDebug(),
                "debug info must be null when debug mode is disabled");
    }

    // ==================== 辅助方法 ====================

    private ChatRequest createRequest(String message, String sessionId) {
        ChatRequest request = new ChatRequest();
        request.setMessage(message);
        request.setSessionId(sessionId);
        return request;
    }

    /**
     * Stub LLM to return a text response with no tool calls.
     */
    private void stubLLMResponse(String text) {
        LLMResponse llmResponse = LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent(text)
                        .build())
                .stopReason("end_turn")
                .build();
        lenient().when(llmProvider.complete(anyList(), any(LLMOptions.class)))
                .thenReturn(llmResponse);
    }

    /**
     * Stub crisis detector to return safe result.
     */
    private void stubSafetyPass() {
        lenient().when(crisisDetector.check(anyString())).thenReturn(SafetyResult.safe());
    }

    /**
     * Inject a value into a private field via reflection.
     */
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
