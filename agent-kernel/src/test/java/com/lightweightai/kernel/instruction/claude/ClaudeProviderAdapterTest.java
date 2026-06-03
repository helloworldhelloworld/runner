package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.instruction.InstructionPackage;
import com.lightweightai.kernel.instruction.ProviderAdapter.ProviderCapabilities;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClaudeProviderAdapter
 */
class ClaudeProviderAdapterTest {

    private ClaudeProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ClaudeProviderAdapter();
    }

    @Test
    void providerNameIsClaude() {
        assertEquals("claude", adapter.getProviderName());
    }

    @Test
    void supportsClaude() {
        assertTrue(adapter.supports("claude"));
    }

    @Test
    void supportsAnthropic() {
        assertTrue(adapter.supports("anthropic"));
    }

    @Test
    void supportsCaseInsensitive() {
        assertTrue(adapter.supports("CLAUDE"));
        assertTrue(adapter.supports("Claude"));
        assertTrue(adapter.supports("Anthropic"));
    }

    @Test
    void doesNotSupportNull() {
        assertFalse(adapter.supports(null));
    }

    @Test
    void doesNotSupportOther() {
        assertFalse(adapter.supports("openai"));
    }

    @Test
    void formatAsSystemMessageUsesSystemRole() {
        InstructionPackage pkg = testPackage("test-pkg", "some instructions");

        ConversationMessage msg = adapter.formatAsSystemMessage(pkg);

        assertEquals(MessageRole.SYSTEM, msg.getRole());
    }

    @Test
    void formatAsSystemMessageUsesPromptContext() {
        InstructionPackage pkg = testPackage("test-pkg", "some instructions");

        ConversationMessage msg = adapter.formatAsSystemMessage(pkg);

        assertEquals(pkg.toPromptContext(), msg.getTextContent());
    }

    @Test
    void formatAsUserPrefixUsesUserRole() {
        InstructionPackage pkg = testPackage("test-pkg", "some instructions");

        ConversationMessage msg = adapter.formatAsUserPrefix(pkg, "Hello");

        assertEquals(MessageRole.USER, msg.getRole());
    }

    @Test
    void formatAsUserPrefixCombinesInstructionsAndMessage() {
        InstructionPackage pkg = testPackage("test-pkg", "Follow these rules");

        ConversationMessage msg = adapter.formatAsUserPrefix(pkg, "Do something");

        String text = msg.getTextContent();
        assertTrue(text.contains("Follow these rules"),
                "Should contain instructions");
        assertTrue(text.contains("Do something"),
                "Should contain user message");
        assertTrue(text.contains("# Instructions"),
                "Should contain Instructions header");
        assertTrue(text.contains("# User Request"),
                "Should contain User Request header");
    }

    @Test
    void injectInstructionsWithEmptyPackagesReturnsOriginal() {
        List<ConversationMessage> original = List.of(
                ConversationMessage.builder()
                        .role(MessageRole.USER)
                        .textContent("Hello")
                        .build()
        );

        // null packages
        List<ConversationMessage> result1 = adapter.injectInstructions(original, null);
        assertSame(original, result1);

        // empty packages
        List<ConversationMessage> result2 = adapter.injectInstructions(original, List.of());
        assertSame(original, result2);
    }

    @Test
    void injectInstructionsPrependsSystemMessage() {
        ConversationMessage userMsg = ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent("Hello")
                .build();
        List<ConversationMessage> original = new ArrayList<>();
        original.add(userMsg);

        InstructionPackage pkg = testPackage("pkg1", "instructions1");

        List<ConversationMessage> result = adapter.injectInstructions(original, List.of(pkg));

        assertEquals(2, result.size());
        assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
        assertSame(userMsg, result.get(1));
    }

    @Test
    void injectInstructionsContainsAllPackageContexts() {
        List<ConversationMessage> original = List.of(
                ConversationMessage.builder()
                        .role(MessageRole.USER)
                        .textContent("Hello")
                        .build()
        );

        InstructionPackage pkg1 = testPackage("alpha", "first instructions");
        InstructionPackage pkg2 = testPackage("beta", "second instructions");

        List<ConversationMessage> result = adapter.injectInstructions(
                original, List.of(pkg1, pkg2));

        String systemText = result.get(0).getTextContent();
        assertTrue(systemText.startsWith("# Active Instruction Packages\n\n"),
                "Should start with header");
        assertTrue(systemText.contains(pkg1.toPromptContext()),
                "Should contain first package context");
        assertTrue(systemText.contains(pkg2.toPromptContext()),
                "Should contain second package context");
    }

    @Test
    void capabilitiesSupportsSystemMessages() {
        ProviderCapabilities capabilities = adapter.getCapabilities();

        assertTrue(capabilities.supportsSystemMessages());
        assertEquals(200000, capabilities.getMaxInstructionLength());
    }

    @Test
    void singletonInstance() {
        ClaudeProviderAdapter instance1 = ClaudeProviderAdapter.getInstance();
        ClaudeProviderAdapter instance2 = ClaudeProviderAdapter.getInstance();

        assertSame(instance1, instance2);
    }

    // --- Test helper ---

    private static InstructionPackage testPackage(String name, String instructions) {
        return new InstructionPackage() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "Test: " + name; }
            @Override public String getInstructions() { return instructions; }
            @Override public Map<String, String> getMetadata() { return Map.of(); }
            @Override public Map<String, byte[]> getResources() { return Map.of(); }
            @Override public Path getSourcePath() { return null; }
            @Override public boolean hasResource(String r) { return false; }
            @Override public byte[] getResource(String r) { return null; }
            @Override public String getResourceAsString(String r) { return null; }
            @Override public String toPromptContext() { return "Context for " + name + ": " + instructions; }
            @Override public String getFormat() { return "test"; }
        };
    }
}
