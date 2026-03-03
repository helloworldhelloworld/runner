package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A test tool for SPI scanning tests.
 *
 * Declared in test/resources/META-INF/services/com.lightweightai.kernel.agent.Tool
 */
public class TestEchoTool implements Tool, ToolMetadata {

    @Override
    public String getName() {
        return "test_echo";
    }

    @Override
    public String getDescription() {
        return "Echo the input (test tool)";
    }

    @Override
    public ToolSchema getSchema() {
        Map<String, Object> messageProp = new HashMap<>();
        messageProp.put("type", "string");
        messageProp.put("description", "Message to echo");
        Map<String, Object> props = new HashMap<>();
        props.put("message", messageProp);
        return ToolSchema.withRequired(props, "message");
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String message = (String) args.get("message");
        return ToolResult.success("Echo: " + message);
    }

    @Override
    public String getCategory() {
        return "test";
    }

    @Override
    public List<String> getTags() {
        return Arrays.asList("test", "echo");
    }
}
