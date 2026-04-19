package com.lightweightai.kernel.testsupport;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ToolCall;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 测试用 LLMProvider：捕获每一次调用收到的 LLMOptions 与 messages，
 * 用于断言链路把载荷（toolDefinitions、systemPrompt、temperature、maxTokens 等）
 * 正确传递给了 provider。
 *
 * 用法：
 * <pre>
 *   CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");
 *   orchestrator.chatStreamReactive(request).collectList().block();
 *   LLMOptions opts = spy.lastOptions();
 *   assertEquals(2, opts.getToolDefinitions().size());
 * </pre>
 *
 * 这是 CLAUDE.md 推荐的共享测试工具 —— 所有跨层 UT 都应复用它，
 * 避免各测试文件里手搓匿名 LLMProvider 导致忘记 capture 入参这一反复出现的缺陷。
 */
public class CapturingLLMProvider implements LLMProvider {

    private final AtomicReference<LLMOptions> lastOptions = new AtomicReference<>();
    private final AtomicReference<List<ConversationMessage>> lastMessages = new AtomicReference<>();
    private final List<LLMOptions> optionsHistory = new CopyOnWriteArrayList<>();
    private final List<List<ConversationMessage>> messagesHistory = new CopyOnWriteArrayList<>();
    private final String responseText;
    private final String stopReason;
    /** 脚本化模式：每次调用依次返回一个响应（用于 tool_use → end_turn 多轮场景） */
    private final List<LLMResponse> scriptedResponses;
    private final AtomicInteger scriptedIndex = new AtomicInteger(0);

    private CapturingLLMProvider(String responseText, String stopReason) {
        this.responseText = responseText;
        this.stopReason = stopReason;
        this.scriptedResponses = null;
    }

    private CapturingLLMProvider(List<LLMResponse> scripted) {
        this.responseText = null;
        this.stopReason = null;
        this.scriptedResponses = scripted;
    }

    /** 常用工厂：返回一条普通文本并以 end_turn 结束（不触发 tool_use 循环） */
    public static CapturingLLMProvider endTurn(String text) {
        return new CapturingLLMProvider(text, "end_turn");
    }

    /**
     * 两轮脚本：第一轮发出 tool_use，第二轮 end_turn。
     * 用于验证 ToolCallingLoop 对 args/result 的传导。
     */
    public static CapturingLLMProvider toolThenEnd(String toolName, Map<String, Object> args,
                                                    String finalText) {
        ToolCall tc = new ToolCall("call_" + UUID.randomUUID(), toolName, args);
        LLMResponse first = LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent("")
                        .build())
                .toolCalls(List.of(tc))
                .stopReason("tool_use")
                .build();
        LLMResponse second = LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent(finalText)
                        .build())
                .stopReason("end_turn")
                .build();
        return new CapturingLLMProvider(List.of(first, second));
    }

    public LLMOptions lastOptions() { return lastOptions.get(); }
    public List<ConversationMessage> lastMessages() { return lastMessages.get(); }
    public List<LLMOptions> optionsHistory() { return List.copyOf(optionsHistory); }
    public List<List<ConversationMessage>> messagesHistory() { return List.copyOf(messagesHistory); }
    public int callCount() { return optionsHistory.size(); }

    private void capture(List<ConversationMessage> messages, LLMOptions options) {
        lastOptions.set(options);
        lastMessages.set(messages);
        optionsHistory.add(options);
        messagesHistory.add(messages);
    }

    private LLMResponse buildResponse() {
        if (scriptedResponses != null) {
            int idx = scriptedIndex.getAndIncrement();
            if (idx >= scriptedResponses.size()) {
                // 超出脚本时返回最后一条的 end_turn 版本，避免循环越界
                return scriptedResponses.get(scriptedResponses.size() - 1);
            }
            return scriptedResponses.get(idx);
        }
        return LLMResponse.builder()
                .message(ConversationMessage.builder()
                        .role(ConversationMessage.MessageRole.ASSISTANT)
                        .textContent(responseText)
                        .build())
                .stopReason(stopReason)
                .build();
    }

    @Override
    public LLMResponse complete(List<ConversationMessage> messages, LLMOptions options) {
        capture(messages, options);
        return buildResponse();
    }

    @Override
    public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> messages, LLMOptions options) {
        return CompletableFuture.completedFuture(complete(messages, options));
    }

    @Override
    public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> messages, LLMOptions options,
                                                        StreamEventHandler handler) {
        capture(messages, options);
        LLMResponse r = buildResponse();
        handler.onComplete(r);
        return CompletableFuture.completedFuture(r);
    }

    @Override
    public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> messages, LLMOptions options) {
        capture(messages, options);
        return Flux.just(StreamEvent.llmComplete(buildResponse()));
    }

    @Override
    public ModelCapability getModelCapability() { return null; }

    @Override
    public String getProviderName() { return "capturing-spy"; }
}
