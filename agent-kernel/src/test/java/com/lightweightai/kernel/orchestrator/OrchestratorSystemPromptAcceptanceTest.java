package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Acceptance: systemPrompt 从 AgentProfile → Orchestrator → AgentLoop → LLM messages 的完整传输链。
 *
 * 验证链路：
 * 1. Profile.systemPrompt → AgentFactory.create → AgentLoop 构建时注入
 * 2. AgentLoop.run → buildMessages → 第一条 SYSTEM 消息包含 systemPrompt
 * 3. CapturingLLMProvider 捕获实际发给 LLM 的 messages，断言 SYSTEM 消息内容
 *
 * 这是 toolDefinitions 传输链测试 (OrchestratorToolDefinitionsAcceptanceTest) 的姊妹件，
 * 补全 LLMOptions 之外"消息内容"维度的传输链覆盖。
 */
@DisplayName("Acceptance: Orchestrator 将 Profile.systemPrompt 传给 LLM SYSTEM 消息")
class OrchestratorSystemPromptAcceptanceTest {

    private ToolRegistry globalRegistry;

    @BeforeEach
    void setUp() {
        globalRegistry = new ToolRegistry();
        globalRegistry.register(noopTool("search"));
    }

    @Test
    @DisplayName("Profile 的 systemPrompt 出现在 LLM 收到的 SYSTEM 消息中")
    void systemPromptReachesLLM() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("custom-prompt")
                .systemPrompt("你是一个专业的翻译助手")
                .build());
        registry.setDefault("custom-prompt");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("已翻译");
        StubMemory memory = new StubMemory();
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-prompt")
                .message("translate this")
                .build();

        orchestrator.chatStreamReactive(request).collectList().block();

        List<ConversationMessage> messages = spy.lastMessages();
        assertNotNull(messages, "LLM should have received messages");

        ConversationMessage systemMsg = messages.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .findFirst().orElse(null);
        assertNotNull(systemMsg, "SYSTEM message must be present");
        assertTrue(systemMsg.getTextContent().contains("你是一个专业的翻译助手"),
                "SYSTEM message should contain the profile's systemPrompt; got: " + systemMsg.getTextContent());
    }

    @Test
    @DisplayName("不同 Agent 的 systemPrompt 互不干扰")
    void differentAgentsGetDifferentSystemPrompts() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("translator")
                .systemPrompt("你是翻译助手")
                .build());
        registry.register(AgentProfile.builder()
                .agentId("coder")
                .systemPrompt("你是编程助手")
                .build());
        registry.setDefault("translator");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        StubMemory memory = new StubMemory();
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        // Route to "translator"
        GatewayRequest req1 = GatewayRequest.builder()
                .sessionId("sess-t")
                .message("hi")
                .metadata("agentId", "translator")
                .build();
        orchestrator.chatStreamReactive(req1).collectList().block();

        List<ConversationMessage> msgs1 = spy.lastMessages();
        String sys1 = msgs1.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .map(ConversationMessage::getTextContent)
                .findFirst().orElse("");

        // Route to "coder"
        GatewayRequest req2 = GatewayRequest.builder()
                .sessionId("sess-c")
                .message("hi")
                .metadata("agentId", "coder")
                .build();
        orchestrator.chatStreamReactive(req2).collectList().block();

        List<ConversationMessage> msgs2 = spy.lastMessages();
        String sys2 = msgs2.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .map(ConversationMessage::getTextContent)
                .findFirst().orElse("");

        assertTrue(sys1.contains("翻译助手"), "translator agent should have 翻译助手 prompt");
        assertFalse(sys1.contains("编程助手"), "translator agent must NOT contain coder prompt");

        assertTrue(sys2.contains("编程助手"), "coder agent should have 编程助手 prompt");
        assertFalse(sys2.contains("翻译助手"), "coder agent must NOT contain translator prompt");
    }

    @Test
    @DisplayName("Orchestrator chatStreamReactive 发出 AGENT_ROUTE 事件且 data 携带 agentId")
    void agentRouteEventCarriesAgentId() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("route-test")
                .systemPrompt("test")
                .build());
        registry.setDefault("route-test");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");
        StubMemory memory = new StubMemory();
        AgentFactory factory = new AgentFactory(spy, memory, globalRegistry);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-route")
                .message("hi")
                .build();

        List<StreamEvent> events = orchestrator.chatStreamReactive(request).collectList().block();
        assertNotNull(events);

        StreamEvent routeEvent = events.stream()
                .filter(e -> e.getType() == StreamEvent.EventType.AGENT_ROUTE)
                .findFirst().orElse(null);
        assertNotNull(routeEvent, "AGENT_ROUTE event must be emitted");
        assertEquals("route-test", routeEvent.getData().get("agentId"),
                "AGENT_ROUTE event must carry the routed agentId");
        assertEquals("sess-route", routeEvent.getData().get("sessionId"),
                "AGENT_ROUTE event must carry the sessionId");
    }

    @Test
    @DisplayName("toolDefinitions 和 systemPrompt 同时正确传递")
    void toolDefinitionsAndSystemPromptBothTransmitted() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("full-check")
                .systemPrompt("完整检查助手")
                .toolDenyList(Set.of("search"))
                .build());
        registry.setDefault("full-check");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("checked");
        StubMemory memory = new StubMemory();

        ToolRegistry tools = new ToolRegistry();
        tools.register(noopTool("search"));
        tools.register(noopTool("read_file"));

        AgentFactory factory = new AgentFactory(spy, memory, tools);
        Orchestrator orchestrator = new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        GatewayRequest request = GatewayRequest.builder()
                .sessionId("sess-full")
                .message("check")
                .build();

        orchestrator.chatStreamReactive(request).collectList().block();

        // Verify systemPrompt in messages
        List<ConversationMessage> messages = spy.lastMessages();
        String systemText = messages.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .map(ConversationMessage::getTextContent)
                .findFirst().orElse("");
        assertTrue(systemText.contains("完整检查助手"), "systemPrompt must reach LLM messages");

        // Verify toolDefinitions in LLMOptions
        LLMOptions opts = spy.lastOptions();
        assertNotNull(opts);
        List<Map<String, Object>> defs = opts.getToolDefinitions();
        assertNotNull(defs);
        assertEquals(1, defs.size(), "search should be denied, only read_file remains");
        assertEquals("read_file", defs.get(0).get("name"));
    }

    private static Tool noopTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public com.lightweightai.kernel.llm.ToolResult execute(Map<String, Object> args) {
                return com.lightweightai.kernel.llm.ToolResult.success("ok");
            }
        };
    }

    private static class StubMemory implements MemoryProvider {
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
    }
}
