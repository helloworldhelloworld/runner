package com.lightweightai.kernel.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of configuration validation
 */
public class ValidationResult {

    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    public void addError(String error) {
        errors.add(error);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (!errors.isEmpty()) {
            sb.append("Errors:\n");
            for (String error : errors) {
                sb.append("  - ").append(error).append("\n");
            }
        }

        if (!warnings.isEmpty()) {
            sb.append("Warnings:\n");
            for (String warning : warnings) {
                sb.append("  - ").append(warning).append("\n");
            }
        }

        return sb.toString();
    }
}
