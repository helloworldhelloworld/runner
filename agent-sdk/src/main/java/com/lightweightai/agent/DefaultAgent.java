package com.lightweightai.agent;

import com.lightweightai.agent.exception.AgentException;
import com.lightweightai.agent.exception.LLMException;
import com.lightweightai.agent.memory.ConversationMemory;
import com.lightweightai.agent.memory.SimpleConversationMemory;
import com.lightweightai.agent.plugin.Plugin;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMProviderFactory;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.plugin.PluginFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent的默认实现
 *
 * 内部使用Kernel进行实际的编排工作
 */
class DefaultAgent implements Agent {

    private final String llmProvider;
    private final String apiKey;
    private final String model;
    private final List<Plugin> plugins;
    private final ChatOptions defaultOptions;
    private final ConversationMemory memory;
    private final ToolExecutor toolExecutor;
    private LLMProvider provider;  // Lazy initialization
    private ToolCallingLoop toolCallingLoop;  // Lazy initialization

    DefaultAgent(AgentBuilder builder) {
        this.llmProvider = builder.getLlmProvider();
        this.apiKey = builder.getApiKey();
        this.model = builder.getModel();
        this.plugins = new ArrayList<>(builder.getPlugins());
        this.defaultOptions = builder.getDefaultOptions();

        // 如果启用记忆，创建记忆对象
        if (builder.isEnableMemory()) {
            this.memory = new SimpleConversationMemory();
        } else {
            this.memory = null;
        }

        // 初始化工具执行器：优先使用 ToolRegistry（支持本地+MCP统一管理）
        ToolRegistry externalRegistry = builder.getToolRegistry();
        if (externalRegistry != null) {
            this.toolExecutor = new ToolExecutor(externalRegistry);
        } else {
            this.toolExecutor = new ToolExecutor();
        }
        registerAllPlugins();

        // LLM Provider 和 ToolCallingLoop 延迟初始化
        // 在实际调用 chat() 时创建
    }

    /**
     * 确保 LLM Provider 和 ToolCallingLoop 已初始化
     */
    private void ensureInitialized() {
        if (provider == null) {
            // 创建 LLM Provider
            // TODO: 实际实现时根据 llmProvider 字段选择对应的 Provider
            provider = createLLMProvider(llmProvider, apiKey, model);

            if (provider == null) {
                throw new AgentException(
                    "LLM Provider not available. " +
                    "No implementation exists for provider: " + llmProvider + ". " +
                    "Please implement a provider or use a mock for testing."
                );
            }
        }

        if (toolCallingLoop == null) {
            // 创建工具调用循环
            toolCallingLoop = ToolCallingLoop.builder()
                .provider(provider)
                .toolExecutor(toolExecutor)
                .maxIterations(10)
                .build();
        }
    }

    /**
     * 注册所有插件的函数到工具执行器
     */
    private void registerAllPlugins() {
        for (Plugin plugin : plugins) {
            toolExecutor.registerFunctions(plugin.getFunctions());
        }
    }

    /**
     * 创建 LLM Provider
     */
    private LLMProvider createLLMProvider(String providerName, String apiKey, String model) {
        return LLMProviderFactory.create(providerName, apiKey, model);
    }

    @Override
    public String chat(String message) {
        return chat(message, defaultOptions);
    }

    @Override
    public String chat(String message, ChatOptions options) {
        // 确保 Provider 和 ToolCallingLoop 已初始化
        ensureInitialized();

        try {
            // 1. 构建对话消息列表
            List<ConversationMessage> messages = buildMessages(message, options);

            // 2. 构建 LLM 选项（包含工具定义）
            LLMOptions llmOptions = buildLLMOptions(options);

            // 3. 使用 ToolCallingLoop 执行对话（自动处理工具调用）
            LLMResponse response = toolCallingLoop.executeWithTools(messages, llmOptions);

            // 4. 提取响应文本
            String responseText = response.getMessage().getTextContent();

            // 5. 如果启用了记忆，保存对话
            if (memory != null) {
                memory.addMessage("user", message);
                memory.addMessage("assistant", responseText);
            }

            return responseText;

        } catch (Exception e) {
            throw new AgentException("Chat failed: " + e.getMessage(), e);
        }
    }

    /**
     * 构建对话消息列表
     */
    private List<ConversationMessage> buildMessages(String userMessage, ChatOptions options) {
        List<ConversationMessage> messages = new ArrayList<>();

        // 添加系统提示（如果有）
        String systemPrompt = options != null && options.getSystemPrompt() != null
            ? options.getSystemPrompt()
            : null;

        if (systemPrompt != null) {
            messages.add(ConversationMessage.builder()
                .role(MessageRole.SYSTEM)
                .textContent(systemPrompt)
                .build());
        }

        // 添加历史对话（如果启用了记忆）
        if (memory != null) {
            List<String> history = memory.getHistory();
            for (int i = 0; i < history.size(); i += 2) {
                if (i + 1 < history.size()) {
                    // 用户消息
                    messages.add(ConversationMessage.builder()
                        .role(MessageRole.USER)
                        .textContent(history.get(i))
                        .build());
                    // 助手消息
                    messages.add(ConversationMessage.builder()
                        .role(MessageRole.ASSISTANT)
                        .textContent(history.get(i + 1))
                        .build());
                }
            }
        }

        // 添加当前用户消息
        messages.add(ConversationMessage.builder()
            .role(MessageRole.USER)
            .textContent(userMessage)
            .build());

        return messages;
    }

    /**
     * 构建 LLM 选项（包含工具定义）
     */
    private LLMOptions buildLLMOptions(ChatOptions options) {
        LLMOptions.Builder builder = LLMOptions.builder();

        // 设置基本选项
        if (options != null) {
            if (options.getTemperature() != null) {
                builder.temperature(options.getTemperature());
            }
            if (options.getMaxTokens() != null) {
                builder.maxTokens(options.getMaxTokens());
            }
        }

        // 添加工具定义（合并 ToolRegistry + legacy plugins）
        List<Map<String, Object>> tools = new ArrayList<>(toolExecutor.getToolDefinitions());
        for (Plugin plugin : plugins) {
            tools.addAll(plugin.toClaudeTools());
        }

        if (!tools.isEmpty()) {
            builder.toolDefinitions(tools);
        }

        return builder.build();
    }

    @Override
    public void chatStream(String message, StreamCallback callback) {
        chatStream(message, defaultOptions, callback);
    }

    @Override
    public void chatStream(String message, ChatOptions options, StreamCallback callback) {
        // TODO: 调用内部Kernel进行流式对话
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Agent addPlugin(Plugin plugin) {
        this.plugins.add(plugin);
        // 动态注册新插件的函数
        toolExecutor.registerFunctions(plugin.getFunctions());
        return this;
    }

    @Override
    public List<Plugin> getPlugins() {
        return new ArrayList<>(plugins);
    }

    @Override
    public ConversationMemory getMemory() {
        return memory;
    }

    @Override
    public void clearMemory() {
        if (memory != null) {
            memory.clear();
        }
    }
}
