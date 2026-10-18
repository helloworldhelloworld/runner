package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ToolExecutionContext;
import com.lightweightai.kernel.core.ToolResultChunk;
import com.lightweightai.kernel.llm.ToolResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/** Optional extension for tools that need request-bound execution services. */
public interface ContextAwareTool extends Tool {
    ToolResult execute(Map<String, Object> args, ToolExecutionContext context);

    default Flux<ToolResultChunk> executeReactive(Map<String, Object> args, ToolExecutionContext context) {
        return Mono.fromCallable(() -> execute(args, context))
                .subscribeOn(Schedulers.boundedElastic())
                .map(result -> ToolResultChunk.complete(getName(), result)).flux();
    }
}
