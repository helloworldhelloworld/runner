package com.lightweightai.kernel.core.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StreamPostProcessor — composable stream transformation")
class StreamPostProcessorTest {

    @Test
    @DisplayName("processor can transform TEXT_DELTA content")
    void transformsTextDelta() {
        StreamPostProcessor uppercaser = new StreamPostProcessor() {
            @Override public String getName() { return "uppercaser"; }
            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> flux) {
                return flux.map(event -> {
                    if (event.getType() == StreamEvent.EventType.TEXT_DELTA) {
                        return StreamEvent.textDelta(event.getTextDelta().toUpperCase());
                    }
                    return event;
                });
            }
        };

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("hello"),
                StreamEvent.textDelta(" world")
        );

        StepVerifier.create(uppercaser.apply(input))
                .assertNext(e -> assertEquals("HELLO", e.getTextDelta()))
                .assertNext(e -> assertEquals(" WORLD", e.getTextDelta()))
                .verifyComplete();
    }

    @Test
    @DisplayName("processors compose via Flux.transform chaining")
    void processorsComposeViaTransform() {
        StreamPostProcessor prefixer = new StreamPostProcessor() {
            @Override public String getName() { return "prefixer"; }
            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> flux) {
                return Flux.concat(
                        Flux.just(StreamEvent.textDelta("[PREFIX] ")),
                        flux
                );
            }
        };

        StreamPostProcessor suffixer = new StreamPostProcessor() {
            @Override public String getName() { return "suffixer"; }
            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> flux) {
                return Flux.concat(
                        flux,
                        Flux.just(StreamEvent.textDelta(" [SUFFIX]"))
                );
            }
        };

        Flux<StreamEvent> input = Flux.just(StreamEvent.textDelta("content"));

        Flux<StreamEvent> result = input
                .transform(prefixer)
                .transform(suffixer);

        List<String> texts = new ArrayList<>();
        StepVerifier.create(result)
                .thenConsumeWhile(e -> {
                    if (e.getType() == StreamEvent.EventType.TEXT_DELTA) {
                        texts.add(e.getTextDelta());
                    }
                    return true;
                })
                .verifyComplete();

        assertEquals("[PREFIX] ", texts.get(0));
        assertEquals("content", texts.get(1));
        assertEquals(" [SUFFIX]", texts.get(2));
    }

    @Test
    @DisplayName("processor can filter/suppress events")
    void filtersSuppressesEvents() {
        StreamPostProcessor filter = new StreamPostProcessor() {
            @Override public String getName() { return "error-filter"; }
            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> flux) {
                return flux.filter(e -> e.getType() != StreamEvent.EventType.ERROR);
            }
        };

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("ok"),
                StreamEvent.error(new RuntimeException("hidden")),
                StreamEvent.textDelta("also ok")
        );

        StepVerifier.create(filter.apply(input))
                .assertNext(e -> assertEquals("ok", e.getTextDelta()))
                .assertNext(e -> assertEquals("also ok", e.getTextDelta()))
                .verifyComplete();
    }

    @Test
    @DisplayName("getOrder defaults to 100")
    void defaultOrderIs100() {
        StreamPostProcessor processor = new StreamPostProcessor() {
            @Override public String getName() { return "default"; }
            @Override public Flux<StreamEvent> apply(Flux<StreamEvent> flux) { return flux; }
        };

        assertEquals(100, processor.getOrder());
    }

    @Test
    @DisplayName("processors sort by order for execution priority")
    void processorsSortByOrder() {
        StreamPostProcessor first = new StreamPostProcessor() {
            @Override public String getName() { return "first"; }
            @Override public int getOrder() { return 10; }
            @Override public Flux<StreamEvent> apply(Flux<StreamEvent> flux) { return flux; }
        };

        StreamPostProcessor second = new StreamPostProcessor() {
            @Override public String getName() { return "second"; }
            @Override public int getOrder() { return 50; }
            @Override public Flux<StreamEvent> apply(Flux<StreamEvent> flux) { return flux; }
        };

        StreamPostProcessor third = new StreamPostProcessor() {
            @Override public String getName() { return "third"; }
            @Override public int getOrder() { return 200; }
            @Override public Flux<StreamEvent> apply(Flux<StreamEvent> flux) { return flux; }
        };

        List<StreamPostProcessor> processors = new ArrayList<>(List.of(third, first, second));
        processors.sort(Comparator.comparingInt(StreamPostProcessor::getOrder));

        assertEquals("first", processors.get(0).getName());
        assertEquals("second", processors.get(1).getName());
        assertEquals("third", processors.get(2).getName());
    }

    @Test
    @DisplayName("processor can accumulate state across events")
    void accumulatesStateAcrossEvents() {
        StreamPostProcessor counter = new StreamPostProcessor() {
            @Override public String getName() { return "counter"; }
            @Override
            public Flux<StreamEvent> apply(Flux<StreamEvent> flux) {
                return flux.index().map(tuple -> {
                    long idx = tuple.getT1();
                    StreamEvent event = tuple.getT2();
                    if (event.getType() == StreamEvent.EventType.TEXT_DELTA) {
                        return StreamEvent.textDelta("[" + (idx + 1) + "] " + event.getTextDelta());
                    }
                    return event;
                });
            }
        };

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("a"),
                StreamEvent.textDelta("b"),
                StreamEvent.textDelta("c")
        );

        StepVerifier.create(counter.apply(input))
                .assertNext(e -> assertEquals("[1] a", e.getTextDelta()))
                .assertNext(e -> assertEquals("[2] b", e.getTextDelta()))
                .assertNext(e -> assertEquals("[3] c", e.getTextDelta()))
                .verifyComplete();
    }

    @Test
    @DisplayName("identity processor passes events through unchanged")
    void identityProcessor() {
        StreamPostProcessor identity = new StreamPostProcessor() {
            @Override public String getName() { return "identity"; }
            @Override public Flux<StreamEvent> apply(Flux<StreamEvent> flux) { return flux; }
        };

        Flux<StreamEvent> input = Flux.just(
                StreamEvent.textDelta("hello"),
                StreamEvent.textDelta("world")
        );

        StepVerifier.create(identity.apply(input))
                .assertNext(e -> assertEquals("hello", e.getTextDelta()))
                .assertNext(e -> assertEquals("world", e.getTextDelta()))
                .verifyComplete();
    }
}
