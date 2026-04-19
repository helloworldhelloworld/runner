package com.lightweightai.kernel.testsupport;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
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

    private CapturingLLMProvider(String responseText, String stopReason) {
        this.responseText = responseText;
        this.stopReason = stopReason;
    }

    /** 常用工厂：返回一条普通文本并以 end_turn 结束（不触发 tool_use 循环） */
    public static CapturingLLMProvider endTurn(String text) {
        return new CapturingLLMProvider(text, "end_turn");
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
