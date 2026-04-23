package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.context.CompactionChain;
import com.lightweightai.kernel.context.MicroCompactor;
import com.lightweightai.kernel.context.SnipCompactor;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.LLMOptions;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 传导链测试：AgentLoop → ToolCallingLoop → LLMProvider
 *
 * 对标 CLAUDE.md Rule #3: 每个 producer→consumer 对需要 ≥1 个 transmission test。
 * 使用 CapturingLLMProvider 验证载荷实际到��了 LLM provider 层。
 */
@DisplayName("AgentLoop → LLMProvider 传导链")
class AgentLoopTransmissionChainTest {

    private MemoryProvider memory;

    @BeforeEach
    void setUp() {
        memory = new InMemoryProvider();
    }

    @Nested
    @DisplayName("toolDefinitions 传导")
    class ToolDefinitionsTransmission {

        @Test
        @DisplayName("ToolRegistry 中的工具定义到达 LLMProvider.complete() 的 LLMOptions")
        void toolDefinitionsReachProvider() {
            CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

            Tool searchTool = simpleTool("search", "search the web");
            Tool readTool = simpleTool("read_file", "read a file");

            AgentLoop loop = AgentLoop.builder()
                    .llmProvider(spy)
                    .memoryProvider(memory)
                    .addTool(searchTool)
                    .addTool(readTool)
                    .systemPrompt("test")
                    .build();

            loop.run("hello", "s1");

            LLMOptions captured = spy.lastOptions();
            assertNotNull(captured, "LLMOptions should be captured");
            List<Map<String, Object>> defs = captured.getToolDefinitions();
            assertNotNull(defs, "toolDefinitions should not be null");
            assertEquals(2, defs.size());

            List<String> names = defs.stream().map(d -> (String) d.get("name")).toList();
            assertTrue(names.contains("search"));
            assertTrue(names.contains("read_file"));
        }

        @Test
        @DisplayName("ScopedToolRegistry deny 后的过滤结果传到 provider")
        void scopedToolFilteringReachesProvider() {
            CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

            ToolRegistry global = new ToolRegistry();
            global.register(simpleTool("search", "search"));
            global.register(simpleTool("shell_exec", "exec commands"));
            global.register(simpleTool("read_file", "read"));

            AgentProfile profile = AgentProfile.builder()
                    .agentId("safe-agent")
                    .systemPrompt("safe")
                    .toolDenyList(java.util.Set.of("shell_exec"))
                    .build();

            ScopedToolRegistry scoped = new ScopedToolRegistry(global, profile);

            AgentLoop loop = AgentLoop.builder()
                    .llmProvider(spy)
                    .memoryProvider(memory)
                    .toolRegistry(scoped)
                    .systemPrompt("test")
                    .build();

            loop.run("hello", "s1");

            List<Map<String, Object>> defs = spy.lastOptions().getToolDefinitions();
            assertEquals(2, defs.size());
            List<String> names = defs.stream().map(d -> (String) d.get("name")).toList();
            assertFalse(names.contains("shell_exec"), "denied tool must not reach provider");
            assertTrue(names.contains("search"));
            assertTrue(names.contains("read_file"));
        }
    }

    @Nested
    @DisplayName("systemPrompt 传导")
    class SystemPromptTransmission {

        @Test
        @DisplayName("systemPrompt 作为 SYSTEM 消息到达 provider")
        void systemPromptReachesProvider() {
            CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

            AgentLoop loop = AgentLoop.builder()
                    .llmProvider(spy)
                    .memoryProvider(memory)
                    .systemPrompt("你是一个温暖的心���咨询师")
                    .build();

            loop.run("你好", "s1");

            List<ConversationMessage> messages = spy.lastMessages();
            assertNotNull(messages);
            assertFalse(messages.isEmpty());

            ConversationMessage systemMsg = messages.get(0);
            assertEquals(ConversationMessage.MessageRole.SYSTEM, systemMsg.getRole());
            assertTrue(systemMsg.getTextContent().contains("温暖的心理咨询师"),
                    "system prompt payload should arrive at provider");
        }
    }

    @Nested
    @DisplayName("userMessage 传导")
    class UserMessageTransmission {

