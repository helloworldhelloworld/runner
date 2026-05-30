package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.instruction.InstructionPackage;
import com.lightweightai.kernel.instruction.ProviderAdapter;
import com.lightweightai.kernel.llm.ConversationMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeProviderAdapter — Claude 指令适配器")
class ClaudeProviderAdapterTest {

    private ClaudeProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = ClaudeProviderAdapter.getInstance();
    }

    @Nested
    @DisplayName("Provider 匹配")
    class SupportsTests {

        @Test
        @DisplayName("支持 claude (大小写不敏感)")
        void supportsClaude() {
            assertTrue(adapter.supports("claude"));
            assertTrue(adapter.supports("Claude"));
            assertTrue(adapter.supports("CLAUDE"));
        }

        @Test
        @DisplayName("支持 anthropic (大小写不敏感)")
        void supportsAnthropic() {
            assertTrue(adapter.supports("anthropic"));
            assertTrue(adapter.supports("Anthropic"));
        }

        @Test
        @DisplayName("不支持其他 provider")
        void doesNotSupportOther() {
            assertFalse(adapter.supports("openai"));
            assertFalse(adapter.supports("gemini"));
        }

        @Test
        @DisplayName("null provider 不支持")
        void nullNotSupported() {
            assertFalse(adapter.supports(null));
        }
    }

    @Test
    @DisplayName("getProviderName 返回 claude")
    void providerNameIsClaude() {
        assertEquals("claude", adapter.getProviderName());
    }

    @Test
    @DisplayName("formatAsSystemMessage 生成 SYSTEM 角色消息")
    void formatsAsSystemMessage() {
        InstructionPackage pkg = new StubPackage("test", "Test instructions");
        ConversationMessage msg = adapter.formatAsSystemMessage(pkg);
        assertEquals(ConversationMessage.MessageRole.SYSTEM, msg.getRole());
    }

    @Test
    @DisplayName("formatAsUserPrefix 生成 USER 角色消息并包含指令")
    void formatsAsUserPrefix() {
        InstructionPackage pkg = new StubPackage("test", "guideline text");
        ConversationMessage msg = adapter.formatAsUserPrefix(pkg, "Hello");
        assertEquals(ConversationMessage.MessageRole.USER, msg.getRole());
    }

    @Nested
    @DisplayName("injectInstructions")
    class InjectTests {

        @Test
        @DisplayName("注入指令包到对话开头作为 SYSTEM 消息")
        void injectsAtBeginning() {
            List<ConversationMessage> messages = List.of(
                    ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.USER)
                            .textContent("hello").build());
            List<InstructionPackage> packages = List.of(
                    new StubPackage("skill1", "instructions1"));

            List<ConversationMessage> enriched = adapter.injectInstructions(messages, packages);

            assertEquals(2, enriched.size());
            assertEquals(ConversationMessage.MessageRole.SYSTEM, enriched.get(0).getRole());
            assertEquals(ConversationMessage.MessageRole.USER, enriched.get(1).getRole());
        }

        @Test
        @DisplayName("空指令包列表返回原始消息不修改")
        void emptyPackagesReturnOriginal() {
            List<ConversationMessage> messages = List.of(
                    ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.USER)
                            .textContent("hello").build());
            assertSame(messages, adapter.injectInstructions(messages, List.of()));
        }

        @Test
        @DisplayName("null 指令包列表返回原始消息不修改")
        void nullPackagesReturnOriginal() {
            List<ConversationMessage> messages = List.of(
                    ConversationMessage.builder()
                            .role(ConversationMessage.MessageRole.USER)
                            .textContent("hello").build());
            assertSame(messages, adapter.injectInstructions(messages, null));
        }
    }

    @Nested
    @DisplayName("Capabilities")
    class CapabilitiesTests {

        @Test
        @DisplayName("Claude 支持 system messages 和多指令包")
        void supportsSystemAndMultiple() {
            ProviderAdapter.ProviderCapabilities caps = adapter.getCapabilities();
            assertTrue(caps.supportsSystemMessages());
            assertTrue(caps.supportsMultipleInstructions());
        }

        @Test
        @DisplayName("maxInstructionLength ≥ 200000")
        void largeContextWindow() {
            assertTrue(adapter.getCapabilities().getMaxInstructionLength() >= 200000);
        }
    }

    private static class StubPackage implements InstructionPackage {
        private final String name;
        private final String instructions;
        StubPackage(String name, String instructions) {
            this.name = name; this.instructions = instructions;
        }
        @Override public String getName() { return name; }
        @Override public String getDescription() { return name; }
        @Override public String getInstructions() { return instructions; }
        @Override public Map<String, String> getMetadata() { return Map.of(); }
        @Override public Map<String, byte[]> getResources() { return Map.of(); }
        @Override public Path getSourcePath() { return null; }
        @Override public boolean hasResource(String r) { return false; }
        @Override public byte[] getResource(String r) { return null; }
        @Override public String getResourceAsString(String r) { return null; }
        @Override public String toPromptContext() { return instructions; }
        @Override public String getFormat() { return "test"; }
    }
}
