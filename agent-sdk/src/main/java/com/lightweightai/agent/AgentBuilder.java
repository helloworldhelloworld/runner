package com.lightweightai.agent;

import com.lightweightai.agent.plugin.Plugin;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent构建器
 *
 * 使用Builder模式创建Agent，提供流畅的API
 */
public class AgentBuilder {

    private String llmProvider;
    private String apiKey;
    private String model;
    private final List<Plugin> plugins = new ArrayList<>();
    private ToolRegistry toolRegistry;
    private ChatOptions defaultOptions;
    private boolean enableMemory = false;
    private Duration timeout = Duration.ofMinutes(5);

    AgentBuilder() {
        // package-private，只能通过Agent.builder()创建
    }

    /**
     * 使用Claude作为LLM提供者（默认模型）
     *
     * @param apiKey Anthropic API密钥
     * @return this
     */
    public AgentBuilder claude(String apiKey) {
        return claude(apiKey, "claude-3-5-sonnet-20241022");
    }

    /**
     * 使用Claude作为LLM提供者（指定模型）
     *
     * @param apiKey Anthropic API密钥
     * @param model  模型名称
     * @return this
     */
    public AgentBuilder claude(String apiKey, String model) {
        this.llmProvider = "claude";
        this.apiKey = apiKey;
        this.model = model;
        return this;
    }

    /**
     * 使用OpenAI作为LLM提供者（默认模型）
     *
     * @param apiKey OpenAI API密钥
     * @return this
     */
    public AgentBuilder openai(String apiKey) {
        return openai(apiKey, "gpt-4");
    }

    /**
     * 使用OpenAI作为LLM提供者（指定模型）
     *
     * @param apiKey OpenAI API密钥
     * @param model  模型名称
     * @return this
     */
    public AgentBuilder openai(String apiKey, String model) {
        this.llmProvider = "openai";
        this.apiKey = apiKey;
        this.model = model;
        return this;
    }

    /**
     * 使用统一 ToolRegistry（支持本地工具、MCP 工具、SPI 工具等）
     *
     * 通过此方法注册的工具，agent 调用时完全透明，不区分工具来源。
     * 配合 ToolClient 使用，可实现 MCP 工具无感调用：
     *
     * <pre>
     * ToolClient client = ToolClient.create()
     *     .registerLocal(new MathTools())
     *     .addMcpServer("weather", transport)
     *     .build();
     *
     * Agent agent = Agent.builder()
     *     .claude(apiKey)
     *     .tools(client.getToolRegistry())  // 本地/MCP/客户端工具统一注册
     *     .build();
     * </pre>
     *
     * @param toolRegistry 包含所有工具的注册表
     * @return this
     */
    public AgentBuilder tools(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        return this;
    }

    /**
     * 注册单个 Tool 到 agent
     *
     * @param tool Tool 实例
     * @return this
     */
    public AgentBuilder tool(Tool tool) {
        ensureToolRegistry();
        this.toolRegistry.register(tool);
        return this;
    }

    /**
     * 通过注解扫描注册工具（@ToolFunction）
     *
     * @param annotated 包含 @ToolFunction 方法的对象
     * @return this
     */
    public AgentBuilder tools(Object annotated) {
        ensureToolRegistry();
        this.toolRegistry.registerObject(annotated);
        return this;
    }

    private void ensureToolRegistry() {
        if (this.toolRegistry == null) {
            this.toolRegistry = new ToolRegistry();
        }
    }

    /**
     * 添加插件
     *
     * @param plugin 插件
     * @return this
     */
    public AgentBuilder plugin(Plugin plugin) {
        this.plugins.add(plugin);
        return this;
    }

    /**
     * 添加多个插件
     *
     * @param plugins 插件数组
     * @return this
     */
    public AgentBuilder plugins(Plugin... plugins) {
        for (Plugin p : plugins) {
            this.plugins.add(p);
        }
        return this;
    }

    /**
     * 启用对话记忆（使用默认策略）
     *
     * @return this
     */
    public AgentBuilder withMemory() {
        this.enableMemory = true;
        return this;
    }

    /**
     * 设置默认聊天选项
     *
     * @param options 选项
     * @return this
     */
    public AgentBuilder options(ChatOptions options) {
        this.defaultOptions = options;
        return this;
    }

    /**
     * 设置超时时间
     *
     * @param timeout 超时时长
     * @return this
     */
    public AgentBuilder timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * 构建Agent实例
     *
     * @return Agent实例
     */
    public Agent build() {
        if (llmProvider == null || apiKey == null) {
            throw new IllegalStateException("LLM provider and API key are required");
        }

        // TODO: 创建实际的Agent实现
        return new DefaultAgent(this);
    }

    // === Package-private getters for DefaultAgent ===

    String getLlmProvider() {
        return llmProvider;
    }

    String getApiKey() {
        return apiKey;
    }

    String getModel() {
        return model;
    }

    List<Plugin> getPlugins() {
        return new ArrayList<>(plugins);
    }

    ChatOptions getDefaultOptions() {
        return defaultOptions;
    }

    boolean isEnableMemory() {
        return enableMemory;
    }

    Duration getTimeout() {
        return timeout;
    }

    ToolRegistry getToolRegistry() {
        return toolRegistry;
    }
}
