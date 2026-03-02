package com.lightweightai.tools.time;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolMetadata;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Get the current date and time.
 */
public class GetTimeTool implements Tool, ToolMetadata {

    @Override
    public String getName() {
        return "get_time";
    }

    @Override
    public String getDescription() {
        return "Get the current date and time";
    }

    @Override
    public ToolSchema getSchema() {
        return ToolSchema.empty();
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return ToolResult.success(now);
    }

    @Override
    public String getCategory() {
        return "utility";
    }

    @Override
    public List<String> getTags() {
        return List.of("utility", "time");
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public boolean isIdempotent() {
        return true;
    }

    @Override
    public boolean isOpenWorld() {
        return false;
    }
}
