package com.lightweightai.web.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeeplinkProcessor 测试")
class DeeplinkProcessorTest {

    private final DeeplinkProcessor.DeeplinkResolver resolver = text ->
            text.replace("Tokyo Tower", "[Tokyo Tower](app://place/tokyo-tower)")
                .replace("东京塔", "[东京塔](app://place/tokyo-tower)");

    @Test
    @DisplayName("单个 delta 中的关键词替换")
    void shouldReplaceKeywordsInSingleDelta() {
        DeeplinkProcessor processor = new DeeplinkProcessor(resolver);

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("Visit Tokyo Tower today!")
        );

        String fullText = collectText(input.transform(processor));
        assertTrue(fullText.contains("[Tokyo Tower](app://place/tokyo-tower)"));
        assertTrue(fullText.contains("Visit "));
        assertTrue(fullText.contains(" today!"));
    }

    @Test
    @DisplayName("跨 delta 边界的关键词替换")
    void shouldHandleCrossDeltaKeywords() {
        DeeplinkProcessor processor = new DeeplinkProcessor(resolver);

        // "Tokyo Tower" 被分到两个 delta 中
        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("Visit Tokyo"),
                StreamEvent.textDelta(" Tower today!")
        );

        String fullText = collectText(input.transform(processor));
        assertTrue(fullText.contains("[Tokyo Tower](app://place/tokyo-tower)"),
                "Expected deeplink in: " + fullText);
    }

    @Test
    @DisplayName("非 TEXT_DELTA 事件透传")
    void shouldPassThroughNonTextEvents() {
        DeeplinkProcessor processor = new DeeplinkProcessor(resolver);

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("Hello, "),
                StreamEvent.toolCallStart(null),
                StreamEvent.textDelta("world!")
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertNotNull(result);

        // 确认 TOOL_CALL_START 事件存在
        boolean hasToolCall = result.stream()
                .anyMatch(e -> e.getType() == StreamEvent.EventType.TOOL_CALL_START);
        assertTrue(hasToolCall);
    }

    @Test
    @DisplayName("无匹配时文本不变")
    void shouldNotModifyTextWithoutMatches() {
        DeeplinkProcessor processor = new DeeplinkProcessor(resolver);

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("No landmarks here.")
        );

        String fullText = collectText(input.transform(processor));
        assertEquals("No landmarks here.", fullText);
    }

    @Test
    @DisplayName("中文关键词替换")
    void shouldHandleChineseKeywords() {
        DeeplinkProcessor processor = new DeeplinkProcessor(resolver);

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("去看东京塔吧！")
        );

        String fullText = collectText(input.transform(processor));
        assertTrue(fullText.contains("[东京塔](app://place/tokyo-tower)"));
    }

    @Test
    @DisplayName("处理器名称和优先级正确")
    void shouldHaveCorrectNameAndOrder() {
        DeeplinkProcessor processor = new DeeplinkProcessor(resolver);
        assertEquals("deeplink", processor.getName());
        assertEquals(50, processor.getOrder());
    }

    @Test
    @DisplayName("findLastWordBoundary 正确找到词边界")
    void shouldFindWordBoundary() {
        assertEquals(6, DeeplinkProcessor.findLastWordBoundary("hello world"));
        assertEquals(6, DeeplinkProcessor.findLastWordBoundary("hello.world"));
        assertEquals(3, DeeplinkProcessor.findLastWordBoundary("你好，世界"));
        assertEquals(-1, DeeplinkProcessor.findLastWordBoundary("nospace"));
    }

    private String collectText(Flux<StreamEvent> flux) {
        List<StreamEvent> events = flux.collectList().block();
        assertNotNull(events);
        return events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.TEXT_DELTA)
                .map(StreamEvent::getTextDelta)
                .collect(Collectors.joining());
    }
}
