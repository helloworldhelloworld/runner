package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.testsupport.CapturingAgentObserver;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 外到内 Acceptance：ToolCallingLoop 必须把 tool_use 的 args 以及工具执行结果
 * 一起传给 AgentObserver.onPostToolUse —— 这样观察者/日志/追踪才能把调用和结果对上号。
 *
 * 也断言 Flux 里出现一个非空的 TOOL_RESULT 事件（浏览器最终需要这个帧）。
 *
 * 红灯证据：修复前 ToolCallingLoop.java:321/323 传 Map.of()，postCalls.args 为空 Map。
 */
@DisplayName("Acceptance: ToolCallingLoop 把 tool_use 参数和结果一起传给 onPostToolUse")
class ToolCallingLoopPostToolUseArgsAcceptanceTest {

    @Test
    @DisplayName("onPostToolUse 收到的 args 与 LLM 发出的 args 一致，result 是工具实际返回")
    void postToolUseReceivesArgsAndResult() {
        Map<String, Object> llmArgs = Map.of("city", "Beijing", "unit", "celsius");

        ToolRegistry registry = new ToolRegistry();
        registry.register(new Tool() {
            @Override public String getName() { return "get_weather"; }
            @Override public String getDescription() { return "weather"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("sunny, 25C");
            }
        });

        CapturingLLMProvider llm = CapturingLLMProvider.toolThenEnd(
                "get_weather", llmArgs, "It's sunny.");
        CapturingAgentObserver observer = new CapturingAgentObserver();
        ToolExecutor executor = new ToolExecutor(registry);

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(llm)
                .toolExecutor(executor)
                .observers(List.of(observer))
                .maxIterations(5)
                .build();

        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.USER)
                        .textContent("weather in beijing?")
                        .build());

        List<StreamEvent> events = loop.executeWithToolsReactive(messages, LLMOptions.builder().build())
                .collectList()
                .block();

        // 1. PostToolUse 必须携带 args（修复前这里是空 Map → 红灯）
        assertEquals(1, observer.postCalls().size(), "PostToolUse should fire exactly once");
        CapturingAgentObserver.ToolInvocation post = observer.postCalls().get(0);
        assertEquals("get_weather", post.name());
        assertEquals(llmArgs, post.args(),
                "PostToolUse 收到的 args 必须等于 LLM 发出的 args（修复前这里是 Map.of()）");

        // 2. PostToolUse 的 result 必须是工具实际输出
        assertNotNull(post.result(), "PostToolUse result 不能为空");
        String content = post.result().getContent();
        assertTrue(content != null && content.contains("sunny, 25C"),
                "PostToolUse result 应含工具实际输出，实际: " + content);

        // 3. PreToolUse 的 args 本来就对，顺便也断一下以确认 LLM 参数真传下来了
        assertEquals(1, observer.preCalls().size());
        assertEquals(llmArgs, observer.preCalls().get(0).args());

        // 4. Flux 里应有 TOOL_RESULT 事件且 chunk 带结果内容
        assertNotNull(events, "events must not be null");
        StreamEvent toolResult = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TOOL_RESULT)
                .findFirst()
                .orElse(null);
        assertNotNull(toolResult, "必须有 TOOL_RESULT 事件");
        assertNotNull(toolResult.getChunk(), "TOOL_RESULT 事件 chunk 不能为 null");
        assertEquals("get_weather", toolResult.getChunk().getToolName());
        assertNotNull(toolResult.getChunk().getResult(),
                "TOOL_RESULT.chunk.result 不能为 null");
    }
}
