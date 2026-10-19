package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.instruction.InstructionPackage;
import com.lightweightai.kernel.instruction.ProviderAdapter;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeProviderAdapter — instruction injection critical path")
class ClaudeProviderAdapterTest {

    private final ClaudeProviderAdapter adapter = ClaudeProviderAdapter.getInstance();

    @Test
    @DisplayName("getProviderName returns 'claude'")
    void providerName() {
        assertEquals("claude", adapter.getProviderName());
    }

    @Test
    @DisplayName("supports 'claude' and 'anthropic' case-insensitively")
    void supportsClaude() {
        assertTrue(adapter.supports("claude"));
        assertTrue(adapter.supports("Claude"));
        assertTrue(adapter.supports("CLAUDE"));
        assertTrue(adapter.supports("anthropic"));
        assertTrue(adapter.supports("Anthropic"));
        assertFalse(adapter.supports("openai"));
        assertFalse(adapter.supports("gemini"));
        assertFalse(adapter.supports(null));
    }

    @Test
    @DisplayName("formatAsSystemMessage creates SYSTEM role message with prompt context")
    void formatAsSystemMessage() {
        InstructionPackage pkg = createPackage("test-pkg", "Test instructions content");

        ConversationMessage msg = adapter.formatAsSystemMessage(pkg);

        assertEquals(MessageRole.SYSTEM, msg.getRole());
        String text = msg.getTextContent();
        assertNotNull(text);
        assertFalse(text.isEmpty(), "system message should contain instruction content");
    }

    @Test
    @DisplayName("formatAsUserPrefix creates USER role message with instructions + user message")
    void formatAsUserPrefix() {
        InstructionPackage pkg = createPackage("test-pkg", "Follow these guidelines carefully");

        ConversationMessage msg = adapter.formatAsUserPrefix(pkg, "What is 2+2?");

        assertEquals(MessageRole.USER, msg.getRole());
        String text = msg.getTextContent();
        assertTrue(text.contains("Follow these guidelines carefully"),
                "should contain instructions");
        assertTrue(text.contains("What is 2+2?"),
                "should contain original user message");
        assertTrue(text.contains("# Instructions"), "should have instructions header");
        assertTrue(text.contains("# User Request"), "should have user request header");
    }

    @Test
    @DisplayName("injectInstructions with null packages returns original messages")
    void injectInstructionsNull() {
        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                        .role(MessageRole.USER)
                        .textContent("hello")
                        .build()
        );

        List<ConversationMessage> result = adapter.injectInstructions(messages, null);

        assertSame(messages, result, "null packages should return original messages");
    }

    @Test
    @DisplayName("injectInstructions with empty packages returns original messages")
    void injectInstructionsEmpty() {
        List<ConversationMessage> messages = List.of(
                ConversationMessage.builder()
                        .role(MessageRole.USER)
                        .textContent("hello")
                        .build()
        );

        List<ConversationMessage> result = adapter.injectInstructions(messages, List.of());

        assertSame(messages, result, "empty packages should return original messages");
    }

    @Test
    @DisplayName("injectInstructions prepends system message with all packages")
    void injectInstructionsPrepends() {
        List<ConversationMessage> messages = new ArrayList<>();
        messages.add(ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent("hello")
                .build());

        InstructionPackage pkg1 = createPackage("pkg1", "Instructions one");
        InstructionPackage pkg2 = createPackage("pkg2", "Instructions two");

        List<ConversationMessage> result = adapter.injectInstructions(
                messages, List.of(pkg1, pkg2));

        assertEquals(2, result.size(), "should have system message + original message");
        assertEquals(MessageRole.SYSTEM, result.get(0).getRole(),
                "first message should be SYSTEM");
        assertEquals(MessageRole.USER, result.get(1).getRole(),
                "second message should be original USER message");

        String systemText = result.get(0).getTextContent();
        assertTrue(systemText.contains("Active Instruction Packages"),
                "system message should contain header");
    }

    @Test
    @DisplayName("getCapabilities returns claude capabilities")
    void capabilities() {
        ProviderAdapter.ProviderCapabilities caps = adapter.getCapabilities();

        assertTrue(caps.supportsSystemMessages());
        assertTrue(caps.supportsInstructions());
        assertTrue(caps.supportsResources());
        assertEquals(200000, caps.getMaxInstructionLength());
        assertTrue(caps.supportsMultipleInstructions());
    }

    @Test
    @DisplayName("singleton instance is consistent")
    void singletonConsistent() {
        assertSame(ClaudeProviderAdapter.getInstance(), ClaudeProviderAdapter.getInstance());
    }

    @Test
    @DisplayName("ProviderCapabilities.openai() returns correct values")
    void openaiCapabilities() {
        ProviderAdapter.ProviderCapabilities caps = ProviderAdapter.ProviderCapabilities.openai();

        assertTrue(caps.supportsSystemMessages());
        assertTrue(caps.supportsInstructions());
        assertFalse(caps.supportsResources());
        assertEquals(128000, caps.getMaxInstructionLength());
        assertTrue(caps.supportsMultipleInstructions());
    }

    @Test
    @DisplayName("ProviderCapabilities.minimal() returns restricted values")
    void minimalCapabilities() {
        ProviderAdapter.ProviderCapabilities caps = ProviderAdapter.ProviderCapabilities.minimal();

        assertFalse(caps.supportsSystemMessages());
        assertTrue(caps.supportsInstructions());
        assertFalse(caps.supportsResources());
        assertEquals(4096, caps.getMaxInstructionLength());
        assertFalse(caps.supportsMultipleInstructions());
    }

    private InstructionPackage createPackage(String name, String instructions) {
        return new InstructionPackage() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Test package: " + name; }
            @Override public String getInstructions() { return instructions; }
            @Override public Map<String, String> getMetadata() { return Map.of(); }
            @Override public Map<String, byte[]> getResources() { return Map.of(); }
            @Override public Path getSourcePath() { return null; }
            @Override public boolean hasResource(String resourceName) { return false; }
            @Override public byte[] getResource(String resourceName) { return null; }
            @Override public String getResourceAsString(String resourceName) { return null; }
            @Override
            public String toPromptContext() {
                return "## " + name + "\n\n" + instructions;
            }
            @Override public String getFormat() { return "test"; }
        };
    }
}
