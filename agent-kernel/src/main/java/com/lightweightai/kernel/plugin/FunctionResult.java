package com.lightweightai.kernel.plugin;

/**
 * Result of a plugin function execution
 */
public class FunctionResult {

    private final boolean success;
    private final Object value;
    private final String error;

    private FunctionResult(boolean success, Object value, String error) {
        this.success = success;
        this.value = value;
        this.error = error;
    }

    public boolean isSuccess() {
        return success;
    }

    public Object getValue() {
        return value;
    }

    public String getError() {
        return error;
    }

    /**
     * Create a successful result
     */
    public static FunctionResult success(Object value) {
        return new FunctionResult(true, value, null);
    }

    /**
     * Create an error result
     */
    public static FunctionResult error(String error) {
        return new FunctionResult(false, null, error);
    }

    /**
     * Create an error result from exception
     */
    public static FunctionResult error(Throwable throwable) {
        return new FunctionResult(false, null, throwable.getMessage());
    }

    @Override
    public String toString() {
        if (success) {
            return "FunctionResult{success=true, value=" + value + '}';
        } else {
            return "FunctionResult{success=false, error='" + error + "'}";
        }
    }
}
