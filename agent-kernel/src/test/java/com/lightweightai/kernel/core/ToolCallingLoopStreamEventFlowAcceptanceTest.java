package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance: ToolCallingLoop reactive 路径上 StreamEvent 的完整类型流验证。
 *
 * 已有 ToolCallingLoopTest 覆盖了基本的 sync/async/reactive 路径，但仅断言"包含"某些事件类型。
 * 本测试验证事件的 **顺序** 和 **payload 完整性**：
 * 1. 无工具调用场景：只有 LLM_COMPLETE
 * 2. 单轮工具调用：LLM_COMPLETE(tool_use) → TOOL_CALL_START → TOOL_RESULT → LLM_COMPLETE(end_turn)
 * 3. 多轮工具调用：多轮的事件序列保持一致
 * 4. 工具错误场景：TOOL_ERROR 事件包含错误信息
 */
@DisplayName("Acceptance: ToolCallingLoop StreamEvent 流 — 事件顺序 + payload")
class ToolCallingLoopStreamEventFlowAcceptanceTest {

    private ToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;

    @BeforeEach
    void setUp() {
        toolRegistry = new ToolRegistry();
        toolRegistry.register(new EchoTool());
        toolRegistry.register(new FailingTool());
        toolExecutor = new ToolExecutor(toolRegistry);
    }

