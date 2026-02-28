package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;

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
        return ToolSchema.withRequired(
            Map.of("message", Map.of("type", "string", "description", "Message to echo")),
            "message"
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String message = (String) args.get("message");
        return ToolResult.success("test_echo", "Echo: " + message);
    }

    @Override
    public String getCategory() {
        return "test";
    }

    @Override
    public List<String> getTags() {
        return List.of("test", "echo");
    }
}
