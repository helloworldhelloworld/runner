package com.lightweightai.agent.exception;

/**
 * Exception thrown when LLM provider encounters an error.
 * This includes API errors, network errors, rate limiting, and model errors.
 */
public class LLMException extends AgentException {

    private final String provider;
    private final Integer statusCode;

    public LLMException(String message) {
        this(message, null, null);
    }

    public LLMException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public LLMException(String message, String provider, Integer statusCode) {
        super(message);
        this.provider = provider;
        this.statusCode = statusCode;
    }

    public LLMException(String message, String provider, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.statusCode = statusCode;
    }

    public String getProvider() {
        return provider;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        if (provider != null) {
            sb.append(" [provider=").append(provider).append("]");
        }
        if (statusCode != null) {
            sb.append(" [statusCode=").append(statusCode).append("]");
        }
        return sb.toString();
    }
}
