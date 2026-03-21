package com.lightweightai.web.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ToolCall;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TracingPostProcessor 调用链追踪测试")
class TracingPostProcessorTest {

    @Test
    @DisplayName("普通文本流应注入 first_token trace")
    void shouldEmitFirstTokenTrace() {
        TracingPostProcessor processor = new TracingPostProcessor();

        Flux<StreamEvent> input = Flux.just(
            StreamEvent.textDelta("Hello"),
            StreamEvent.textDelta(" World")
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertNotNull(result);

        List<StreamEvent> traces = result.stream()
            .filter(e -> e.getType() == StreamEvent.EventType.TRACE)
            .toList();

        // 应有 first_token trace
        assertTrue(traces.stream().anyMatch(t -> "llm.first_token".equals(t.getTracePhase())),
            "Should have first_token trace");

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
    @DisplayName("工具调用应注入 tool.start trace")
    void shouldEmitToolStartTrace() {
        TracingPostProcessor processor = new TracingPostProcessor();

        ToolCall toolCall = new ToolCall("tc1", "web_search", java.util.Map.of());
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
    }

    @Test
    @DisplayName("LLM_COMPLETE 应注入 llm.complete trace")
    void shouldEmitLlmCompleteTrace() {
        TracingPostProcessor processor = new TracingPostProcessor();

        LLMResponse response = LLMResponse.builder()
            .message(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.ASSISTANT)
                .textContent("done").build())
            .build();

        Flux<StreamEvent> input = Flux.just(
            StreamEvent.textDelta("text"),
            StreamEvent.llmComplete(response)
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();

        assertTrue(result.stream().anyMatch(
            e -> e.getType() == StreamEvent.EventType.TRACE
                && "llm.complete".equals(e.getTracePhase())));
    }

    @Test
    @DisplayName("非关键事件不注入额外 trace")
    void shouldNotInjectTraceForNonKeyEvents() {
        TracingPostProcessor processor = new TracingPostProcessor();

        // TOOL_PROGRESS 不需要额外 trace
        Flux<StreamEvent> input = Flux.just(
            StreamEvent.toolProgress(null)
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        // 只有原始事件，没有 trace
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
        // 应该是: TRACE(tool.start) → TOOL_CALL_START
        assertEquals(2, result.size());
        assertEquals(StreamEvent.EventType.TRACE, result.get(0).getType());
        assertEquals(StreamEvent.EventType.TOOL_CALL_START, result.get(1).getType());
    }

    @Test
    @DisplayName("处理器名称和优先级正确")
    void shouldHaveCorrectNameAndOrder() {
        TracingPostProcessor processor = new TracingPostProcessor();
        assertEquals("tracing", processor.getName());
        // 应该在所有业务处理器之后（order 大）
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
}
