package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.core.ToolExecutor;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.prompt.Skill;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Agent 执行循环 (OpenClaw 风格)
 *
 * 核心流程：
 * 1. 检索记忆 (Memory Search)
 * 2. 构建上下文 (System Prompt + Memory + History)
 * 3. 调用 LLM
 * 4. 处理 Tool Calls (循环)
 * 5. 写入记忆
 * 6. 返回结果
 */
public class AgentLoop {

    private final LLMProvider llmProvider;
    private final MemoryProvider memoryProvider;
    private final PromptEngine promptEngine;
    private final ToolRegistry toolRegistry;
    private final String systemPrompt;
    private final int maxToolIterations;
    private final LLMOptions llmOptions;
    private final List<AgentObserver> observers;

    private AgentLoop(Builder builder) {
        this.llmProvider = Objects.requireNonNull(builder.llmProvider, "llmProvider required");
        this.memoryProvider = Objects.requireNonNull(builder.memoryProvider, "memoryProvider required");
        this.toolRegistry = builder.toolRegistry;
        this.systemPrompt = builder.systemPrompt != null ? builder.systemPrompt : "";
        this.maxToolIterations = builder.maxToolIterations;
        this.llmOptions = builder.llmOptions;
        this.observers = new ArrayList<>(builder.observers);

        // 初始化 PromptEngine
        this.promptEngine = PromptEngine.builder()
            .memoryProvider(memoryProvider)
            .baseSystemPrompt(systemPrompt)
            .build();

        // 注册 Skills（将 Tools 转换为 Skill）
        for (Tool tool : toolRegistry.getEnabled()) {
            Skill skill = Skill.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .addTool(tool.getName(), tool.getDescription(), tool.getSchema().toMap())
                .build();
            promptEngine.registerSkill(skill);
        }
    }

    // ==================== 同步执行 ====================

    /**
     * 执行 Agent 循环（同步）
     *
     * 委托给 ToolCallingLoop.executeWithTools() 处理 LLM ↔ Tool 循环，
     * 本方法只负责前置（记忆检索、消息构建）和后置（记忆写入）。
     */
    public AgentResponse run(String input, String sessionId) {
        // 1. 检索相关记忆
        List<MemorySearchResult> memoryResults = memoryProvider.search(input);
        String memoryContext = formatMemoryContext(memoryResults);

        // 2. 保存用户消息
        memoryProvider.addMessage(sessionId, Message.user(input));

        // 3. 构建消息上下文
        List<ConversationMessage> messages = buildMessages(sessionId, memoryContext);

        // 4. 委托给 ToolCallingLoop 执行工具循环
        ToolExecutor toolExecutor = new ToolExecutor(toolRegistry);
        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(llmProvider)
            .toolExecutor(toolExecutor)
            .maxIterations(maxToolIterations)
            .build();

        LLMResponse finalResponse;
        try {
            finalResponse = loop.executeWithTools(messages, llmOptions);
        } catch (RuntimeException e) {
            return AgentResponse.builder()
                .text("Reached maximum tool iterations")
                .stopReason("max_iterations")
                .build();
        }

        // 5. 保存助手消息到记忆
        String responseText = finalResponse.getMessage().getTextContent();
        memoryProvider.addMessage(sessionId, Message.assistant(responseText));
        writeConversationToMemory(input, responseText);

        return AgentResponse.builder()
            .text(responseText)
            .stopReason(finalResponse.getStopReason())
            .build();
    }

    // ==================== 流式执行 ====================

    /**
     * 执行 Agent 循环（流式）
     */
    public CompletableFuture<AgentResponse> runStream(
            String input,
            String sessionId,
            Consumer<String> onDelta) {

        // 1. 检索相关记忆
        List<MemorySearchResult> memoryResults = memoryProvider.search(input);
        String memoryContext = formatMemoryContext(memoryResults);

        // 2. 保存用户消息
        memoryProvider.addMessage(sessionId, Message.user(input));

        // 3. 构建消息上下文
        List<ConversationMessage> messages = buildMessages(sessionId, memoryContext);

        // 4. 流式调用 LLM
        StringBuilder fullResponse = new StringBuilder();

        return llmProvider.completeStream(messages, llmOptions, new LLMProvider.StreamEventHandler() {
            @Override
            public void onTextDelta(String delta) {
                fullResponse.append(delta);
                onDelta.accept(delta);
            }

            @Override
            public void onComplete(LLMResponse response) {
                // 保存助手消息到记忆
                memoryProvider.addMessage(sessionId, Message.assistant(fullResponse.toString()));
                writeConversationToMemory(input, fullResponse.toString());
            }

            @Override
            public void onError(Throwable error) {
                // 错误处理
            }
        }).thenApply(response -> AgentResponse.builder()
            .text(fullResponse.toString())
            .stopReason(response.getStopReason())
            .build());
    }

