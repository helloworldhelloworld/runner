package com.lightweightai.kernel.core;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 传输链测试：ToolRegistry → AgentLoop.llmOptions.toolDefinitions → LLMProvider.
 *
 * AgentLoop 在构造时检查：若调用方未显式提供 toolDefinitions，则从 ToolRegistry 自动注入。
 * 此测试验证该传输链在 AgentLoop.run 路径上端到端成立——
 * CapturingLLMProvider 捕获实际发给 LLM 的 LLMOptions，断言 toolDefinitions 载荷。
 */
@DisplayName("AgentLoop toolDefinitions 传输链：Registry → LLMOptions → Provider")
class AgentLoopToolDefinitionTransmissionTest {

    @Test
    @DisplayName("ToolRegistry 中注册的工具出现在 Provider 收到的 LLMOptions.toolDefinitions")
    void registeredToolsReachProvider() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(dummyTool("calculator"));
        registry.register(dummyTool("web_search"));

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("result");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new NoOpMemory())
                .toolRegistry(registry)
                .build();

        agent.run("2+2", "sess-td");

        LLMOptions received = spy.lastOptions();
        assertNotNull(received, "Provider should have received LLMOptions");
        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertNotNull(defs, "toolDefinitions should not be null");
        assertEquals(2, defs.size(), "Both registered tools should be present");

        List<String> names = defs.stream().map(d -> (String) d.get("name")).sorted().toList();
        assertEquals(List.of("calculator", "web_search"), names);
    }

    @Test
    @DisplayName("空 ToolRegistry → Provider 收到的 toolDefinitions 为空列表")
    void emptyRegistryProducesEmptyDefinitions() {
        ToolRegistry registry = new ToolRegistry();
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new NoOpMemory())
                .toolRegistry(registry)
                .build();

        agent.run("hi", "sess-empty");

        LLMOptions received = spy.lastOptions();
        assertNotNull(received);
        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertTrue(defs == null || defs.isEmpty(),
                "Empty registry should produce empty/null toolDefinitions");
    }

    @Test
    @DisplayName("调用方已显式提供 toolDefinitions 时，AgentLoop 不覆盖")
    void explicitToolDefinitionsNotOverridden() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(dummyTool("from_registry"));

        List<Map<String, Object>> explicit = List.of(
                Map.of("name", "explicit_tool", "description", "provided by caller"));

        LLMOptions callerOptions = LLMOptions.builder()
                .toolDefinitions(explicit)
                .build();

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new NoOpMemory())
                .toolRegistry(registry)
                .llmOptions(callerOptions)
                .build();

        agent.run("hi", "sess-explicit");

        LLMOptions received = spy.lastOptions();
        assertNotNull(received);
        List<Map<String, Object>> defs = received.getToolDefinitions();
        assertEquals(1, defs.size());
        assertEquals("explicit_tool", defs.get(0).get("name"),
                "Caller-provided toolDefinitions should take precedence over registry");
    }

    @Test
    @DisplayName("systemPrompt 注入到 LLM 收到的 SYSTEM 消息")
    void systemPromptAppearsInMessages() {
        ToolRegistry registry = new ToolRegistry();
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new NoOpMemory())
                .toolRegistry(registry)
                .systemPrompt("你是一个测试助手")
                .build();

        agent.run("你好", "sess-sys");

        List<ConversationMessage> msgs = spy.lastMessages();
        assertNotNull(msgs);

        ConversationMessage systemMsg = msgs.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.SYSTEM)
                .findFirst().orElse(null);
        assertNotNull(systemMsg, "SYSTEM message must be present in LLM call");
        assertTrue(systemMsg.getTextContent().contains("你是一个测试助手"),
                "SYSTEM message should contain the configured systemPrompt; got: "
                + systemMsg.getTextContent());
    }

    @Test
    @DisplayName("用户输入出现在 LLM 收到的 USER 消息中")
    void userInputAppearsInMessages() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("pong");

        AgentLoop agent = AgentLoop.builder()
                .llmProvider(spy)
                .memoryProvider(new NoOpMemory())
                .toolRegistry(new ToolRegistry())
                .systemPrompt("test")
                .build();

        agent.run("ping", "sess-user");

        List<ConversationMessage> msgs = spy.lastMessages();
        boolean hasUserMsg = msgs.stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.USER)
                .anyMatch(m -> m.getTextContent().contains("ping"));
        assertTrue(hasUserMsg, "User input 'ping' should appear in messages sent to LLM");
    }

    private static Tool dummyTool(String name) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " description"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }

    private static class NoOpMemory implements MemoryProvider {
        @Override public void addMessage(String sessionId, Message message) {}
        @Override public List<Message> getHistory(String sessionId, int limit) { return List.of(); }
        @Override public void clearSession(String sessionId) {}
        @Override public void writeEphemeral(String content) {}
        @Override public void writeDurable(String section, String content) {}
        @Override public List<MemorySearchResult> search(String query) { return List.of(); }
    }
}