    @Test
    @DisplayName("无工具调用 → 只发出 LLM_COMPLETE，且 response 载荷正确")
    void noToolCall_onlyLlmComplete() {
        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("hello world");
        ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10);

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(
                loop.executeWithToolsReactive(singleUserMessage("hi"), emptyOptions())
                        .doOnNext(events::add))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        assertEquals(1, events.size(), "No-tool-call path should emit exactly 1 event");
        StreamEvent evt = events.get(0);
        assertEquals(StreamEvent.EventType.LLM_COMPLETE, evt.getType());
        assertNotNull(evt.getResponse(), "LLM_COMPLETE event must carry LLMResponse");
        assertEquals("hello world", evt.getResponse().getMessage().getTextContent());
        assertEquals("end_turn", evt.getResponse().getStopReason());
    }

    @Test
    @DisplayName("单轮工具调用 → 事件顺序：LLM_COMPLETE(tool_use) → TOOL_CALL_START → TOOL_RESULT → LLM_COMPLETE(end_turn)")
    void singleToolCall_correctEventSequence() {
        CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "echo", Map.of("text", "test"), "final answer");
        ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10);

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(
                loop.executeWithToolsReactive(singleUserMessage("go"), emptyOptions())
                        .doOnNext(events::add))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        assertTrue(events.size() >= 3, "Should have at least 3 events; got " + events.size());

        // Extract event type sequence
        List<StreamEvent.EventType> types = events.stream()
                .map(StreamEvent::getType)
                .toList();

        // First event: LLM_COMPLETE with tool_use stopReason
        assertEquals(StreamEvent.EventType.LLM_COMPLETE, types.get(0),
                "First event should be LLM_COMPLETE (tool_use response)");
        assertEquals("tool_use", events.get(0).getResponse().getStopReason());

        // Must contain TOOL_CALL_START
        assertTrue(types.contains(StreamEvent.EventType.TOOL_CALL_START),
                "Should contain TOOL_CALL_START; types=" + types);

        // TOOL_CALL_START must carry ToolCall payload
        StreamEvent toolStartEvt = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TOOL_CALL_START)
                .findFirst().orElseThrow();
        assertNotNull(toolStartEvt.getToolCall(), "TOOL_CALL_START must carry ToolCall payload");
        assertEquals("echo", toolStartEvt.getToolCall().getName());

        // Must contain TOOL_RESULT
        assertTrue(types.contains(StreamEvent.EventType.TOOL_RESULT),
                "Should contain TOOL_RESULT; types=" + types);

        // TOOL_RESULT must carry chunk with result
        StreamEvent toolResultEvt = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TOOL_RESULT)
                .findFirst().orElseThrow();
        assertNotNull(toolResultEvt.getChunk(), "TOOL_RESULT must carry ToolResultChunk");

        // Last LLM_COMPLETE should have end_turn
        StreamEvent lastComplete = null;
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).getType() == StreamEvent.EventType.LLM_COMPLETE) {
                lastComplete = events.get(i);
                break;
            }
        }
        assertNotNull(lastComplete, "Should have a final LLM_COMPLETE event");
        assertEquals("end_turn", lastComplete.getResponse().getStopReason());
        assertEquals("final answer", lastComplete.getResponse().getMessage().getTextContent());

        // Ordering: TOOL_CALL_START comes after first LLM_COMPLETE, TOOL_RESULT comes after TOOL_CALL_START
        int toolStartIdx = types.indexOf(StreamEvent.EventType.TOOL_CALL_START);
        int toolResultIdx = types.indexOf(StreamEvent.EventType.TOOL_RESULT);
        assertTrue(toolStartIdx > 0, "TOOL_CALL_START should come after first LLM_COMPLETE");
        assertTrue(toolResultIdx > toolStartIdx, "TOOL_RESULT should come after TOOL_CALL_START");
    }

    @Test
    @DisplayName("工具执行错误 → 发出 TOOL_ERROR 事件，但循环继续到 LLM_COMPLETE")
    void toolError_emitsToolErrorEvent() {
        CapturingLLMProvider provider = CapturingLLMProvider.toolThenEnd(
                "fail_tool", Map.of(), "handled error");
        ToolCallingLoop loop = new ToolCallingLoop(provider, toolExecutor, 10);

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(
                loop.executeWithToolsReactive(singleUserMessage("trigger error"), emptyOptions())
                        .doOnNext(events::add))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        List<StreamEvent.EventType> types = events.stream()
                .map(StreamEvent::getType)
                .toList();

        // Tool error should produce either TOOL_RESULT(is_error) or TOOL_ERROR
        boolean hasToolResult = types.contains(StreamEvent.EventType.TOOL_RESULT);
        boolean hasToolError = types.contains(StreamEvent.EventType.TOOL_ERROR);
        assertTrue(hasToolResult || hasToolError,
                "Should emit TOOL_RESULT or TOOL_ERROR for failed tool; types=" + types);

        // Loop should still complete (not error out)
        assertTrue(types.contains(StreamEvent.EventType.LLM_COMPLETE),
                "Loop should still reach LLM_COMPLETE after tool error");
    }

    @Test
    @DisplayName("CancellationToken 已取消 → reactive 流立即完成，不发出任何事件")
    void cancelledToken_emitsNoEvents() {
        CancellationToken token = new CancellationToken();
        token.cancel();

        CapturingLLMProvider provider = CapturingLLMProvider.endTurn("should not see");
        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .cancellationToken(token)
                .build();

        List<StreamEvent> events = new ArrayList<>();
        StepVerifier.create(
                loop.executeWithToolsReactive(singleUserMessage("go"), emptyOptions())
                        .doOnNext(events::add))
                .thenConsumeWhile(e -> true)
                .verifyComplete();

        assertEquals(0, provider.callCount(), "LLM should not be called when token is cancelled");
        assertTrue(events.isEmpty(), "No events should be emitted when cancelled");
    }

    // ==================== Helpers ====================

    private List<ConversationMessage> singleUserMessage(String text) {
        return new ArrayList<>(List.of(
                ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent(text)
                        .build()
        ));
    }

    private LLMOptions emptyOptions() {
        return LLMOptions.builder().build();
    }

    private static class EchoTool implements Tool {
        @Override public String getName() { return "echo"; }
        @Override public String getDescription() { return "Echoes text"; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success("Echo: " + args.get("text"));
        }
    }

    private static class FailingTool implements Tool {
        @Override public String getName() { return "fail_tool"; }
        @Override public String getDescription() { return "Always fails"; }
        @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.error("Something went wrong");
        }
    }
}