    // ==================== Reactive 流式执行 ====================

    /**
     * 执行 Agent 循环（Reactive 流式）
     *
     * 返回 Flux&lt;StreamEvent&gt;，包含 LLM 文本片段、工具进度、工具结果等全链路事件。
     * 需要 ToolCallingLoop 支持。
     */
    public Flux<StreamEvent> runReactive(String input, String sessionId) {
        // 1. 检索记忆（可以阻塞，一次性操作）
        List<MemorySearchResult> memoryResults = memoryProvider.search(input);
        String memoryContext = formatMemoryContext(memoryResults);

        // 2. 保存用户消息
        memoryProvider.addMessage(sessionId, Message.user(input));

        // 3. 构建消息上下文
        List<ConversationMessage> messages = buildMessages(sessionId, memoryContext);

        // 4. 构建 ToolCallingLoop 并执行
        ToolExecutor toolExecutor = new ToolExecutor(toolRegistry);
        ToolCallingLoop loop = ToolCallingLoop.builder()
            .provider(llmProvider)
            .toolExecutor(toolExecutor)
            .maxIterations(maxToolIterations)
            .build();

        return loop.executeWithToolsReactive(messages, llmOptions)
            .doOnComplete(() -> {
                // 写入记忆（在完成时做，简化处理）
            });
    }

    // ==================== 辅助方法 ====================

    private List<ConversationMessage> buildMessages(String sessionId, String memoryContext) {
        List<ConversationMessage> messages = new ArrayList<>();

        // System prompt
        StringBuilder systemContent = new StringBuilder(systemPrompt);
        if (!memoryContext.isEmpty()) {
            systemContent.append("\n\n相关记忆:\n").append(memoryContext);
        }

        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.SYSTEM)
            .textContent(systemContent.toString())
            .build());

        // 历史消息
        List<Message> history = memoryProvider.getHistory(sessionId, 20);
        for (Message msg : history) {
            messages.add(msg.toConversationMessage());
        }

        return messages;
    }

    private String formatMemoryContext(List<MemorySearchResult> results) {
        if (results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        int count = Math.min(3, results.size());
        for (int i = 0; i < count; i++) {
            sb.append("- ").append(results.get(i).getSnippet(100)).append("\n");
        }
        return sb.toString();
    }

    private void writeConversationToMemory(String userInput, String response) {
        String summary = String.format("用户: %s\n助手: %s",
            truncate(userInput, 100),
            truncate(response, 100));
        memoryProvider.writeEphemeral(summary);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        text = text.replace("\n", " ").trim();
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    // ==================== Builder ====================

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private LLMProvider llmProvider;
        private MemoryProvider memoryProvider;
        private ToolRegistry toolRegistry = new ToolRegistry();
        private String systemPrompt;
        private int maxToolIterations = 10;
        private LLMOptions llmOptions = LLMOptions.builder().build();
        private List<AgentObserver> observers = new ArrayList<>();

        public Builder llmProvider(LLMProvider provider) {
            this.llmProvider = provider;
            return this;
        }

        public Builder memoryProvider(MemoryProvider provider) {
            this.memoryProvider = provider;
            return this;
        }

        public Builder toolRegistry(ToolRegistry registry) {
            this.toolRegistry = registry;
            return this;
        }

        public Builder addTool(Tool tool) {
            this.toolRegistry.register(tool);
            return this;
        }

        public Builder tools(List<Tool> tools) {
            this.toolRegistry.registerAll(tools);
            return this;
        }

        public Builder systemPrompt(String prompt) {
            this.systemPrompt = prompt;
            return this;
        }

        public Builder maxToolIterations(int max) {
            this.maxToolIterations = max;
            return this;
        }

        public Builder llmOptions(LLMOptions options) {
            this.llmOptions = options;
            return this;
        }

        public Builder addObserver(AgentObserver observer) {
            this.observers.add(observer);
            return this;
        }

        public AgentLoop build() {
            return new AgentLoop(this);
        }
    }
}
