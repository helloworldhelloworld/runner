package com.lightweightai.agent.exception;

/**
 * Base exception for all agent-related errors.
 * All specific exceptions in the agent SDK inherit from this class.
 */
public class AgentException extends RuntimeException {

    public AgentException(String message) {
        super(message);
    }

    public AgentException(String message, Throwable cause) {
        super(message, cause);
    }

    public AgentException(Throwable cause) {
        super(cause);
    }
}
