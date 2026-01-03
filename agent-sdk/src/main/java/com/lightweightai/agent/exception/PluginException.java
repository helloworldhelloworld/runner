package com.lightweightai.agent.exception;

/**
 * Exception thrown when plugin execution fails.
 * This includes plugin registration errors, function execution errors, and parameter validation errors.
 */
public class PluginException extends AgentException {

    private final String pluginName;
    private final String functionName;

    public PluginException(String message) {
        this(message, null, null);
    }

    public PluginException(String message, Throwable cause) {
        this(message, null, null, cause);
    }

    public PluginException(String message, String pluginName, String functionName) {
        super(message);
        this.pluginName = pluginName;
        this.functionName = functionName;
    }

    public PluginException(String message, String pluginName, String functionName, Throwable cause) {
        super(message, cause);
        this.pluginName = pluginName;
        this.functionName = functionName;
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getFunctionName() {
        return functionName;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(super.getMessage());
        if (pluginName != null) {
            sb.append(" [plugin=").append(pluginName).append("]");
        }
        if (functionName != null) {
            sb.append(" [function=").append(functionName).append("]");
        }
        return sb.toString();
    }
}
