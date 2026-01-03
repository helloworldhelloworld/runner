package com.lightweightai.kernel.config;

import com.lightweightai.kernel.core.KernelConfig;

/**
 * Root configuration class for the entire kernel.
 * Supports multiple configuration sources: code, files, environment variables.
 */
public class KernelConfiguration {

    private KernelConfig kernel = new KernelConfig();
    private LLMConfiguration llm = new LLMConfiguration();
    private PluginConfiguration plugins = new PluginConfiguration();
    private MemoryConfiguration memory = new MemoryConfiguration();
    private ExecutionConfiguration execution = new ExecutionConfiguration();

    public KernelConfig getKernel() {
        return kernel;
    }

    public void setKernel(KernelConfig kernel) {
        this.kernel = kernel;
    }

    public LLMConfiguration getLlm() {
        return llm;
    }

    public void setLlm(LLMConfiguration llm) {
        this.llm = llm;
    }

    public PluginConfiguration getPlugins() {
        return plugins;
    }

    public void setPlugins(PluginConfiguration plugins) {
        this.plugins = plugins;
    }

    public MemoryConfiguration getMemory() {
        return memory;
    }

    public void setMemory(MemoryConfiguration memory) {
        this.memory = memory;
    }

    public ExecutionConfiguration getExecution() {
        return execution;
    }

    public void setExecution(ExecutionConfiguration execution) {
        this.execution = execution;
    }

    /**
     * Validate the configuration
     */
    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();

        if (llm.getProviders().isEmpty()) {
            result.addError("At least one LLM provider must be configured");
        }

        if (llm.getDefaultProvider() == null || llm.getDefaultProvider().isEmpty()) {
            result.addError("Default LLM provider must be specified");
        }

        return result;
    }
}
