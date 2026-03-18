package com.lightweightai.kernel.core.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamPostProcessorPipeline 测试")
class StreamPostProcessorPipelineTest {

    @Test
    @DisplayName("空管道透传所有事件")
    void emptyPipelineShouldPassThrough() {
        StreamPostProcessorPipeline pipeline = StreamPostProcessorPipeline.builder().build();

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("hello"),
                StreamEvent.textDelta(" world")
        );

        StepVerifier.create(input.transform(pipeline))
                .expectNextMatches(e -> "hello".equals(e.getTextDelta()))
                .expectNextMatches(e -> " world".equals(e.getTextDelta()))
                .verifyComplete();
    }

    @Test
    @DisplayName("处理器按 order 排序执行")
    void shouldApplyProcessorsInOrder() {
        List<String> executionOrder = new ArrayList<>();

        StreamPostProcessor first = new NamedProcessor("first", 10, executionOrder);
        StreamPostProcessor second = new NamedProcessor("second", 20, executionOrder);
        StreamPostProcessor third = new NamedProcessor("third", 30, executionOrder);

        // 故意乱序添加
        StreamPostProcessorPipeline pipeline = StreamPostProcessorPipeline.builder()
                .add(third)
                .add(first)
                .add(second)
                .build();

        Flux<StreamEvent> input = Flux.just(StreamEvent.textDelta("test"));

        StepVerifier.create(input.transform(pipeline))
                .expectNextCount(1)
                .verifyComplete();

        assertEquals(List.of("first", "second", "third"), executionOrder);
    }

    @Test
    @DisplayName("getProcessors 返回已排序列表")
    void shouldReturnSortedProcessors() {
        StreamPostProcessor a = simpleProcessor("a", 300);
        StreamPostProcessor b = simpleProcessor("b", 100);
        StreamPostProcessor c = simpleProcessor("c", 200);

        StreamPostProcessorPipeline pipeline = StreamPostProcessorPipeline.builder()
                .add(a).add(b).add(c).build();

        List<StreamPostProcessor> processors = pipeline.getProcessors();
        assertEquals(3, processors.size());
        assertEquals("b", processors.get(0).getName());
        assertEquals("c", processors.get(1).getName());
        assertEquals("a", processors.get(2).getName());
    }

    @Test
    @DisplayName("addAll 批量添加处理器")
    void shouldAddAllProcessors() {
        StreamPostProcessor a = simpleProcessor("a", 100);
        StreamPostProcessor b = simpleProcessor("b", 200);

        StreamPostProcessorPipeline pipeline = StreamPostProcessorPipeline.builder()
                .addAll(List.of(a, b))
                .build();

        assertEquals(2, pipeline.getProcessors().size());
    }

    @Test
    @DisplayName("null 处理器被忽略")
    void shouldIgnoreNullProcessors() {
        StreamPostProcessorPipeline pipeline = StreamPostProcessorPipeline.builder()
                .add(null)
                .add(simpleProcessor("a", 100))
                .addAll(null)
                .build();

        assertEquals(1, pipeline.getProcessors().size());
    }

    @Test
    @DisplayName("管道可与 transform 组合使用")
    void shouldWorkWithTransform() {
        StreamPostProcessor upper = new StreamPostProcessor() {
            @Override
            public String getName() { return "upper"; }
            @Override
            public int getOrder() { return 100; }
            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
                return source.map(event -> {
                    if (event.getType() == StreamEvent.EventType.TEXT_DELTA) {
                        return StreamEvent.textDelta(event.getTextDelta().toUpperCase());
                    }
                    return event;
                });
            }
        };

        StreamPostProcessorPipeline pipeline = StreamPostProcessorPipeline.builder()
                .add(upper)
                .build();

        Flux<StreamEvent> input = Flux.just(StreamEvent.textDelta("hello"));

        StepVerifier.create(input.transform(pipeline))
                .expectNextMatches(e -> "HELLO".equals(e.getTextDelta()))
                .verifyComplete();
    }

    // 辅助：记录执行顺序的处理器
    private static class NamedProcessor implements StreamPostProcessor {
        private final String name;
        private final int order;
        private final List<String> executionLog;

        NamedProcessor(String name, int order, List<String> executionLog) {
            this.name = name;
            this.order = order;
            this.executionLog = executionLog;
        }

        @Override
        public String getName() { return name; }

        @Override
        public int getOrder() { return order; }

        @Override
        public Flux<StreamEvent> apply(Flux<StreamEvent> source) {
            return source.doOnSubscribe(s -> executionLog.add(name));
        }
    }

    private static StreamPostProcessor simpleProcessor(String name, int order) {
        return new StreamPostProcessor() {
            @Override
            public String getName() { return name; }
            @Override
            public int getOrder() { return order; }
            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> source) { return source; }
        };
    }
}
