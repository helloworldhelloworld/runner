package com.lightweightai.kernel.core;

import java.util.function.Consumer;

/**
 * Generic streaming abstraction for the framework.
 * Any component (LLM, RAG, external API, plugin) can produce streams.
 *
 * This is framework-level, not tied to LLM specifics.
 *
 * @param <T> Type of stream chunks
 */
public interface StreamSource<T> {

    /**
     * Subscribe to the stream
     *
     * @param subscriber Stream subscriber
     * @return Subscription handle for cancellation
     */
    Subscription subscribe(StreamSubscriber<T> subscriber);

    /**
     * Stream subscriber interface
     *
     * @param <T> Chunk type
     */
    interface StreamSubscriber<T> {

        /**
         * Called when subscription starts
         *
         * @param subscription Subscription handle
         */
        void onSubscribe(Subscription subscription);

        /**
         * Called for each chunk
         *
         * @param chunk Data chunk
         */
        void onNext(T chunk);

        /**
         * Called when stream completes
         */
        void onComplete();

        /**
         * Called on error
         *
         * @param error The error
         */
        void onError(Throwable error);
    }

    /**
     * Subscription handle - allows cancellation and backpressure
     */
    interface Subscription {

        /**
         * Request N more items (backpressure control)
         *
         * @param n Number of items to request
         */
        void request(long n);

        /**
         * Cancel the subscription
         */
        void cancel();

        /**
         * Check if cancelled
         *
         * @return true if cancelled
         */
        boolean isCancelled();
    }

    /**
     * Helper: Create a simple subscriber with callbacks
     *
     * @param onNext   Callback for chunks
     * @param onError  Callback for errors
     * @param onComplete Callback for completion
     * @param <T>      Chunk type
     * @return Stream subscriber
     */
    static <T> StreamSubscriber<T> create(
            Consumer<T> onNext,
            Consumer<Throwable> onError,
            Runnable onComplete
    ) {
        return new StreamSubscriber<T>() {
            private Subscription subscription;

            @Override
            public void onSubscribe(Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE); // Unbounded
            }

            @Override
            public void onNext(T chunk) {
                if (onNext != null) {
                    onNext.accept(chunk);
                }
            }

            @Override
            public void onComplete() {
                if (onComplete != null) {
                    onComplete.run();
                }
            }

            @Override
            public void onError(Throwable error) {
                if (onError != null) {
                    onError.accept(error);
                }
            }
        };
    }
}
