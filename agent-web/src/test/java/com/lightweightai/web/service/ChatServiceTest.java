package com.lightweightai.web.service;

import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.instruction.InstructionPackage;
import com.lightweightai.kernel.instruction.InstructionRegistry;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ChatService 单元测试
 *
 * 覆盖关键路径：
 * - 直接 LLM 调用（不使用工具）
 * - 工具调用模式
 * - 技能激活/注入
 * - 异常处理
 * - 可用技能/工具列表查询
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService - 聊天服务核心逻辑")
class ChatServiceTest {

    @Mock
    private LLMProvider llmProvider;

    @Mock
    private ToolCallingLoop toolCallingLoop;

    @Mock
    private InstructionRegistry instructionRegistry;

    @Mock
    private ToolExecutor toolExecutor;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(llmProvider, toolCallingLoop, instructionRegistry, toolExecutor);
    }

    // ==================== 直接 LLM 调用 ====================

    @Test
    @DisplayName("直接 LLM 调用 - 不使用工具时正常返回")
    void shouldCallLLMDirectlyWhenToolCallingDisabled() {
        ChatRequest request = new ChatRequest();
        request.setMessage("你好");
        request.setUseToolCalling(false);

        LLMResponse llmResponse = createTextResponse("你好，有什么可以帮你的？");
        when(llmProvider.complete(anyList(), any(LLMOptions.class))).thenReturn(llmResponse);
        when(llmProvider.getProviderName()).thenReturn("mock");
        when(instructionRegistry.getActivePackageCount()).thenReturn(0);

        ChatResponse response = chatService.chat(request);

        assertNotNull(response);
        assertEquals("你好，有什么可以帮你的？", response.getResponse());
        assertNull(response.getError());
        assertNotNull(response.getMetadata());
        assertEquals("mock", response.getMetadata().get("provider"));
        assertEquals(false, response.getMetadata().get("toolCallingEnabled"));

        verify(llmProvider).complete(anyList(), any(LLMOptions.class));
        verify(toolCallingLoop, never()).executeWithTools(anyList(), any(LLMOptions.class));
    }

    // ==================== 工具调用模式 ====================

    @Test
    @DisplayName("工具调用模式 - 使用 ToolCallingLoop 执行")
    void shouldUseToolCallingLoopWhenEnabled() {
        ChatRequest request = new ChatRequest();
        request.setMessage("计算 3+4");
        request.setUseToolCalling(true);

        List<Map<String, Object>> toolDefs = List.of(
            Map.of("name", "add", "description", "Add numbers")
        );
        when(toolExecutor.getToolDefinitions()).thenReturn(toolDefs);

        LLMResponse llmResponse = createToolCallResponse("add", Map.of("a", 3, "b", 4));
        when(toolCallingLoop.executeWithTools(anyList(), any(LLMOptions.class))).thenReturn(llmResponse);
        when(llmProvider.getProviderName()).thenReturn("mock");
        when(instructionRegistry.getActivePackageCount()).thenReturn(0);

        ChatResponse response = chatService.chat(request);

        assertNotNull(response);
        assertNull(response.getError());
        assertEquals(true, response.getMetadata().get("toolCallingEnabled"));
        assertNotNull(response.getToolCallsUsed());
        assertTrue(response.getToolCallsUsed().contains("add"));

        verify(toolCallingLoop).executeWithTools(anyList(), any(LLMOptions.class));
        verify(llmProvider, never()).complete(anyList(), any(LLMOptions.class));
    }

    // ==================== 技能激活 ====================

    @Test
    @DisplayName("技能激活 - 请求中指定技能时激活并注入指令")
    void shouldActivateSkillsWhenSpecified() {
        ChatRequest request = new ChatRequest();
        request.setMessage("帮我写代码");
        request.setActiveSkills(List.of("coding-assist"));

        LLMResponse llmResponse = createTextResponse("好的，请说需求");
        when(llmProvider.complete(anyList(), any(LLMOptions.class))).thenReturn(llmResponse);
        when(llmProvider.getProviderName()).thenReturn("mock");

        // First call returns 0 (before activation check for injection),
        // subsequent calls return 1 (after activation, for metadata)
        when(instructionRegistry.getActivePackageCount()).thenReturn(1);
        when(instructionRegistry.injectInstructions(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        InstructionPackage pkg = mock(InstructionPackage.class);
        when(pkg.getName()).thenReturn("coding-assist");
        when(instructionRegistry.getActivePackages()).thenReturn(List.of(pkg));

        ChatResponse response = chatService.chat(request);

        assertNotNull(response);
        assertNull(response.getError());
        assertNotNull(response.getSkillsApplied());
        assertTrue(response.getSkillsApplied().contains("coding-assist"));

        verify(instructionRegistry).deactivateAll();
        verify(instructionRegistry).activatePackage("coding-assist");
        verify(instructionRegistry).injectInstructions(anyList());
    }

    @Test
    @DisplayName("无技能时不注入指令")
    void shouldNotInjectInstructionsWhenNoSkills() {
        ChatRequest request = new ChatRequest();
        request.setMessage("你好");

        LLMResponse llmResponse = createTextResponse("你好");
        when(llmProvider.complete(anyList(), any(LLMOptions.class))).thenReturn(llmResponse);
        when(llmProvider.getProviderName()).thenReturn("mock");
        when(instructionRegistry.getActivePackageCount()).thenReturn(0);

        chatService.chat(request);

        verify(instructionRegistry).deactivateAll();
        verify(instructionRegistry, never()).injectInstructions(anyList());
    }

    // ==================== 异常处理 ====================

    @Test
    @DisplayName("LLM 调用异常时返回错误响应")
    void shouldReturnErrorResponseOnException() {
        ChatRequest request = new ChatRequest();
        request.setMessage("测试");

        when(llmProvider.complete(anyList(), any(LLMOptions.class)))
            .thenThrow(new RuntimeException("API 调用失败"));
        when(instructionRegistry.getActivePackageCount()).thenReturn(0);

        ChatResponse response = chatService.chat(request);

        assertNotNull(response);
        assertNotNull(response.getError());
        assertTrue(response.getError().contains("API 调用失败"));
        assertNull(response.getResponse());
    }

    // ==================== 可用技能/工具 ====================

    @Test
    @DisplayName("获取可用工具列表")
    void shouldReturnAvailableTools() {
        List<Map<String, Object>> toolDefs = List.of(
            Map.of("name", "search", "description", "Search the web")
        );
        when(toolExecutor.getToolDefinitions()).thenReturn(toolDefs);

        List<Map<String, Object>> tools = chatService.getAvailableTools();

        assertEquals(1, tools.size());
        assertEquals("search", tools.get(0).get("name"));
    }

    @Test
    @DisplayName("获取可用技能列表")
    void shouldReturnAvailableSkills() {
        InstructionPackage pkg = mock(InstructionPackage.class);
        when(pkg.getName()).thenReturn("coding");
        when(pkg.getDescription()).thenReturn("Coding assistant");
        when(pkg.getFormat()).thenReturn("claude");
        when(instructionRegistry.getAllPackages()).thenReturn(new ArrayList<>(List.of(pkg)));

        List<Map<String, String>> skills = chatService.getAvailableSkills();

        assertEquals(1, skills.size());
        assertEquals("coding", skills.get(0).get("name"));
        assertEquals("Coding assistant", skills.get(0).get("description"));
    }

    // ==================== 辅助方法 ====================

    private LLMResponse createTextResponse(String text) {
        ConversationMessage message = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .textContent(text)
            .build();
        return LLMResponse.builder()
            .message(message)
            .build();
    }

    private LLMResponse createToolCallResponse(String toolName, Map<String, Object> args) {
        ConversationMessage message = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .textContent("")
            .build();
        List<ToolCall> toolCalls = List.of(new ToolCall("tc-1", toolName, args));
        return LLMResponse.builder()
            .message(message)
            .toolCalls(toolCalls)
            .build();
    }
}
