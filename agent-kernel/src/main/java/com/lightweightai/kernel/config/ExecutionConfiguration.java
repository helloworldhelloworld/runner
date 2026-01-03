package com.lightweightai.kernel.config;

/**
 * Configuration for execution behavior
 */
public class ExecutionConfiguration {

    private int maxToolCalls = 10;
    private long timeoutMs = 300000; // 5 minutes
    private boolean autoExecuteTools = true;
    private int concurrency = 1;

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean isAutoExecuteTools() {
        return autoExecuteTools;
    }

    public void setAutoExecuteTools(boolean autoExecuteTools) {
        this.autoExecuteTools = autoExecuteTools;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }
}
