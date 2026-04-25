package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.instruction.InstructionPackage;
import com.lightweightai.kernel.instruction.ProviderAdapter;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeProviderAdapter - formats instructions for Claude/Anthropic")
class ClaudeProviderAdapterTest {

    private ClaudeProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = ClaudeProviderAdapter.getInstance();
    }

    @Test
    @DisplayName("getProviderName returns 'claude'")
    void testProviderName() {
        assertEquals("claude", adapter.getProviderName());
    }

    @Test
    @DisplayName("supports 'claude' (case-insensitive)")
    void testSupportsClaude() {
        assertTrue(adapter.supports("claude"));
        assertTrue(adapter.supports("Claude"));
        assertTrue(adapter.supports("CLAUDE"));
    }

    @Test
    @DisplayName("supports 'anthropic' (case-insensitive)")
    void testSupportsAnthropic() {
        assertTrue(adapter.supports("anthropic"));
        assertTrue(adapter.supports("Anthropic"));
    }

    @Test
    @DisplayName("does not support other providers")
    void testDoesNotSupportOthers() {
        assertFalse(adapter.supports("openai"));
        assertFalse(adapter.supports("gemini"));
        assertFalse(adapter.supports(null));
    }

    @Test
    @DisplayName("formatAsSystemMessage creates SYSTEM role message with prompt context")
    void testFormatAsSystemMessage() {
        InstructionPackage pkg = new TestInstructionPackage("test-skill",
                "Test description", "Follow these instructions carefully.");

        ConversationMessage msg = adapter.formatAsSystemMessage(pkg);

        assertEquals(MessageRole.SYSTEM, msg.getRole());
        String content = msg.getTextContent();
        assertTrue(content.contains("test-skill"));
        assertTrue(content.contains("Follow these instructions"));
    }

    @Test
    @DisplayName("formatAsUserPrefix combines instructions and user message")
    void testFormatAsUserPrefix() {
        InstructionPackage pkg = new TestInstructionPackage("skill",
                "desc", "Be polite and helpful.");

        ConversationMessage msg = adapter.formatAsUserPrefix(pkg, "What is 2+2?");

        assertEquals(MessageRole.USER, msg.getRole());
        String content = msg.getTextContent();
        assertTrue(content.contains("Be polite and helpful."));
        assertTrue(content.contains("What is 2+2?"));
        assertTrue(content.contains("Instructions"));
        assertTrue(content.contains("User Request"));
    }

    @Test
    @DisplayName("injectInstructions prepends system message to conversation")
    void testInjectInstructions() {
        InstructionPackage pkg = new TestInstructionPackage("greeting",
                "Greeting skill", "Always greet warmly.");

        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder().role(MessageRole.USER).textContent("hi").build()
        );

        List<ConversationMessage> enriched = adapter.injectInstructions(messages, List.of(pkg));

        assertEquals(2, enriched.size());
        assertEquals(MessageRole.SYSTEM, enriched.get(0).getRole());
        assertEquals(MessageRole.USER, enriched.get(1).getRole());
        assertTrue(enriched.get(0).getTextContent().contains("greeting"));
    }

    @Test
    @DisplayName("injectInstructions with empty list returns original messages")
    void testInjectEmptyList() {
        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder().role(MessageRole.USER).textContent("hi").build()
        );

        List<ConversationMessage> result = adapter.injectInstructions(messages, List.of());
        assertEquals(messages, result);
    }

    @Test
    @DisplayName("injectInstructions with null list returns original messages")
    void testInjectNullList() {
        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder().role(MessageRole.USER).textContent("hi").build()
        );

        List<ConversationMessage> result = adapter.injectInstructions(messages, null);
        assertEquals(messages, result);
    }

    @Test
    @DisplayName("injectInstructions with multiple packages combines them")
    void testInjectMultiplePackages() {
        List<InstructionPackage> packages = List.of(
                new TestInstructionPackage("skill-a", "A desc", "Instruction A"),
                new TestInstructionPackage("skill-b", "B desc", "Instruction B")
        );

        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder().role(MessageRole.USER).textContent("hello").build()
        );

        List<ConversationMessage> enriched = adapter.injectInstructions(messages, packages);

        assertEquals(2, enriched.size());
        String systemContent = enriched.get(0).getTextContent();
        assertTrue(systemContent.contains("skill-a"));
        assertTrue(systemContent.contains("skill-b"));
    }

    @Test
    @DisplayName("getCapabilities returns Claude capabilities")
    void testCapabilities() {
        ProviderAdapter.ProviderCapabilities caps = adapter.getCapabilities();

        assertTrue(caps.supportsSystemMessages());
        assertTrue(caps.supportsInstructions());
        assertTrue(caps.supportsResources());
        assertTrue(caps.supportsMultipleInstructions());
        assertTrue(caps.getMaxInstructionLength() >= 200000);
    }

    @Test
    @DisplayName("getInstance returns singleton")
    void testSingleton() {
        assertSame(ClaudeProviderAdapter.getInstance(), ClaudeProviderAdapter.getInstance());
    }

    private static class TestInstructionPackage implements InstructionPackage {
        private final String name;
        private final String description;
        private final String instructions;

        TestInstructionPackage(String name, String description, String instructions) {
            this.name = name;
            this.description = description;
            this.instructions = instructions;
        }

        @Override public String getName() { return name; }
        @Override public String getDescription() { return description; }
        @Override public String getInstructions() { return instructions; }
        @Override public Map<String, String> getMetadata() { return Map.of(); }
        @Override public Map<String, byte[]> getResources() { return Map.of(); }
        @Override public Path getSourcePath() { return null; }
        @Override public boolean hasResource(String r) { return false; }
        @Override public byte[] getResource(String r) { return null; }
        @Override public String getResourceAsString(String r) { return null; }
        @Override
        public String toPromptContext() {
            return "# Skill: " + name + "\n**Description**: " + description + "\n\n" + instructions;
        }
        @Override public String getFormat() { return "test"; }
    }
}
