package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Transmission chain: ToolResult → ConversationMessage (TOOL role) → LLM conversation history
 *
 * When a tool returns a result, ToolCallingLoop converts it to a ConversationMessage
 * with role=TOOL, then adds it to the conversation for the next LLM call.
 * The next LLM call must receive this message with correct metadata (tool_use_id, is_error).
 *
 * This test uses CapturingLLMProvider to inspect what the second LLM call actually receives.
 */
@DisplayName("Transmission: ToolResult → ConversationMessage → next LLM call")
class ToolResultConversationMessageTest {

    @Test
    @DisplayName("成功 ToolResult 转换为 TOOL role 消息，携带 tool_use_id 和 is_error=false")
    void successfulToolResultAppearsInNextLLMCall() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override public String getName() { return "get_weather"; }
            @Override public String getDescription() { return "weather"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("sunny, 25C");
            }
        });

        CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                "get_weather", Map.of("city", "Beijing"), "It's sunny in Beijing.");

        ToolExecutor executor = new ToolExecutor(registry);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(spy)
                .toolExecutor(executor)
                .maxIterations(5)
                .build();

        LLMResponse response = loop.executeWithTools(
                List.of(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent("weather in beijing?")
                        .build()),
                LLMOptions.builder().build());

        assertEquals(2, spy.callCount(), "Should have 2 LLM calls");

        // Inspect the second call's conversation
        List<ConversationMessage> secondCallMessages = spy.messagesHistory().get(1);
        assertNotNull(secondCallMessages);

        // Find the TOOL message
        ConversationMessage toolMsg = secondCallMessages.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                .findFirst()
                .orElse(null);
        assertNotNull(toolMsg, "Second LLM call must contain a TOOL role message");
        assertEquals("sunny, 25C", toolMsg.getTextContent(),
                "TOOL message content must be the tool's actual output");

        // Verify metadata
        Map<String, Object> metadata = toolMsg.getMetadata();
        assertNotNull(metadata, "TOOL message must have metadata");
        assertNotNull(metadata.get("tool_use_id"), "must have tool_use_id");
        assertEquals(false, metadata.get("is_error"), "successful result must have is_error=false");
    }

    @Test
    @DisplayName("失败 ToolResult (tool not found) 转换为 TOOL role 消息，携带 is_error=true")
    void errorToolResultAppearsWithIsErrorTrue() {
        ToolRegistry registry = new ToolRegistry();
        // Don't register any tools — "nonexistent" will fail

        CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                "nonexistent", Map.of(), "I couldn't find that tool.");

        ToolExecutor executor = new ToolExecutor(registry);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(spy)
                .toolExecutor(executor)
                .maxIterations(5)
                .build();

        LLMResponse response = loop.executeWithTools(
                List.of(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent("do something")
                        .build()),
                LLMOptions.builder().build());

        assertEquals(2, spy.callCount());

        List<ConversationMessage> secondCallMessages = spy.messagesHistory().get(1);
        ConversationMessage toolMsg = secondCallMessages.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.TOOL)
                .findFirst()
                .orElse(null);
        assertNotNull(toolMsg, "Error tool result must still produce a TOOL message");

        Map<String, Object> metadata = toolMsg.getMetadata();
        assertEquals(true, metadata.get("is_error"),
                "Failed tool result must have is_error=true");
    }

    @Test
    @DisplayName("assistant 消息携带 tool_calls metadata（tool_use 请求重放给下一轮 LLM）")
    void assistantMessageWithToolCallsMetadata() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override public String getName() { return "calc"; }
            @Override public String getDescription() { return "calc"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("42");
            }
        });

        CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                "calc", Map.of("expr", "6*7"), "The answer is 42.");

        ToolExecutor executor = new ToolExecutor(registry);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(spy)
                .toolExecutor(executor)
                .maxIterations(5)
                .build();

        loop.executeWithTools(
                List.of(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent("what is 6*7?")
                        .build()),
                LLMOptions.builder().build());

        List<ConversationMessage> secondCallMessages = spy.messagesHistory().get(1);

        // Find the ASSISTANT message that should have tool_calls metadata
        ConversationMessage assistantMsg = secondCallMessages.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.ASSISTANT)
                .findFirst()
                .orElse(null);
        assertNotNull(assistantMsg, "Second call must include the assistant's tool_use message");

        Object toolCallsMeta = assistantMsg.getMetadata().get("tool_calls");
        assertNotNull(toolCallsMeta,
                "Assistant message must carry tool_calls metadata for provider to serialize");
        assertTrue(toolCallsMeta instanceof List,
                "tool_calls metadata should be a List");

        @SuppressWarnings("unchecked")
        List<ToolCall> toolCalls = (List<ToolCall>) toolCallsMeta;
        assertEquals(1, toolCalls.size());
        assertEquals("calc", toolCalls.get(0).getName());
        assertEquals(Map.of("expr", "6*7"), toolCalls.get(0).getArguments());
    }

    @Test
    @DisplayName("reactive: ToolResult chunk 在 TOOL_RESULT 事件中携带正确的工具名和结果")
    void reactiveToolResultChunkHasCorrectPayload() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override public String getName() { return "translate"; }
            @Override public String getDescription() { return "translate"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("translated: hello → 你好");
            }
        });

        CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                "translate", Map.of("text", "hello"), "翻译完成");

        ToolExecutor executor = new ToolExecutor(registry);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(spy)
                .toolExecutor(executor)
                .maxIterations(5)
                .build();

        List<StreamEvent> events = loop.executeWithToolsReactive(
                        List.of(ConversationMessage.builder()
                                .role(ConversationMessage.MessageRole.USER)
                                .textContent("translate hello")
                                .build()),
                        LLMOptions.builder().build())
                .collectList()
                .block();

        assertNotNull(events);

        StreamEvent toolResultEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TOOL_RESULT)
                .findFirst()
                .orElse(null);
        assertNotNull(toolResultEvent, "Must have TOOL_RESULT event");

        ToolResultChunk chunk = toolResultEvent.getChunk();
        assertNotNull(chunk, "TOOL_RESULT must have a chunk");
        assertEquals("translate", chunk.getToolName(), "chunk must carry the tool name");
        assertNotNull(chunk.getResult(), "chunk must carry the ToolResult");
        assertTrue(chunk.getResult().getContent().contains("translated"),
                "chunk result content must match tool output");
    }
}
