package com.lightweightai.web.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TracingPostProcessor 调用链追踪测试")
class TracingPostProcessorTest {

    @Test
    @DisplayName("普通文本流应注入 first_token trace 并携带延迟数据")
    void shouldEmitFirstTokenTraceWithLatency() {
        TracingPostProcessor processor = new TracingPostProcessor();

        Flux<StreamEvent> input = Flux.just(
            StreamEvent.textDelta("Hello"),
            StreamEvent.textDelta(" World")
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertNotNull(result);

        List<StreamEvent> traces = result.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TRACE
                && "llm.first_token".equals(e.getTracePhase()))
            .toList();

        assertEquals(1, traces.size(), "Should have exactly one first_token trace");
        // data 应包含 latencyMs
        assertNotNull(traces.get(0).getData());
        assertTrue(traces.get(0).getData().containsKey("latencyMs"));

        // 原始事件应保留
        List<StreamEvent> textEvents = result.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA)
            .toList();
        assertEquals(2, textEvents.size());
        assertEquals("Hello", textEvents.get(0).getTextDelta());
        assertEquals(" World", textEvents.get(1).getTextDelta());
    }

    @Test
    @DisplayName("first_token trace 只出现一次")
    void shouldEmitFirstTokenTraceOnlyOnce() {
        TracingPostProcessor processor = new TracingPostProcessor();

        Flux<StreamEvent> input = Flux.just(
            StreamEvent.textDelta("A"),
            StreamEvent.textDelta("B"),
            StreamEvent.textDelta("C")
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        long traceCount = result.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TRACE
                && "llm.first_token".equals(e.getTracePhase()))
            .count();
        assertEquals(1, traceCount, "first_token trace should appear exactly once");
    }

    @Test
    @DisplayName("首个 SPEAKABLE_CHUNK 应注入 speakable.first_chunk trace 并携带延迟，仅一次")
    void shouldEmitFirstSpeakableChunkTraceWithLatency() {
        TracingPostProcessor processor = new TracingPostProcessor();

        Flux<StreamEvent> input = Flux.just(
            StreamEvent.textDelta("你好。"),
            StreamEvent.speakableChunk("你好。", 0, "neutral"),
            StreamEvent.speakableChunk("再见！", 1, "neutral")
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertNotNull(result);

        List<StreamEvent> traces = result.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TRACE
                && "speakable.first_chunk".equals(e.getTracePhase()))
            .toList();

        assertEquals(1, traces.size(), "speakable.first_chunk 只在首个可说块时注入一次");
        assertNotNull(traces.get(0).getData());
        assertTrue(traces.get(0).getData().containsKey("latencyMs"),
            "应携带从订阅起算的 chunk_form 延迟");
    }

    @Test
    @DisplayName("工具调用应注入 tool.start trace 并携带参数")
    void shouldEmitToolStartTraceWithArguments() {
        TracingPostProcessor processor = new TracingPostProcessor();

        Map<String, Object> args = Map.of("query", "天气", "city", "北京");
        ToolCall toolCall = new ToolCall("tc1", "web_search", args);
        Flux<StreamEvent> input = Flux.just(
            StreamEvent.textDelta("Let me search"),
            StreamEvent.toolCallStart(toolCall)
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();

        List<StreamEvent> toolTraces = result.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TRACE
                && "tool.start".equals(e.getTracePhase()))
            .toList();
        assertEquals(1, toolTraces.size());
        assertTrue(toolTraces.get(0).getTraceMessage().contains("web_search"));

        // data 应包含工具名和参数
        Map<String, Object> data = toolTraces.get(0).getData();
        assertNotNull(data);
        assertEquals("web_search", data.get("toolName"));
        assertEquals("tc1", data.get("toolCallId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> traceArgs = (Map<String, Object>) data.get("arguments");
        assertEquals("天气", traceArgs.get("query"));
        assertEquals("北京", traceArgs.get("city"));
    }

    @Test
    @DisplayName("工具结果应注入 tool.result trace 并携带返回内容")
    void shouldEmitToolResultTraceWithContent() {
        TracingPostProcessor processor = new TracingPostProcessor();

        ToolResult toolResult = ToolResult.success("tc1", "北京今天晴天，25°C");
        ToolResultChunk chunk = ToolResultChunk.complete("web_search", toolResult);

        Flux<StreamEvent> input = Flux.just(StreamEvent.toolResult(chunk));

        List<StreamEvent> result = input.transform(processor).collectList().block();

        List<StreamEvent> toolTraces = result.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TRACE
                && "tool.result".equals(e.getTracePhase()))
            .toList();
        assertEquals(1, toolTraces.size());

        Map<String, Object> data = toolTraces.get(0).getData();
        assertNotNull(data);
        assertEquals("web_search", data.get("toolName"));
        assertEquals("北京今天晴天，25°C", data.get("content"));
        assertEquals(false, data.get("isError"));
    }

    @Test
    @DisplayName("LLM_COMPLETE 应注入 llm.complete trace 并携带模型输出")
    void shouldEmitLlmCompleteTraceWithOutput() {
        TracingPostProcessor processor = new TracingPostProcessor();

        LLMResponse response = LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("done").build())
            .build();

        Flux<StreamEvent> input = Flux.just(
            StreamEvent.textDelta("Hello"),
            StreamEvent.textDelta(" World"),
            StreamEvent.llmComplete(response)
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();

        List<StreamEvent> completeTraces = result.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TRACE
                && "llm.complete".equals(e.getTracePhase()))
            .toList();
        assertEquals(1, completeTraces.size());

        Map<String, Object> data = completeTraces.get(0).getData();
        assertNotNull(data);
        assertEquals("Hello World", data.get("output"));
        assertEquals(false, data.get("hasToolCalls"));
        assertTrue(((Long) data.get("elapsedMs")) >= 0);
    }

    @Test
    @DisplayName("工具错误应注入 tool.error trace 并携带错误信息")
    void shouldEmitToolErrorTraceWithMessage() {
        TracingPostProcessor processor = new TracingPostProcessor();

        ToolResultChunk errorChunk = ToolResultChunk.error("web_search", "Connection timeout");
        Flux<StreamEvent> input = Flux.just(StreamEvent.toolError(errorChunk));

        List<StreamEvent> result = input.transform(processor).collectList().block();

        List<StreamEvent> errorTraces = result.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TRACE
                && "tool.error".equals(e.getTracePhase()))
            .toList();
        assertEquals(1, errorTraces.size());

        Map<String, Object> data = errorTraces.get(0).getData();
        assertNotNull(data);
        assertEquals("web_search", data.get("toolName"));
        assertEquals("Connection timeout", data.get("errorMessage"));
    }

    @Test
    @DisplayName("上游 TRACE 事件直接透传不再包装")
    void shouldPassThroughUpstreamTraceEvents() {
        TracingPostProcessor processor = new TracingPostProcessor();

        StreamEvent userTrace = StreamEvent.trace("user.message", "你好",
            Map.of("sessionId", "s1"));

        Flux<StreamEvent> input = Flux.just(userTrace);

        List<StreamEvent> result = input.transform(processor).collectList().block();

        assertEquals(1, result.size());
        assertEquals(StreamEvent.EventType.TRACE, result.get(0).getType());
        assertEquals("user.message", result.get(0).getTracePhase());
        assertEquals("你好", result.get(0).getTraceMessage());
        assertEquals("s1", result.get(0).getData().get("sessionId"));
    }

    @Test
    @DisplayName("非关键事件不注入额外 trace")
    void shouldNotInjectTraceForNonKeyEvents() {
        TracingPostProcessor processor = new TracingPostProcessor();

        Flux<StreamEvent> input = Flux.just(
            StreamEvent.toolProgress(null)
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("事件顺序：trace 在原始事件之前")
    void traceEventShouldPrecedeOriginalEvent() {
        TracingPostProcessor processor = new TracingPostProcessor();

        ToolCall toolCall = new ToolCall("tc2", "get_time", java.util.Map.of());
        Flux<StreamEvent> input = Flux.just(
            StreamEvent.toolCallStart(toolCall)
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertEquals(2, result.size());
        assertEquals(StreamEvent.EventType.TRACE, result.get(0).getType());
        assertEquals(StreamEvent.EventType.TOOL_CALL_START, result.get(1).getType());
    }

    @Test
    @DisplayName("处理器名称和优先级正确")
    void shouldHaveCorrectNameAndOrder() {
        TracingPostProcessor processor = new TracingPostProcessor();
        assertEquals("tracing", processor.getName());
        assertTrue(processor.getOrder() >= 900);
    }

    @Test
    @DisplayName("空流不崩溃")
    void shouldHandleEmptyFlux() {
        TracingPostProcessor processor = new TracingPostProcessor();
        List<StreamEvent> result = Flux.<StreamEvent>empty()
            .transform(processor).collectList().block();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("完整调用链：user → model output → tool → result → complete")
    void shouldTraceFullCallChain() {
        TracingPostProcessor processor = new TracingPostProcessor();

        StreamEvent userTrace = StreamEvent.trace("user.message", "查天气",
            Map.of("sessionId", "s1"));
        ToolCall toolCall = new ToolCall("tc1", "web_search", Map.of("query", "天气"));
        ToolResult toolResult = ToolResult.success("tc1", "晴天 25°C");
        ToolResultChunk resultChunk = ToolResultChunk.complete("web_search", toolResult);
        LLMResponse response = LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("今天晴天").build())
            .build();

        Flux<StreamEvent> input = Flux.just(
            userTrace,
            StreamEvent.textDelta("今天"),
            StreamEvent.toolCallStart(toolCall),
            StreamEvent.toolResult(resultChunk),
            StreamEvent.textDelta("晴天"),
            StreamEvent.llmComplete(response)
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();

        List<String> tracePhases = result.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TRACE)
            .map(StreamEvent::getTracePhase)
            .toList();

        assertEquals(List.of("user.message", "llm.first_token", "tool.start",
            "tool.result", "llm.complete"), tracePhases);

        // 验证 llm.complete 的 output 累积了所有 TEXT_DELTA
        StreamEvent completeTrace = result.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TRACE
                && "llm.complete".equals(e.getTracePhase()))
            .findFirst().orElseThrow();
        assertEquals("今天晴天", completeTrace.getData().get("output"));
    }
}
