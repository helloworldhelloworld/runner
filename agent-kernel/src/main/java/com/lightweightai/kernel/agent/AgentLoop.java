package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.prompt.PromptContext;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.prompt.PromptRequest;
import com.lightweightai.kernel.prompt.Skill;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.Collections;

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
    private final Map<String, Tool> tools;
    private final String systemPrompt;
    private final int maxToolIterations;
    private final LLMOptions llmOptions;
    private final List<AgentObserver> observers;

    private AgentLoop(Builder builder) {
        this.llmProvider = Objects.requireNonNull(builder.llmProvider, "llmProvider required");
        this.memoryProvider = Objects.requireNonNull(builder.memoryProvider, "memoryProvider required");
        this.tools = new HashMap<>(builder.tools);
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
        for (Tool tool : tools.values()) {
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
     */
    public AgentResponse run(String input, String sessionId) {
        // 1. 检索相关记忆
        List<MemorySearchResult> memoryResults = memoryProvider.search(input);
        String memoryContext = formatMemoryContext(memoryResults);

        // 2. 保存用户消息
        memoryProvider.addMessage(sessionId, Message.user(input));

        // 3. 构建消息上下文
        List<ConversationMessage> messages = buildMessages(sessionId, memoryContext);

        // 4. Tool 循环
        List<AgentResponse.ToolCallRecord> allToolCalls = new ArrayList<>();
        int iterations = 0;

        while (iterations < maxToolIterations) {
            // 调用 LLM
            LLMResponse llmResponse = llmProvider.complete(messages, llmOptions);
            ConversationMessage assistantMsg = llmResponse.getMessage();

            // 检查是否有 Tool 调用
            if (!hasToolUse(assistantMsg)) {
                // 无 Tool 调用，结束循环
                String responseText = assistantMsg.getTextContent();

                // 保存助手消息到记忆
                memoryProvider.addMessage(sessionId, Message.assistant(responseText));

                // 写入 Ephemeral 记忆（对话摘要）
                writeConversationToMemory(input, responseText);

                return AgentResponse.builder()
                    .text(responseText)
                    .toolCalls(allToolCalls)
                    .stopReason(llmResponse.getStopReason())
                    .build();
            }

            // 有 Tool 调用，执行工具
            List<ToolUse> toolUses = extractToolUses(assistantMsg);
            List<ToolResult> toolResults = new ArrayList<>();

            for (ToolUse toolUse : toolUses) {
                Tool tool = tools.get(toolUse.getName());
                if (tool == null) {
                    toolResults.add(ToolResult.error(toolUse.getId(),
                        "Unknown tool: " + toolUse.getName()));
                    continue;
                }

                try {
                    ToolResult result = tool.execute(toolUse.getInput());
                    toolResults.add(result);
                    allToolCalls.add(new AgentResponse.ToolCallRecord(
                        toolUse.getName(), toolUse.getInput().toString(), result.getContent()));
                } catch (Exception e) {
                    toolResults.add(ToolResult.error(toolUse.getId(), e));
                }
            }

            // 添加助手消息和工具结果到上下文
            messages.add(assistantMsg);
            messages.add(createToolResultMessage(toolResults));

            iterations++;
        }

        // 达到最大迭代次数
        return AgentResponse.builder()
            .text("Reached maximum tool iterations")
            .toolCalls(allToolCalls)
            .stopReason("max_iterations")
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

    private boolean hasToolUse(ConversationMessage msg) {
        // 检查消息元数据中是否有 tool_uses
        Object toolUses = msg.getMetadata().get("tool_uses");
        return toolUses != null && toolUses instanceof List && !((List<?>) toolUses).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<ToolUse> extractToolUses(ConversationMessage msg) {
        Object toolUses = msg.getMetadata().get("tool_uses");
        if (toolUses == null || !(toolUses instanceof List)) {
            return Collections.emptyList();
        }
        return (List<ToolUse>) toolUses;
    }

    private ConversationMessage createToolResultMessage(List<ToolResult> results) {
        // 将工具结果格式化为文本
        StringBuilder sb = new StringBuilder();
        for (ToolResult result : results) {
            sb.append("Tool result for ").append(result.getToolUseId()).append(":\n");
            sb.append(result.getContent()).append("\n\n");
        }

        return ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(sb.toString().trim())
            .build();
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
        private Map<String, Tool> tools = new HashMap<>();
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

        public Builder addTool(Tool tool) {
            this.tools.put(tool.getName(), tool);
            return this;
        }

        public Builder tools(List<Tool> tools) {
            for (Tool tool : tools) {
                this.tools.put(tool.getName(), tool);
            }
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
