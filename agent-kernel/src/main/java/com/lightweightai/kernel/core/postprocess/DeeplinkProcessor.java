package com.lightweightai.kernel.core.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Deeplink 处理器 - 将文本中的关键词/地标转换为 deeplink
 *
 * 处理流式分片的挑战：关键词可能跨越多个 TEXT_DELTA 边界。
 * 使用 buffer 在词边界处安全地进行替换。
 */
public class DeeplinkProcessor implements StreamPostProcessor {

    private final DeeplinkResolver resolver;

    public DeeplinkProcessor(DeeplinkResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public String getName() {
        return "deeplink";
    }

    @Override
    public int getOrder() {
        return 50;
    }

    @Override
    public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
        AtomicReference<String> buffer = new AtomicReference<>("");

        return source.concatMap(event -> {
            if (event.getType() != StreamEvent.EventType.TEXT_DELTA) {
                // 非文本事件：先 flush buffer，再透传
                String remaining = buffer.getAndSet("");
                if (remaining.isEmpty()) {
                    return Flux.just(event);
                }
                String resolved = resolver.resolve(remaining);
                return Flux.just(StreamEvent.textDelta(resolved), event);
            }

            String text = buffer.get() + event.getTextDelta();
            int lastBoundary = findLastWordBoundary(text);
            if (lastBoundary <= 0) {
                buffer.set(text);
                return Flux.empty();
            }

            String safe = text.substring(0, lastBoundary);
            buffer.set(text.substring(lastBoundary));
            String resolved = resolver.resolve(safe);
            return Flux.just(StreamEvent.textDelta(resolved));
        }).concatWith(Flux.defer(() -> {
            String remaining = buffer.getAndSet("");
            if (remaining.isEmpty()) {
                return Flux.empty();
            }
            return Flux.just(StreamEvent.textDelta(resolver.resolve(remaining)));
        }));
    }

    /**
     * 查找文本中最后一个词边界的位置
     */
    static int findLastWordBoundary(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\n' || c == '\t' || c == '。' || c == '，'
                    || c == '、' || c == '！' || c == '？' || c == '.' || c == ','
                    || c == '!' || c == '?' || c == ';' || c == ':' || c == '；' || c == '：') {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * Deeplink 解析接口
     */
    @FunctionalInterface
    public interface DeeplinkResolver {
        String resolve(String text);
    }
}
