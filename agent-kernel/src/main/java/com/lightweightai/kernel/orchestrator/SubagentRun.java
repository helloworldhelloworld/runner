package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.core.CancellationToken;

import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

/**
 * Subagent 运行状态跟踪
 */
public class SubagentRun {

    public enum Status { RUNNING, COMPLETED, FAILED, CANCELLED }

    private final String runId;
    private final String sessionKey;
    private final SpawnRequest request;
    private final long startTime;

    private volatile Status status = Status.RUNNING;
    private volatile AgentResponse result;
    private volatile String errorMessage;
    private volatile Future<?> future;
    private final CompletableFuture<Void> terminal = new CompletableFuture<>();
    private CancellationToken.Registration cancellationRegistration;

    public SubagentRun(String runId, String sessionKey, SpawnRequest request) {
        this.runId = runId;
        this.sessionKey = sessionKey;
        this.request = request;
        this.startTime = System.currentTimeMillis();
    }

    public void complete(AgentResponse response) { tryComplete(response); }

    synchronized boolean tryComplete(AgentResponse response) {
        if (!isRunning()) return false;
        this.result = response;
        this.status = Status.COMPLETED;
        releaseCancellationRegistration();
        terminal.complete(null);
        return true;
    }

    public void fail(Exception e) { tryFail(e); }

    synchronized boolean tryFail(Exception e) {
        if (!isRunning()) return false;
        this.errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        this.status = Status.FAILED;
        releaseCancellationRegistration();
        terminal.complete(null);
        return true;
    }

    public void cancel(String reason) { tryCancel(reason); }

    synchronized boolean tryCancel(String reason) {
        if (!isRunning()) return false;
        this.errorMessage = reason;
        this.status = Status.CANCELLED;
        releaseCancellationRegistration();
        if (future != null) future.cancel(true);
        terminal.complete(null);
        return true;
    }

    public synchronized void setFuture(Future<?> future) {
        this.future = future;
        if (status == Status.CANCELLED && future != null) future.cancel(true);
    }

    synchronized void setCancellationRegistration(CancellationToken.Registration registration) {
        cancellationRegistration = registration;
        if (!isRunning()) releaseCancellationRegistration();
    }

    synchronized void releaseCancellationRegistration() {
        if (cancellationRegistration != null) {
            cancellationRegistration.close();
            cancellationRegistration = null;
        }
    }

    boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        try {
            terminal.get(Math.max(0, timeout), unit);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unexpected completion signal failure", e.getCause());
        }
    }

    public String getRunId() { return runId; }
    public String getSessionKey() { return sessionKey; }
    public SpawnRequest getRequest() { return request; }
    public Status getStatus() { return status; }
    public AgentResponse getResult() { return result; }
    public String getErrorMessage() { return errorMessage; }
    public long getStartTime() { return startTime; }
    public long getDurationMs() { return System.currentTimeMillis() - startTime; }
    public boolean isRunning() { return status == Status.RUNNING; }
}
