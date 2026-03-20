package com.lightweightai.web.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CardAppendProcessor 测试")
class CardAppendProcessorTest {

    @Test
    @DisplayName("流结束后追加卡片事件")
    void shouldAppendCardsAfterStream() {
        CardAppendProcessor processor = new CardAppendProcessor(
                fullText -> List.of(
                        new CardAppendProcessor.Card("recommendation", "相关文章", "/article/1", null),
                        new CardAppendProcessor.Card("action", "查看更多", "/more", Map.of("icon", "arrow"))
                )
        );

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("Hello "),
                StreamEvent.textDelta("world")
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();

        assertNotNull(result);
        assertEquals(4, result.size());

        // 前两个是原始 TEXT_DELTA
        assertEquals(StreamEvent.EventType.TEXT_DELTA, result.get(0).getType());
        assertEquals(StreamEvent.EventType.TEXT_DELTA, result.get(1).getType());

        // 后两个是 POST_PROCESS_DATA 卡片
        StreamEvent card1 = result.get(2);
        assertEquals(StreamEvent.EventType.POST_PROCESS_DATA, card1.getType());
        assertEquals("card", card1.getCategory());
        assertEquals("recommendation", card1.getData().get("type"));
        assertEquals("相关文章", card1.getData().get("title"));
        assertEquals("/article/1", card1.getData().get("url"));

        StreamEvent card2 = result.get(3);
        assertEquals("action", card2.getData().get("type"));
        assertEquals("arrow", card2.getData().get("icon"));
    }

    @Test
    @DisplayName("无卡片时不追加任何事件")
    void shouldNotAppendWhenNoCards() {
        CardAppendProcessor processor = new CardAppendProcessor(
                fullText -> Collections.emptyList()
        );

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("no cards here")
        );

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(StreamEvent.EventType.TEXT_DELTA, result.get(0).getType());
    }

    @Test
    @DisplayName("null 卡片列表时不追加")
    void shouldHandleNullCardList() {
        CardAppendProcessor processor = new CardAppendProcessor(fullText -> null);

        Flux<StreamEvent> input = Flux.just(StreamEvent.textDelta("test"));

        List<StreamEvent> result = input.transform(processor).collectList().block();
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("累积完整文本传给 CardProvider")
    void shouldAccumulateFullText() {
        StringBuilder captured = new StringBuilder();

        CardAppendProcessor processor = new CardAppendProcessor(fullText -> {
            captured.append(fullText);
            return Collections.emptyList();
        });

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("Hello "),
                StreamEvent.toolCallStart(null),  // 非文本事件不累积
                StreamEvent.textDelta("world!")
        );

        input.transform(processor).collectList().block();

        assertEquals("Hello world!", captured.toString());
    }

    @Test
    @DisplayName("Card.toMap 包含所有字段")
    void cardToMapShouldContainAllFields() {
        CardAppendProcessor.Card card = new CardAppendProcessor.Card(
                "recommendation", "Title", "/url", Map.of("extra", "value")
        );

        Map<String, Object> map = card.toMap();
        assertEquals("recommendation", map.get("type"));
        assertEquals("Title", map.get("title"));
        assertEquals("/url", map.get("url"));
        assertEquals("value", map.get("extra"));
    }

    @Test
    @DisplayName("处理器名称和优先级正确")
    void shouldHaveCorrectNameAndOrder() {
        CardAppendProcessor processor = new CardAppendProcessor(t -> Collections.emptyList());
        assertEquals("card-append", processor.getName());
        assertEquals(900, processor.getOrder());
    }
}
