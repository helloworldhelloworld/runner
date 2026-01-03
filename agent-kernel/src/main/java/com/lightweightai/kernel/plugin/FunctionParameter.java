package com.lightweightai.kernel.plugin;

/**
 * Defines a parameter for a plugin function
 */
public class FunctionParameter {

    private final String name;
    private final ParameterType type;
    private final String description;
    private final boolean required;
    private final Object defaultValue;

    private FunctionParameter(Builder builder) {
        this.name = builder.name;
        this.type = builder.type;
        this.description = builder.description;
        this.required = builder.required;
        this.defaultValue = builder.defaultValue;
    }

    public String getName() {
        return name;
    }

    public ParameterType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public static Builder builder(String name, ParameterType type) {
        return new Builder(name, type);
    }

    public enum ParameterType {
        STRING("string"),
        NUMBER("number"),
        INTEGER("integer"),
        BOOLEAN("boolean"),
        OBJECT("object"),
        ARRAY("array");

        private final String jsonType;

        ParameterType(String jsonType) {
            this.jsonType = jsonType;
        }

        public String getJsonType() {
            return jsonType;
        }
    }

    public static class Builder {
        private final String name;
        private final ParameterType type;
        private String description;
        private boolean required = true;
        private Object defaultValue;

        public Builder(String name, ParameterType type) {
            this.name = name;
            this.type = type;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public FunctionParameter build() {
            return new FunctionParameter(this);
        }
    }
}
