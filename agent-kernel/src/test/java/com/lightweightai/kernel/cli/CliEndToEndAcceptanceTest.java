package com.lightweightai.kernel.cli;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.llm.LLMResponse;
import com.lightweightai.kernel.llm.ModelCapability;
import com.lightweightai.kernel.llm.ToolCall;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.orchestrator.AgentFactory;
import com.lightweightai.kernel.orchestrator.MetadataAgentRouter;
import com.lightweightai.kernel.orchestrator.Orchestrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outside-in E2E: CliTool + LocalProcessExecutor 穿越 agent 全链路
 *
 * Orchestrator → AgentFactory → ScopedToolRegistry → ToolCallingLoop → ToolExecutor
 *   → CliTool(默认构造,内嵌 LocalProcessExecutor)→ 真实 bash 子进程 → stdout → ToolResult
 */
@DisplayName("E2E: CliTool 全链路(默认 LocalProcessExecutor + 真实 bash)")
class CliEndToEndAcceptanceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("LLM 调 CLI 工具 → LocalProcessExecutor 启 bash → stdout 回到 assistant 消息")
    void cliToolExecutesThroughAgentPipeline() throws IOException {
        // 1. 准备一个 echo 脚本 + manifest
        Path cliDir = tempDir.resolve("greet");
        Files.createDirectories(cliDir.resolve("bin"));
        Path bin = cliDir.resolve("bin/greet");
        Files.writeString(bin, "#!/bin/bash\necho \"hi from cli $1\"\n");
        bin.toFile().setExecutable(true);
        Files.writeString(cliDir.resolve("cli-manifest.json"), """
                {"name":"greet","version":"1.0.0","description":"greet cli","entry_point":"bin/greet","output_format":"text"}
                """);

        // 2. 通过 CliToolSourceProvider(默认 LocalProcessExecutor)注册到全局 registry
        ToolRegistry global = new ToolRegistry();
        new CliToolSourceProvider(tempDir).start(global);
        assertTrue(global.has("greet"), "CLI 应已注册");

        // 3. Agent profile 无权限限制,用默认 ToolPolicy(allowAll)
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("cli-agent").build());
        registry.setDefault("cli-agent");

        // 4. 记录 assistant 最终消息 + tool result content
        List<String> toolResults = new CopyOnWriteArrayList<>();

        AgentFactory factory = new AgentFactory(
                llmThatRequestsGreet(toolResults),
                new NoopMemory(),
                global);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // 5. 触发 agent,收集事件
        List<StreamEvent> events = orchestrator.chatStreamReactive(
                GatewayRequest.builder().sessionId("s").message("greet me").build()
        ).collectList().block();

        assertNotNull(events);

        // 6. 断言真实 bash 输出到达了 assistant 消息路径
        boolean greeted = toolResults.stream().anyMatch(r -> r.contains("hi from cli world"));
        assertTrue(greeted, "tool result 应含真实 bash stdout(hi from cli world)。实际: " + toolResults);
    }

    /** 第一轮 LLM 请求调 greet 工具,第二轮收到结果后收尾 */
    private LLMProvider llmThatRequestsGreet(List<String> toolResultContents) {
        return new LLMProvider() {
            int call = 0;
            @Override public Flux<StreamEvent> completeStreamReactive(List<ConversationMessage> m, LLMOptions o) {
                call++;
                if (call == 1) {
                    ToolCall tc = new ToolCall("tc_1", "greet", Map.of("args", "world"));
                    return Flux.just(
                            StreamEvent.textDelta("calling greet"),
                            StreamEvent.llmComplete(LLMResponse.builder()
                                    .message(ConversationMessage.builder()
                                            .role(ConversationMessage.MessageRole.ASSISTANT)
                                            .textContent("calling greet").build())
                                    .toolCalls(List.of(tc))
                                    .stopReason("tool_use").build()));
                }
                // 第二轮:读取 messages 里的 tool 结果,作为断言依据
                m.forEach(msg -> {
                    if (msg.getRole() == ConversationMessage.MessageRole.TOOL && msg.getTextContent() != null) {
                        toolResultContents.add(msg.getTextContent());
                    }
                });
                return Flux.just(StreamEvent.llmComplete(LLMResponse.builder()
                        .message(ConversationMessage.builder()
                                .role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("done").build())
                        .stopReason("end_turn").build()));
            }
            @Override public LLMResponse complete(List<ConversationMessage> m, LLMOptions o) {
                return LLMResponse.builder().stopReason("end_turn")
                        .message(ConversationMessage.builder().role(ConversationMessage.MessageRole.ASSISTANT)
                                .textContent("done").build()).build();
            }
            @Override public CompletableFuture<LLMResponse> completeAsync(List<ConversationMessage> m, LLMOptions o) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public CompletableFuture<LLMResponse> completeStream(List<ConversationMessage> m, LLMOptions o, LLMProvider.StreamEventHandler h) {
                return CompletableFuture.completedFuture(complete(m, o)); }
            @Override public ModelCapability getModelCapability() { return null; }
            @Override public String getProviderName() { return "e2e-cli"; }
        };
    }

    private static class NoopMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
