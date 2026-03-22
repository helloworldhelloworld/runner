package com.lightweightai.kernel.trace;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TraceContext Reactor Context 传播测试
 */
class TraceContextTest {

    @Test
    void putAndCurrent_roundTrip() {
        SpanContext span = SpanContext.createRoot("test");
        Context ctx = TraceContext.put(span).apply(Context.empty());

        SpanContext retrieved = TraceContext.current(ctx);
        assertSame(span, retrieved);
    }

    @Test
    void current_returnsNull_whenNoSpan() {
        Context ctx = Context.empty();
        SpanContext retrieved = TraceContext.current(ctx);
        assertNull(retrieved);
    }

    @Test
    void reactorContext_propagatesThroughFlux() {
        SpanContext rootSpan = SpanContext.createRoot("gateway");

        // Simulate downstream operator reading span from context
        Flux<String> flux = Flux.deferContextual(ctx -> {
            SpanContext parent = TraceContext.current(ctx);
            assertNotNull(parent);
            assertEquals("gateway", parent.getName());
            assertEquals(rootSpan.getTraceId(), parent.getTraceId());
            return Flux.just("ok");
        }).contextWrite(TraceContext.put(rootSpan));

        StepVerifier.create(flux)
            .expectNext("ok")
            .verifyComplete();
    }

    @Test
    void reactorContext_childSpanPropagation() {
        SpanContext rootSpan = SpanContext.createRoot("gateway");

        // Simulate: outer writes root → inner reads root, creates child, writes child → innermost reads child
        Flux<String> flux = Flux.deferContextual(outerCtx -> {
            SpanContext parent = TraceContext.current(outerCtx);
            SpanContext child = parent.createChild("iteration");

            return Flux.deferContextual(innerCtx -> {
                SpanContext current = TraceContext.current(innerCtx);
                assertEquals("iteration", current.getName());
                assertEquals(parent.getSpanId(), current.getParentSpanId());
                return Flux.just("nested");
            }).contextWrite(TraceContext.put(child));
        }).contextWrite(TraceContext.put(rootSpan));

        StepVerifier.create(flux)
            .expectNext("nested")
            .verifyComplete();
    }
}
