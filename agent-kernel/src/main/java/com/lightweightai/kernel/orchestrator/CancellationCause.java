package com.lightweightai.kernel.orchestrator;

/** Why an active execution was terminated. Package-private until a cross-module API is needed. */
public enum CancellationCause {
    USER_INTERRUPT,
    CONNECTION_REPLACED,
    CLIENT_DISCONNECTED,
    BUDGET_EXCEEDED,
    SYSTEM_SHUTDOWN
}
