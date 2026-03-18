package com.lightweightai.kernel.core.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * 后处理器管道 - 将多个 StreamPostProcessor 按顺序组合
 *
 * 处理器按 {@link StreamPostProcessor#getOrder()} 排序执行（升序）。
 * 管道本身也实现 {@code Function<Flux<StreamEvent>, Flux<StreamEvent>>}，
 * 可以用 {@code flux.transform(pipeline)} 直接应用，也支持嵌套组合。
 *
 * 用法:
 * <pre>
 * StreamPostProcessorPipeline pipeline = StreamPostProcessorPipeline.builder()
 *     .add(new DeeplinkProcessor(resolver))
 *     .add(new RiskControlProcessor(checker, 50))
 *     .add(new CardAppendProcessor(cardProvider))
 *     .build();
 *
 * Flux&lt;StreamEvent&gt; processed = originalFlux.transform(pipeline);
 * </pre>
 */
public class StreamPostProcessorPipeline implements Function<Flux<StreamEvent>, Flux<StreamEvent>> {

    private final List<StreamPostProcessor> processors;

    private StreamPostProcessorPipeline(List<StreamPostProcessor> processors) {
        this.processors = processors;
    }

    @Override
    public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
        Flux<StreamEvent> current = source;
        for (StreamPostProcessor processor : processors) {
            current = current.transform(processor);
        }
        return current;
    }

    /**
     * 返回管道中的处理器列表（已排序，不可变）
     */
    public List<StreamPostProcessor> getProcessors() {
        return processors;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final List<StreamPostProcessor> processors = new ArrayList<>();

        public Builder add(StreamPostProcessor processor) {
            if (processor != null) {
                processors.add(processor);
            }
            return this;
        }

        public Builder addAll(List<StreamPostProcessor> processors) {
            if (processors != null) {
                for (StreamPostProcessor p : processors) {
                    add(p);
                }
            }
            return this;
        }

        public StreamPostProcessorPipeline build() {
            List<StreamPostProcessor> sorted = new ArrayList<>(processors);
            sorted.sort(Comparator.comparingInt(StreamPostProcessor::getOrder));
            return new StreamPostProcessorPipeline(List.copyOf(sorted));
        }
    }
}
