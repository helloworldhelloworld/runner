package com.lightweightai.kernel.core.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 卡片追加处理器 - 在流结束后追加推荐/关联内容卡片
 *
 * 模式：累积文本 → 流结束后生成卡片 → concatWith 追加
 */
public class CardAppendProcessor implements StreamPostProcessor {

    private final CardProvider cardProvider;

    public CardAppendProcessor(CardProvider cardProvider) {
        this.cardProvider = cardProvider;
    }

    @Override
    public String getName() {
        return "card-append";
    }

    @Override
    public int getOrder() {
        return 900;
    }

    @Override
    public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
        StringBuilder accumulated = new StringBuilder();

        return source
                .doOnNext(event -> {
                    if (event.getType() == StreamEvent.EventType.TEXT_DELTA && event.getTextDelta() != null) {
                        accumulated.append(event.getTextDelta());
                    }
                })
                .concatWith(Flux.defer(() -> {
                    List<Card> cards = cardProvider.getCards(accumulated.toString());
                    if (cards == null || cards.isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(cards)
                            .map(card -> StreamEvent.postProcessData("card", card.toMap()));
                }));
    }

    /**
     * 卡片生成接口
     */
    @FunctionalInterface
    public interface CardProvider {
        List<Card> getCards(String fullText);
    }

    /**
     * 卡片数据
     */
    public static class Card {
        private final String type;
        private final String title;
        private final String url;
        private final Map<String, Object> extra;

        public Card(String type, String title, String url, Map<String, Object> extra) {
            this.type = type;
            this.title = title;
            this.url = url;
            this.extra = extra != null ? extra : Collections.emptyMap();
        }

        public String getType() { return type; }
        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public Map<String, Object> getExtra() { return extra; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("type", type);
            map.put("title", title);
            map.put("url", url);
            if (!extra.isEmpty()) {
                map.putAll(extra);
            }
            return map;
        }
    }
}
