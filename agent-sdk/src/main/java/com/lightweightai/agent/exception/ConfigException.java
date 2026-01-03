package com.lightweightai.agent.exception;

/**
 * Exception thrown when configuration is invalid or missing.
 * This includes missing API keys, invalid model names, and configuration validation errors.
 */
public class ConfigException extends AgentException {

    private final String configKey;

    public ConfigException(String message) {
        this(message, (String) null);
    }

    public ConfigException(String message, String configKey) {
        super(message);
        this.configKey = configKey;
    }

    public ConfigException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public ConfigException(String message, String configKey, Throwable cause) {
        super(message, cause);
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        if (configKey != null) {
            sb.append(" [config=").append(configKey).append("]");
        }
        return sb.toString();
    }
}
