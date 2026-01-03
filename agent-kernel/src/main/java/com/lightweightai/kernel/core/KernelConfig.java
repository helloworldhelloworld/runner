package com.lightweightai.kernel.core;

/**
 * Core kernel configuration
 */
public class KernelConfig {

    private String name;
    private String version;
    private int maxToolCallsPerTurn;
    private long toolExecutionTimeoutMs;
    private boolean autoExecuteTools;

    public KernelConfig() {
        this.name = "lightweight-ai-kernel";
        this.version = "0.1.0";
        this.maxToolCallsPerTurn = 10;
        this.toolExecutionTimeoutMs = 30000; // 30 seconds
        this.autoExecuteTools = true;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getMaxToolCallsPerTurn() {
        return maxToolCallsPerTurn;
    }

    public void setMaxToolCallsPerTurn(int maxToolCallsPerTurn) {
        this.maxToolCallsPerTurn = maxToolCallsPerTurn;
    }

    public long getToolExecutionTimeoutMs() {
        return toolExecutionTimeoutMs;
    }

    public void setToolExecutionTimeoutMs(long toolExecutionTimeoutMs) {
        this.toolExecutionTimeoutMs = toolExecutionTimeoutMs;
    }

    public boolean isAutoExecuteTools() {
        return autoExecuteTools;
    }

    public void setAutoExecuteTools(boolean autoExecuteTools) {
        this.autoExecuteTools = autoExecuteTools;
    }
}
