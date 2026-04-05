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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ChatService - LLM chat with skills and tool calling")
class ChatServiceTest {

    private LLMProvider llmProvider;
    private ToolCallingLoop toolCallingLoop;
    private InstructionRegistry instructionRegistry;
    private ToolExecutor toolExecutor;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        llmProvider = mock(LLMProvider.class);
        toolCallingLoop = mock(ToolCallingLoop.class);
        instructionRegistry = mock(InstructionRegistry.class);
        toolExecutor = mock(ToolExecutor.class);
        chatService = new ChatService(llmProvider, toolCallingLoop, instructionRegistry, toolExecutor);

        when(llmProvider.getProviderName()).thenReturn("mock-provider");
        when(instructionRegistry.getActivePackageCount()).thenReturn(0);
    }

    @Nested
    @DisplayName("Direct LLM call (no tool calling)")
    class DirectLLMCall {

        @Test
        @DisplayName("simple message returns LLM response text")
        void simpleMessageReturnsResponse() {
            ChatRequest request = new ChatRequest();
            request.setMessage("Hello");

            ConversationMessage respMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("Hi there!")
                .build();
            LLMResponse llmResponse = LLMResponse.builder()
                .message(respMsg)
                .build();

            when(llmProvider.complete(any(), any())).thenReturn(llmResponse);

            ChatResponse result = chatService.chat(request);

            assertNull(result.getError());
            assertEquals("Hi there!", result.getResponse());
            assertNotNull(result.getMetadata());
            assertEquals("mock-provider", result.getMetadata().get("provider"));
            assertEquals(false, result.getMetadata().get("toolCallingEnabled"));
            verify(llmProvider).complete(any(), any());
            verify(toolCallingLoop, never()).executeWithTools(any(), any());
        }

        @Test
        @DisplayName("LLM exception returns error response")
        void llmExceptionReturnsError() {
            ChatRequest request = new ChatRequest();
            request.setMessage("Hello");

            when(llmProvider.complete(any(), any()))
                .thenThrow(new RuntimeException("API timeout"));

            ChatResponse result = chatService.chat(request);

            assertNotNull(result.getError());
            assertEquals("API timeout", result.getError());
        }
    }

    @Nested
    @DisplayName("Tool calling path")
    class ToolCallingPath {

        @Test
        @DisplayName("useToolCalling=true routes through ToolCallingLoop")
        void toolCallingRoutesThroughLoop() {
            ChatRequest request = new ChatRequest();
            request.setMessage("What's the weather?");
            request.setUseToolCalling(true);

            when(toolExecutor.getToolDefinitions()).thenReturn(
                List.of(Map.of("name", "weather", "description", "Check weather")));

            ToolCall toolCall = new ToolCall("tc-1", "weather", Map.of("city", "Beijing"));

            ConversationMessage respMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("It's sunny in Beijing")
                .build();
            LLMResponse llmResponse = LLMResponse.builder()
                .message(respMsg)
                .toolCalls(List.of(toolCall))
                .build();

            when(toolCallingLoop.executeWithTools(any(), any())).thenReturn(llmResponse);

            ChatResponse result = chatService.chat(request);

            assertNull(result.getError());
            assertEquals("It's sunny in Beijing", result.getResponse());
            assertEquals(List.of("weather"), result.getToolCallsUsed());
            assertEquals(true, result.getMetadata().get("toolCallingEnabled"));
            verify(toolCallingLoop).executeWithTools(any(), any());
            verify(llmProvider, never()).complete(any(), any());
        }
    }

    @Nested
    @DisplayName("Skill activation")
    class SkillActivation {

        @Test
        @DisplayName("activeSkills are activated via InstructionRegistry")
        void skillsAreActivated() {
            ChatRequest request = new ChatRequest();
            request.setMessage("Help me");
            request.setActiveSkills(List.of("soul-comfort", "memory"));

            when(instructionRegistry.getActivePackageCount()).thenReturn(2);

            List<ConversationMessage> injected = List.of(
                ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.SYSTEM)
                    .textContent("skill instructions")
                    .build(),
                ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent("Help me")
                    .build()
            );
            when(instructionRegistry.injectInstructions(any())).thenReturn(injected);

            InstructionPackage pkg1 = mock(InstructionPackage.class);
            when(pkg1.getName()).thenReturn("soul-comfort");
            InstructionPackage pkg2 = mock(InstructionPackage.class);
            when(pkg2.getName()).thenReturn("memory");
            when(instructionRegistry.getActivePackages()).thenReturn(List.of(pkg1, pkg2));

            ConversationMessage respMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("I'm here to help")
                .build();
            LLMResponse llmResponse = LLMResponse.builder()
                .message(respMsg)
                .build();
            when(llmProvider.complete(any(), any())).thenReturn(llmResponse);

            ChatResponse result = chatService.chat(request);

            assertNull(result.getError());
            assertEquals(List.of("soul-comfort", "memory"), result.getSkillsApplied());
            verify(instructionRegistry).deactivateAll();
            verify(instructionRegistry).activatePackage("soul-comfort");
            verify(instructionRegistry).activatePackage("memory");
            verify(instructionRegistry).injectInstructions(any());
        }

        @Test
        @DisplayName("null activeSkills skips activation")
        void nullSkillsSkipsActivation() {
            ChatRequest request = new ChatRequest();
            request.setMessage("Hello");

            ConversationMessage respMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("Hi")
                .build();
            when(llmProvider.complete(any(), any())).thenReturn(
                LLMResponse.builder().message(respMsg).build());

            chatService.chat(request);

            verify(instructionRegistry).deactivateAll();
            verify(instructionRegistry, never()).activatePackage(any());
        }

        @Test
        @DisplayName("failed skill activation logs warning but continues")
        void failedSkillActivationContinues() {
            ChatRequest request = new ChatRequest();
            request.setMessage("Hello");
            request.setActiveSkills(List.of("nonexistent"));

            doThrow(new IllegalArgumentException("Package not found: nonexistent"))
                .when(instructionRegistry).activatePackage("nonexistent");

            ConversationMessage respMsg = ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("Hi")
                .build();
            when(llmProvider.complete(any(), any())).thenReturn(
                LLMResponse.builder().message(respMsg).build());

            ChatResponse result = chatService.chat(request);

            assertNull(result.getError());
            assertEquals("Hi", result.getResponse());
        }
    }

    @Nested
    @DisplayName("getAvailableSkills / getAvailableTools")
    class AvailableResources {

        @Test
        @DisplayName("getAvailableTools delegates to toolExecutor")
        void getAvailableToolsDelegates() {
            List<Map<String, Object>> tools = List.of(
                Map.of("name", "weather", "description", "Check weather"));
            when(toolExecutor.getToolDefinitions()).thenReturn(tools);

            List<Map<String, Object>> result = chatService.getAvailableTools();

            assertEquals(1, result.size());
            assertEquals("weather", result.get(0).get("name"));
        }
    }
}
