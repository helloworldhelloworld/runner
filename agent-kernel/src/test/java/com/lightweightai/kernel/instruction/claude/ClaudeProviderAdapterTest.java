package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.instruction.InstructionPackage;
import com.lightweightai.kernel.instruction.ProviderAdapter.ProviderCapabilities;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeProviderAdapter - Claude 指令适配器")
class ClaudeProviderAdapterTest {

    private ClaudeProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = ClaudeProviderAdapter.getInstance();
    }

    @Test
    @DisplayName("单例模式返回同一实例")
    void shouldReturnSameInstance() {
        assertSame(ClaudeProviderAdapter.getInstance(), ClaudeProviderAdapter.getInstance());
    }

    @Test
    @DisplayName("提供者名称为 claude")
    void shouldReturnClaudeAsProviderName() {
        assertEquals("claude", adapter.getProviderName());
    }

    @Nested
    @DisplayName("supports 方法")
    class Supports {

        @Test
        @DisplayName("支持 claude")
        void shouldSupportClaude() {
            assertTrue(adapter.supports("claude"));
            assertTrue(adapter.supports("Claude"));
            assertTrue(adapter.supports("CLAUDE"));
        }

        @Test
        @DisplayName("支持 anthropic")
        void shouldSupportAnthropic() {
            assertTrue(adapter.supports("anthropic"));
            assertTrue(adapter.supports("Anthropic"));
        }

        @Test
        @DisplayName("不支持其他提供者")
        void shouldNotSupportOthers() {
            assertFalse(adapter.supports("openai"));
            assertFalse(adapter.supports("gemini"));
        }

        @Test
        @DisplayName("null 返回 false")
        void shouldReturnFalseForNull() {
            assertFalse(adapter.supports(null));
        }
    }

    @Nested
    @DisplayName("formatAsSystemMessage")
    class FormatAsSystemMessage {

        @Test
        @DisplayName("创建 SYSTEM 角色消息")
        void shouldCreateSystemMessage() {
            InstructionPackage pkg = createMockPackage("test-skill", "Test instructions");
            ConversationMessage msg = adapter.formatAsSystemMessage(pkg);

            assertEquals(MessageRole.SYSTEM, msg.getRole());
        }
    }

    @Nested
    @DisplayName("formatAsUserPrefix")
    class FormatAsUserPrefix {

        @Test
        @DisplayName("合并指令和用户消息")
        void shouldCombineInstructionsAndUserMessage() {
            InstructionPackage pkg = createMockPackage("skill-1", "Be helpful");
            ConversationMessage msg = adapter.formatAsUserPrefix(pkg, "Hello");

            assertEquals(MessageRole.USER, msg.getRole());
            String content = msg.getTextContent();
            assertTrue(content.contains("Be helpful"));
            assertTrue(content.contains("Hello"));
        }
    }

    @Nested
    @DisplayName("injectInstructions")
    class InjectInstructions {

        @Test
        @DisplayName("空包列表返回原始消息")
        void shouldReturnOriginalMessagesForEmptyPackages() {
            List<ConversationMessage> messages = List.of(
                    ConversationMessage.builder().role(MessageRole.USER).textContent("Hi").build());

            List<ConversationMessage> result = adapter.injectInstructions(messages, List.of());
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("null 包列表返回原始消息")
        void shouldReturnOriginalMessagesForNullPackages() {
            List<ConversationMessage> messages = List.of(
                    ConversationMessage.builder().role(MessageRole.USER).textContent("Hi").build());

            List<ConversationMessage> result = adapter.injectInstructions(messages, null);
            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("注入后系统消息在最前面")
        void shouldPrependSystemMessage() {
            List<ConversationMessage> messages = new ArrayList<>();
            messages.add(ConversationMessage.builder().role(MessageRole.USER).textContent("Hi").build());

            InstructionPackage pkg = createMockPackage("skill-1", "instructions");

            List<ConversationMessage> result = adapter.injectInstructions(messages, List.of(pkg));

            assertEquals(2, result.size());
            assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
            assertEquals(MessageRole.USER, result.get(1).getRole());
        }

        @Test
        @DisplayName("多个包合并为一个系统消息")
        void shouldCombineMultiplePackages() {
            List<ConversationMessage> messages = new ArrayList<>();
            messages.add(ConversationMessage.builder().role(MessageRole.USER).textContent("Hi").build());

            InstructionPackage pkg1 = createMockPackage("skill-1", "inst-1");
            InstructionPackage pkg2 = createMockPackage("skill-2", "inst-2");

            List<ConversationMessage> result = adapter.injectInstructions(
                    messages, List.of(pkg1, pkg2));

            // 1 system (combined) + 1 user
            assertEquals(2, result.size());
            assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
        }
    }

    @Test
    @DisplayName("getCapabilities 返回 Claude 能力")
    void shouldReturnClaudeCapabilities() {
        ProviderCapabilities caps = adapter.getCapabilities();
        assertTrue(caps.supportsSystemMessages());
        assertTrue(caps.supportsInstructions());
        assertTrue(caps.supportsResources());
        assertTrue(caps.supportsMultipleInstructions());
        assertEquals(200000, caps.getMaxInstructionLength());
    }

    private InstructionPackage createMockPackage(String name, String instructions) {
        return new InstructionPackage() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "desc"; }
            @Override public String getInstructions() { return instructions; }
            @Override public Map<String, String> getMetadata() { return Map.of(); }
            @Override public Map<String, byte[]> getResources() { return Map.of(); }
            @Override public Path getSourcePath() { return null; }
            @Override public boolean hasResource(String r) { return false; }
            @Override public byte[] getResource(String r) { return null; }
            @Override public String getResourceAsString(String r) { return null; }
            @Override public String toPromptContext() { return "# " + name + "\n" + instructions; }
            @Override public String getFormat() { return "test"; }
        };
    }
}
