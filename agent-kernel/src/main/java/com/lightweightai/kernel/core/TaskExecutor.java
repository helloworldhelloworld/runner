package com.lightweightai.kernel.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Framework-level task executor.
 * Manages execution of AsyncTask instances with various strategies.
 */
public interface TaskExecutor {

    /**
     * Execute a single task
     *
     * @param task Task to execute
     * @param <T>  Result type
     * @return Execution result future
     */
    <T> CompletableFuture<T> execute(AsyncTask<T> task);

    /**
     * Execute multiple tasks in parallel
     *
     * @param tasks Tasks to execute
     * @param <T>   Result type
     * @return List of results (in order)
     */
    <T> CompletableFuture<List<T>> executeParallel(List<AsyncTask<T>> tasks);

    /**
     * Execute tasks sequentially (pipeline)
     * Output of one task can feed into the next
     *
     * @param tasks Tasks to execute in order
     * @param <T>   Result type
     * @return Final result
     */
    <T> CompletableFuture<T> executeSequential(List<AsyncTask<T>> tasks);

    /**
     * Execute with timeout
     *
     * @param task    Task to execute
     * @param timeout Timeout from task metadata
     * @param <T>     Result type
     * @return Execution result future
     */
    <T> CompletableFuture<T> executeWithTimeout(AsyncTask<T> task);

    /**
     * Get the underlying executor
     *
     * @return Java executor
     */
    Executor getExecutor();

    /**
     * Shutdown the executor
     */
    void shutdown();
}
