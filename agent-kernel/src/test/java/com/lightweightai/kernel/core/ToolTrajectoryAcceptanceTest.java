package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.AgentObserver;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in Acceptance: Observer 收到的 tool 调用轨迹应保留结构。
 *
 * 之前 ToolCallingLoop 把 Map.of() 当作 args 传给 onPostToolUse,
 * 导致观察者无法获得原始 Map&lt;String, Object&gt; 结构。
 */
@DisplayName("Acceptance: Tool 调用轨迹保留结构")
class ToolTrajectoryAcceptanceTest {

    @Test
    @DisplayName("onPostToolUse 接收的 args 应等于 LLM 原始 ToolCall.arguments(非空 Map)")
    void postToolUseCarriesOriginalArgs() {
        Map<String, Object> originalArgs = Map.of("city", "Tokyo", "unit", "metric");
        Tool weather = simpleTool("weather", "ok-result");
        ToolRegistry registry = new ToolRegistry();
        registry.register(weather);
        ToolExecutor executor = new ToolExecutor(registry);

        // Observer 边走边记
        List<Map<String, Object>> recordedArgs = new CopyOnWriteArrayList<>();
        List<ToolResult> recordedResults = new CopyOnWriteArrayList<>();
        AgentObserver recorder = new AgentObserver() {
            @Override public void onPostToolUse(String name, Map<String, Object> args, ToolResult result) {
                recordedArgs.add(args);
                recordedResults.add(result);
            }
        };

        // LLM 第一轮请求 weather,第二轮收尾
        LLMProvider llm = new LLMProvider() {
            int call = 0;
            @Override public Flux<com.lightweightai.kernel.core.StreamEvent>
                    completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
                call++;
                if (call == 1) {
                    ToolCall tc = new ToolCall("tc-x", "weather", originalArgs);
                    return Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                            .message(ConversationMessage.builder()
                                    .role(ConversationMessage.MessageRole.ASSISTANT)
                                    .textContent("checking").build())
                            .toolCalls(List.of(tc))
                            .stopReason("tool_use").build()));
                }
                return Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                        .message(ConversationMessage.builder()
                                .role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("done").build())
                        .stopReason("end_turn").build()));
            }
            @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                return LLMResponse.builder().stopReason("end_turn").message(
                        ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("done").build()).build();
            }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, LLMProvider.StreamEventHandler h) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "trajectory-test"; }
        };

        ToolCallingLoop loop = ToolCallingLoop.builder()
                .provider(llm).toolExecutor(executor).maxIterations(5)
                .observers(List.of(recorder))
                .build();

        loop.executeWithToolsReactive(new ArrayList<>(), LLMOptions.builder().build())
                .collectList().block();

        assertEquals(1, recordedArgs.size(), "应收到一次 PostToolUse");
        assertEquals(originalArgs, recordedArgs.get(0),
                "observer 收到的 args 必须与 ToolCall 原 arguments 严格相等(保留 Map 结构),实际: " + recordedArgs);
        assertNotNull(recordedResults.get(0));
        assertEquals("ok-result", recordedResults.get(0).getContent());
    }

    @Test
    @DisplayName("ToolCallRecord 类型化构造:暴露 getArgumentsMap / getResultObject")
    void toolCallRecordExposesTypedAccessors() {
        Map<String, Object> args = Map.of("k", 7);
        ToolResult result = ToolResult.success("done");

        com.lightweightai.kernel.agent.AgentResponse.ToolCallRecord record =
                new com.lightweightai.kernel.agent.AgentResponse.ToolCallRecord("calc", args, result);

        assertEquals("calc", record.getToolName());
        assertEquals(args, record.getArgumentsMap(), "类型化 args 应原样返回");
        assertSame(result, record.getResultObject(), "类型化 result 应原引用返回");
    }

    @Test
    @DisplayName("ToolCallRecord 旧 String 构造仍可用(向后兼容)")
    void legacyStringConstructorStillWorks() {
        @SuppressWarnings("deprecation")
        com.lightweightai.kernel.agent.AgentResponse.ToolCallRecord record =
                new com.lightweightai.kernel.agent.AgentResponse.ToolCallRecord("calc", "{\"x\":1}", "42");

        assertEquals("calc", record.getToolName());
        assertEquals("{\"x\":1}", record.getArguments());
        assertEquals("42", record.getResult());
    }

    private Tool simpleTool(String name, String content) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success(content);
            }
        };
    }
}
