package com.lightweightai.kernel.core;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Framework-level abstraction for asynchronous tasks.
 * All operations (LLM calls, plugin execution, RAG queries, external APIs) implement this.
 *
 * Supports three execution modes:
 * 1. Sync - blocking execution
 * 2. Async - non-blocking with CompletableFuture
 * 3. Stream - event-driven streaming results
 *
 * @param <T> Result type
 */
public interface AsyncTask<T> {

    /**
     * Execute synchronously (blocking)
     *
     * @return Task result
     */
    T execute();

    /**
     * Execute asynchronously (non-blocking)
     *
     * @return CompletableFuture of task result
     */
    CompletableFuture<T> executeAsync();

    /**
     * Execute with streaming results
     *
     * @param streamHandler Handler for stream events
     * @return CompletableFuture that completes when streaming finishes
     */
    CompletableFuture<T> executeStream(StreamHandler<T> streamHandler);

    /**
     * Get task metadata (name, description, timeout, etc.)
     *
     * @return Task metadata
     */
    TaskMetadata getMetadata();

    /**
     * Stream event handler - generic for any streaming operation
     *
     * @param <T> Result type
     */
    interface StreamHandler<T> {

        /**
         * Called when streaming starts
         */
        default void onStart() {}

        /**
         * Called when a chunk/delta is available
         *
         * @param chunk Data chunk
         */
        default void onChunk(Object chunk) {}

        /**
         * Called when streaming completes
         *
         * @param result Final complete result
         */
        default void onComplete(T result) {}

        /**
         * Called on error
         *
         * @param error The error
         */
        default void onError(Throwable error) {}

        /**
         * Called on cancellation
         */
        default void onCancel() {}
    }
}
