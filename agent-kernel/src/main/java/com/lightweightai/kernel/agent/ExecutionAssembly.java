package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.ConfigurationContent;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.prompt.PromptSnapshot;

import java.util.*;

/** Immutable evidence of the configuration actually selected for an execution, excluding user memory and secrets. */
public final class ExecutionAssembly {
    private final Map<String, Object> configuration;
    private final String digest;

    public ExecutionAssembly(String agentId, String systemPrompt, LLMProvider provider, LLMOptions options,
                             ToolRegistry tools, DeviceContext device, PromptSnapshot prompts,
                             String input, int maxIterations, String memoryImplementation) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("provider", provider.getProviderName());
        model.put("implementation", provider.getClass().getName());
        var capability = provider.getModelCapability();
        model.put("model", capability == null ? null : capability.getModelId());
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("temperature", options.getTemperature());
        parameters.put("maxTokens", options.getMaxTokens());
        parameters.put("maxBudgetTokens", options.getMaxBudgetTokens());
        parameters.put("systemPrompt", options.getSystemPrompt());
        List<Map<String, Object>> implementations = tools.getAll().stream().map(tool -> {
            Tool implementation = tool instanceof SnapshotToolRegistry.FrozenTool frozen ? frozen.delegate : tool;
            return Map.<String, Object>of("name", tool.getName(), "implementation", implementation.getClass().getName(),
                    "version", tool instanceof ToolMetadata metadata ? metadata.getVersion() : "unspecified",
                    "risk", tool.riskLevel() == null ? "unspecified" : tool.riskLevel().name());
        }).sorted(Comparator.comparing(m -> (String) m.get("name"))).toList();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("schemaVersion", 1);
        values.put("agentId", agentId);
        values.put("systemPrompt", systemPrompt);
        values.put("model", model);
        values.put("parameters", parameters);
        values.put("toolDefinitions", options.getToolDefinitions());
        values.put("toolImplementations", implementations);
        values.put("promptCatalogDigest", prompts.getDigest());
        values.put("selectedPrompts", prompts.describeSelection(input));
        values.put("device", Map.of("type", device.getDeviceType(), "version", Objects.toString(device.getVersion(), "")));
        values.put("maxToolIterations", maxIterations);
        values.put("memoryImplementation", memoryImplementation);
        configuration = ConfigurationContent.map(values);
        digest = ConfigurationContent.digest(configuration);
    }
    public Map<String, Object> getConfiguration() { return configuration; }
    public String getDigest() { return digest; }
}