        @Test
        @DisplayName("用户消息作为 USER message 到达 provider")
        void userMessageReachesProvider() {
            CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

            AgentLoop loop = AgentLoop.builder()
                    .llmProvider(spy)
                    .memoryProvider(memory)
                    .systemPrompt("test")
                    .build();

            loop.run("帮我分析一下这个代码", "s1");

            List<ConversationMessage> messages = spy.lastMessages();
            boolean hasUserMsg = messages.stream()
                    .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.USER
                            && "帮我分析一下这个代码".equals(m.getTextContent()));
            assertTrue(hasUserMsg, "user message payload should arrive at provider");
        }
    }

    @Nested
    @DisplayName("contextCompactor 传导")
    class CompactorTransmission {

        @Test
        @DisplayName("CompactionChain 压缩后的消��到达 provider（大 TOOL 被截断）")
        void compactedMessagesReachProvider() {
            CapturingLLMProvider spy = CapturingLLMProvider.endTurn("done");

            AgentLoop loop = AgentLoop.builder()
                    .llmProvider(spy)
                    .memoryProvider(memory)
                    .systemPrompt("test")
                    .contextCompactor(new CompactionChain(
                            new SnipCompactor(3),
                            new MicroCompactor(100)
                    ))
                    .build();

            // Pre-populate history: 4 rounds + large TOOL result in early round
            memory.addMessage("s1", com.lightweightai.kernel.memory.Message.user("q1"));
            memory.addMessage("s1", com.lightweightai.kernel.memory.Message.assistant("a1"));
            memory.addMessage("s1", com.lightweightai.kernel.memory.Message.user("q2"));
            memory.addMessage("s1", com.lightweightai.kernel.memory.Message.assistant("a2"));
            memory.addMessage("s1", com.lightweightai.kernel.memory.Message.user("q3"));
            memory.addMessage("s1", com.lightweightai.kernel.memory.Message.assistant("a3"));

            loop.run("q4", "s1");

            List<ConversationMessage> messages = spy.lastMessages();
            assertNotNull(messages);
            assertTrue(messages.size() >= 2, "at least system + user");
        }
    }

    @Nested
    @DisplayName("tool_use 循环传导")
    class ToolUseTransmission {

        @Test
        @DisplayName("tool_use → tool execution → result 传回 provider 的第二轮调用")
        void toolResultFlowsBackToProvider() {
            CapturingLLMProvider spy = CapturingLLMProvider.toolThenEnd(
                    "search", Map.of("query", "weather"), "最终答案");

            Tool searchTool = new Tool() {
                @Override public String getName() { return "search"; }
                @Override public String getDescription() { return "search"; }
                @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
                @Override public ToolResult execute(Map<String, Object> args) {
                    assertEquals("weather", args.get("query"),
                            "tool should receive the args from LLM");
                    return ToolResult.success("晴天 25°C");
                }
            };

            AgentLoop loop = AgentLoop.builder()
                    .llmProvider(spy)
                    .memoryProvider(memory)
                    .addTool(searchTool)
                    .systemPrompt("test")
                    .build();

            AgentResponse result = loop.run("天气如何", "s1");

            assertEquals(2, spy.callCount(), "provider should be called twice (tool_use + end_turn)");
            assertEquals("最终答案", result.getText());

            List<ConversationMessage> secondCallMsgs = spy.messagesHistory().get(1);
            boolean hasToolResult = secondCallMsgs.stream()
                    .anyMatch(m -> m.getRole() == ConversationMessage.MessageRole.TOOL
                            && m.getTextContent() != null
                            && m.getTextContent().contains("晴天"));
            assertTrue(hasToolResult,
                    "tool execution result should be appended to messages in second LLM call");
        }
    }

    @Nested
    @DisplayName("Observer 传导")
    class ObserverTransmission {

        @Test
        @DisplayName("AgentObserver 收到 LLMResponse 中的实际 stopReason")
        void observerReceivesStopReason() {
            CapturingLLMProvider spy = CapturingLLMProvider.endTurn("response");

            var capturedResponses = new java.util.ArrayList<com.lightweightai.kernel.llm.LLMResponse>();

            AgentLoop loop = AgentLoop.builder()
                    .llmProvider(spy)
                    .memoryProvider(memory)
                    .systemPrompt("test")
                    .addObserver(new AgentObserver() {
                        @Override
                        public void onLLMResponse(com.lightweightai.kernel.llm.LLMResponse response) {
                            capturedResponses.add(response);
                        }
                    })
                    .build();

            loop.run("test", "s1");

            assertEquals(1, capturedResponses.size());
            assertEquals("end_turn", capturedResponses.get(0).getStopReason(),
                    "observer should receive the actual stopReason from provider");
        }
    }

    private Tool simpleTool(String name, String desc) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return desc; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.success("ok");
            }
        };
    }
}
