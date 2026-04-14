package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentResponse;

import java.util.concurrent.Future;

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

    public SubagentRun(String runId, String sessionKey, SpawnRequest request) {
        this.runId = runId;
        this.sessionKey = sessionKey;
        this.request = request;
        this.startTime = System.currentTimeMillis();
    }

    public void complete(AgentResponse response) {
        this.result = response;
        this.status = Status.COMPLETED;
    }

    public void fail(Exception e) {
        this.errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        this.status = Status.FAILED;
    }

    public void cancel(String reason) {
        this.errorMessage = reason;
        this.status = Status.CANCELLED;
        if (future != null) {
            future.cancel(true);
        }
    }

    public void setFuture(Future<?> future) { this.future = future; }

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
