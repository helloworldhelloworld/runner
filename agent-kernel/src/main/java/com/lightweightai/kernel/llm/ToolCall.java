package com.lightweightai.kernel.llm;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a tool call request from the LLM
 */
public class ToolCall {

    private final String id;
    private final String name;
    private final Map<String, Object> arguments;

    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this.id = id;
        this.name = name;
        this.arguments = new HashMap<>(arguments);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, Object> getArguments() {
        return new HashMap<>(arguments);
    }

    @Override
    public String toString() {
        return "ToolCall{id='" + id + "', name='" + name + "', arguments=" + arguments + '}';
    }
}
