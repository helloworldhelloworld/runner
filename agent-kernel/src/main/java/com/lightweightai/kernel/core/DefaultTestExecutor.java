package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 默认测试执行器
 *
 * 复用 ToolCallingLoop 对真实 LLM 执行单个测试用例：
 * 1. 从 context.mockResponses 构建 MockToolRegistry
 * 2. 构建 ToolCallingLoop(llmProvider, mockToolExecutor)
 * 3. 组装 messages: [system(systemPrompt), user(userInput)]
 * 4. 执行 loop.executeWithToolsReactive()
 * 5. 收集全文 + 工具调用信息，追加 POST_PROCESS_DATA("test_case_result") 汇总事件
 */
public class DefaultTestExecutor implements TestExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTestExecutor.class);

    private final LLMProvider llmProvider;
    private final int maxToolIterations;

    public DefaultTestExecutor(LLMProvider llmProvider) {
        this(llmProvider, 3);
    }

    public DefaultTestExecutor(LLMProvider llmProvider, int maxToolIterations) {
        if (llmProvider == null) {
            throw new IllegalArgumentException("LLMProvider cannot be null");
        }
        this.llmProvider = llmProvider;
        this.maxToolIterations = maxToolIterations;
    }

    @Override
    public Flux<StreamEvent> executeTestCase(SkillTestContext context) {
        return Flux.defer(() -> {
            long startTime = System.currentTimeMillis();

            // Build mock tool registry from context
            MockToolRegistry mockRegistry = new MockToolRegistry(context.getMockResponses());
            ToolRegistry registry = mockRegistry.getRegistry();
            ToolExecutor toolExecutor = new ToolExecutor(registry);

            // Build ToolCallingLoop
            ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(llmProvider)
                .toolExecutor(toolExecutor)
                .maxIterations(maxToolIterations)
                .build();

            // Build messages
            List<ConversationMessage> messages = buildMessages(context);

            // Build LLM options with tool definitions
            LLMOptions.Builder optionsBuilder = LLMOptions.builder();
            if (!context.getToolDefinitions().isEmpty()) {
                optionsBuilder.toolDefinitions(context.getToolDefinitions());
            } else if (registry.enabledCount() > 0) {
                optionsBuilder.toolDefinitions(registry.getToolDefinitions());
            }
            LLMOptions options = optionsBuilder.build();

            // Accumulate text and tool calls for the result summary
            StringBuilder fullText = new StringBuilder();
            List<Map<String, Object>> toolCallRecords = new ArrayList<>();

            return loop.executeWithToolsReactive(messages, options)
                .doOnNext(event -> {
                    if (event.getType() == StreamEvent.EventType.TEXT_DELTA && event.getTextDelta() != null) {
                        fullText.append(event.getTextDelta());
                    }
                    if (event.getType() == StreamEvent.EventType.TOOL_CALL_START && event.getToolCall() != null) {
                        Map<String, Object> record = new HashMap<>();
                        record.put("name", event.getToolCall().getName());
                        record.put("arguments", event.getToolCall().getArguments());
                        toolCallRecords.add(record);
                    }
                })
                .concatWith(Flux.defer(() -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    Map<String, Object> resultData = new LinkedHashMap<>();
                    resultData.put("testCaseId", context.getTestCaseId());
                    resultData.put("llmOutput", fullText.toString());
                    resultData.put("toolCalls", toolCallRecords);
                    resultData.put("durationMs", durationMs);
                    return Flux.just(StreamEvent.postProcessData("test_case_result", resultData));
                }))
                .onErrorResume(error -> {
                    logger.warn("Test case {} execution failed: {}", context.getTestCaseId(), error.getMessage());
                    long durationMs = System.currentTimeMillis() - startTime;
                    Map<String, Object> resultData = new LinkedHashMap<>();
                    resultData.put("testCaseId", context.getTestCaseId());
                    resultData.put("llmOutput", fullText.toString());
                    resultData.put("toolCalls", toolCallRecords);
                    resultData.put("durationMs", durationMs);
                    resultData.put("error", error.getMessage());
                    return Flux.just(StreamEvent.postProcessData("test_case_result", resultData));
                });
        });
    }

    private List<ConversationMessage> buildMessages(SkillTestContext context) {
        List<ConversationMessage> messages = new ArrayList<>();

        // System prompt (skill definition)
        if (context.getSystemPrompt() != null && !context.getSystemPrompt().isBlank()) {
            String systemContent = context.getSystemPrompt();
            if (context.getMemoryContext() != null && !context.getMemoryContext().isBlank()) {
                systemContent += "\n\n---\n\n# 上下文记忆\n" + context.getMemoryContext();
            }
            messages.add(ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.SYSTEM)
                .textContent(systemContent)
                .build());
        }

        // User input
        messages.add(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent(context.getUserInput())
            .build());

        return messages;
    }
}
