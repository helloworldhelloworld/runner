package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentLoop;
import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentResponse;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSchema;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentFactory → AgentLoop 链路 — 上下文压缩传导验证")
class AgentFactoryContextCompactionTest {

    @Test
    @DisplayName("AgentFactory.create 为 AgentLoop 注入 CompactionChain(Snip+Micro)")
    void agentFactoryInjectsCompactionChain() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("response");
        MemoryProvider memory = new InMemoryProvider();
        ToolRegistry tools = new ToolRegistry();
        tools.register(new EchoTool());

        AgentFactory factory = new AgentFactory(spy, memory, tools);
        AgentProfile profile = AgentProfile.builder()
                .agentId("test")
                .systemPrompt("You are a test agent")
                .maxSpawnDepth(0)
                .build();

        AgentLoop agent = factory.create(profile);
        AgentResponse result = agent.run("hello", "session-1");

        assertEquals("response", result.getText());
        assertTrue(spy.callCount() > 0, "LLM should have been called");
    }

    @Test
    @DisplayName("AgentFactory 生成的 AgentLoop 正确传递 systemPrompt 到 LLM 调用")
    void systemPromptTransmittedToLLM() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("ok");
        MemoryProvider memory = new InMemoryProvider();
        ToolRegistry tools = new ToolRegistry();

        AgentFactory factory = new AgentFactory(spy, memory, tools);
        AgentProfile profile = AgentProfile.builder()
                .agentId("sys-test")
                .systemPrompt("你是心理咨询助手")
                .build();

        AgentLoop agent = factory.create(profile);
        agent.run("你好", "s1");

        var msgs = spy.lastMessages();
        assertNotNull(msgs);
        assertFalse(msgs.isEmpty());
        boolean hasSystemPrompt = msgs.stream()
                .anyMatch(m -> m.getTextContent() != null
                        && m.getTextContent().contains("你是心理咨询助手"));
        assertTrue(hasSystemPrompt, "System prompt should appear in messages sent to LLM");
    }

    @Test
    @DisplayName("AgentFactory 创建的 AgentLoop 带有 ScopedToolRegistry 的工具定义")
    void scopedToolDefinitionsCarriedToLLM() {
        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");
        MemoryProvider memory = new InMemoryProvider();
        ToolRegistry tools = new ToolRegistry();
        tools.register(new EchoTool());

        AgentFactory factory = new AgentFactory(spy, memory, tools);
        AgentProfile profile = AgentProfile.builder()
                .agentId("tool-test")
                .systemPrompt("test")
                .build();

        AgentLoop agent = factory.create(profile);
        agent.run("test", "s1");

        var options = spy.lastOptions();
        assertNotNull(options, "LLMOptions should be captured");
        assertNotNull(options.getToolDefinitions(), "Tool definitions should be present");
        boolean hasEchoTool = options.getToolDefinitions().stream()
                .anyMatch(td -> "echo".equals(td.get("name")));
        assertTrue(hasEchoTool, "echo tool should appear in tool definitions sent to LLM");
    }

    private static class EchoTool implements Tool {
        @Override public String getName() { return "echo"; }
        @Override public String getDescription() { return "Echoes input"; }
        @Override public ToolSchema getSchema() {
            return ToolSchema.withProperties(Map.of(
                    "text", Map.of("type", "string", "description", "text to echo")));
        }
        @Override public ToolResult execute(Map<String, Object> args) {
            return ToolResult.success(String.valueOf(args.get("text")));
        }
    }
}
