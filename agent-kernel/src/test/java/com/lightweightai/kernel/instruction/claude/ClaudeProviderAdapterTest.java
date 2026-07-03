package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.instruction.InstructionPackage;
import com.lightweightai.kernel.instruction.ProviderAdapter;
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

@DisplayName("ClaudeProviderAdapter -- formats instructions for Claude/Anthropic models")
class ClaudeProviderAdapterTest {

    private ClaudeProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = ClaudeProviderAdapter.getInstance();
    }

    // ==================== Singleton ====================

    @Nested
    @DisplayName("Singleton pattern")
    class Singleton {

        @Test
        @DisplayName("getInstance returns same instance every time")
        void getInstanceReturnsSameInstance() {
            ClaudeProviderAdapter a1 = ClaudeProviderAdapter.getInstance();
            ClaudeProviderAdapter a2 = ClaudeProviderAdapter.getInstance();

            assertSame(a1, a2, "getInstance() should return the same singleton instance");
        }
    }

    // ==================== getProviderName ====================

    @Nested
    @DisplayName("getProviderName()")
    class GetProviderName {

        @Test
        @DisplayName("returns 'claude'")
        void returnsClaude() {
            assertEquals("claude", adapter.getProviderName());
        }
    }

    // ==================== supports ====================

    @Nested
    @DisplayName("supports()")
    class Supports {

        @Test
        @DisplayName("supports 'claude' (exact case)")
        void supportsClaude() {
            assertTrue(adapter.supports("claude"));
        }

        @Test
        @DisplayName("supports 'Claude' (mixed case)")
        void supportsClaudeMixedCase() {
            assertTrue(adapter.supports("Claude"));
        }

        @Test
        @DisplayName("supports 'CLAUDE' (upper case)")
        void supportsClaudeUpperCase() {
            assertTrue(adapter.supports("CLAUDE"));
        }

        @Test
        @DisplayName("supports 'anthropic' (exact case)")
        void supportsAnthropic() {
            assertTrue(adapter.supports("anthropic"));
        }

        @Test
        @DisplayName("supports 'Anthropic' (mixed case)")
        void supportsAnthropicMixedCase() {
            assertTrue(adapter.supports("Anthropic"));
        }

        @Test
        @DisplayName("supports 'ANTHROPIC' (upper case)")
        void supportsAnthropicUpperCase() {
            assertTrue(adapter.supports("ANTHROPIC"));
        }

        @Test
        @DisplayName("does not support 'openai'")
        void doesNotSupportOpenai() {
            assertFalse(adapter.supports("openai"));
        }

        @Test
        @DisplayName("does not support 'gemini'")
        void doesNotSupportGemini() {
            assertFalse(adapter.supports("gemini"));
        }

        @Test
        @DisplayName("does not support empty string")
        void doesNotSupportEmptyString() {
            assertFalse(adapter.supports(""));
        }

        @Test
        @DisplayName("does not support null")
        void doesNotSupportNull() {
            assertFalse(adapter.supports(null));
        }

        @Test
        @DisplayName("does not support partial match 'claud'")
        void doesNotSupportPartialMatch() {
            assertFalse(adapter.supports("claud"));
        }
    }

    // ==================== formatAsSystemMessage ====================

    @Nested
    @DisplayName("formatAsSystemMessage()")
    class FormatAsSystemMessage {

        @Test
        @DisplayName("creates SYSTEM role message from instruction package")
        void createsSystemRoleMessage() {
            InstructionPackage pkg = createTestPackage("test-skill",
                    "A test skill", "Follow these steps");

            ConversationMessage msg = adapter.formatAsSystemMessage(pkg);

            assertEquals(MessageRole.SYSTEM, msg.getRole());
        }

        @Test
        @DisplayName("message content is the package's toPromptContext()")
        void messageContentIsPromptContext() {
            InstructionPackage pkg = createTestPackage("my-pkg",
                    "My description", "My instructions");

            ConversationMessage msg = adapter.formatAsSystemMessage(pkg);
            String content = msg.getTextContent();

            assertEquals(pkg.toPromptContext(), content,
                    "system message content should match toPromptContext()");
        }
    }

    // ==================== formatAsUserPrefix ====================

    @Nested
    @DisplayName("formatAsUserPrefix()")
    class FormatAsUserPrefix {

        @Test
        @DisplayName("creates USER role message")
        void createsUserRoleMessage() {
            InstructionPackage pkg = createTestPackage("skill-1",
                    "desc", "Do X then Y");

            ConversationMessage msg = adapter.formatAsUserPrefix(pkg, "Hello world");

            assertEquals(MessageRole.USER, msg.getRole());
        }

        @Test
        @DisplayName("combined message contains instructions and user message")
        void combinedMessageContainsBothParts() {
            InstructionPackage pkg = createTestPackage("skill-1",
                    "desc", "Step 1: Do A. Step 2: Do B.");

            ConversationMessage msg = adapter.formatAsUserPrefix(pkg, "Please help me");
            String content = msg.getTextContent();

            assertTrue(content.contains("Step 1: Do A. Step 2: Do B."),
                    "combined message should contain instructions: " + content);
            assertTrue(content.contains("Please help me"),
                    "combined message should contain user message: " + content);
            assertTrue(content.contains("# Instructions"),
                    "combined message should have Instructions section header");
            assertTrue(content.contains("# User Request"),
                    "combined message should have User Request section header");
        }
    }

    // ==================== injectInstructions ====================

    @Nested
    @DisplayName("injectInstructions()")
    class InjectInstructions {

        @Test
        @DisplayName("returns original messages when instructionPackages is null")
        void returnsOriginalWhenNull() {
            List<ConversationMessage> messages = List.of(userMessage("Hello"));

            List<ConversationMessage> result = adapter.injectInstructions(messages, null);

            assertSame(messages, result, "null packages should return original messages unchanged");
        }

        @Test
        @DisplayName("returns original messages when instructionPackages is empty")
        void returnsOriginalWhenEmpty() {
            List<ConversationMessage> messages = List.of(userMessage("Hello"));

            List<ConversationMessage> result = adapter.injectInstructions(messages, List.of());

            assertSame(messages, result, "empty packages should return original messages unchanged");
        }

        @Test
        @DisplayName("prepends system message with single package")
        void prependsSystemMessageWithSinglePackage() {
            List<ConversationMessage> messages = List.of(userMessage("Hello"));
            InstructionPackage pkg = createTestPackage("pkg-1", "desc-1", "instructions-1");

            List<ConversationMessage> result = adapter.injectInstructions(
                    messages, List.of(pkg));

            assertEquals(2, result.size(), "should have system message + original");
            assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
            assertEquals(MessageRole.USER, result.get(1).getRole());
        }

        @Test
        @DisplayName("system message contains package context")
        void systemMessageContainsPackageContext() {
            InstructionPackage pkg = createTestPackage("my-skill",
                    "My skill description", "Do this and that");
            List<ConversationMessage> messages = List.of(userMessage("Hello"));

            List<ConversationMessage> result = adapter.injectInstructions(
                    messages, List.of(pkg));

            String systemContent = result.get(0).getTextContent();
            assertTrue(systemContent.contains("Active Instruction Packages"),
                    "system message should have header: " + systemContent);
            assertTrue(systemContent.contains(pkg.toPromptContext()),
                    "system message should contain package context");
        }

        @Test
        @DisplayName("combines multiple packages into single system message")
        void combinesMultiplePackages() {
            InstructionPackage pkg1 = createTestPackage("alpha", "desc-alpha", "instructions-alpha");
            InstructionPackage pkg2 = createTestPackage("beta", "desc-beta", "instructions-beta");
            List<ConversationMessage> messages = List.of(userMessage("Hello"));

            List<ConversationMessage> result = adapter.injectInstructions(
                    messages, List.of(pkg1, pkg2));

            assertEquals(2, result.size(), "should have 1 system message + 1 original");
            String systemContent = result.get(0).getTextContent();
            assertTrue(systemContent.contains(pkg1.toPromptContext()),
                    "system message should contain first package context");
            assertTrue(systemContent.contains(pkg2.toPromptContext()),
                    "system message should contain second package context");
        }

        @Test
        @DisplayName("preserves all original messages in order after system message")
        void preservesOriginalMessagesInOrder() {
            ConversationMessage msg1 = userMessage("First");
            ConversationMessage msg2 = assistantMessage("Reply");
            ConversationMessage msg3 = userMessage("Second");
            List<ConversationMessage> messages = List.of(msg1, msg2, msg3);

            InstructionPackage pkg = createTestPackage("pkg", "desc", "instr");

            List<ConversationMessage> result = adapter.injectInstructions(
                    messages, List.of(pkg));

            assertEquals(4, result.size());
            assertSame(msg1, result.get(1), "first original should be at index 1");
            assertSame(msg2, result.get(2), "second original should be at index 2");
            assertSame(msg3, result.get(3), "third original should be at index 3");
        }

        @Test
        @DisplayName("does not modify original messages list")
        void doesNotModifyOriginalList() {
            List<ConversationMessage> original = new ArrayList<>();
            original.add(userMessage("Hello"));

            InstructionPackage pkg = createTestPackage("pkg", "desc", "instr");

            adapter.injectInstructions(original, List.of(pkg));

            assertEquals(1, original.size(),
                    "original messages list should not be modified");
        }
    }

    // ==================== getCapabilities ====================

    @Nested
    @DisplayName("getCapabilities()")
    class GetCapabilities {

        @Test
        @DisplayName("returns Claude capabilities")
        void returnsClaudeCapabilities() {
            ProviderAdapter.ProviderCapabilities caps = adapter.getCapabilities();

            assertNotNull(caps);
            assertTrue(caps.supportsSystemMessages(), "Claude supports system messages");
            assertTrue(caps.supportsInstructions(), "Claude supports instructions");
            assertTrue(caps.supportsResources(), "Claude supports resources");
            assertEquals(200000, caps.getMaxInstructionLength(),
                    "Claude supports 200k context");
            assertTrue(caps.supportsMultipleInstructions(),
                    "Claude supports multiple instruction packages");
        }
    }

    // ==================== Helpers ====================

    private ConversationMessage userMessage(String text) {
        return ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent(text)
                .build();
    }

    private ConversationMessage assistantMessage(String text) {
        return ConversationMessage.builder()
                .role(MessageRole.ASSISTANT)
                .textContent(text)
                .build();
    }

    private InstructionPackage createTestPackage(String name, String description, String instructions) {
        return new InstructionPackage() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public String getInstructions() { return instructions; }
            @Override public Map<String, String> getMetadata() { return Map.of(); }
            @Override public Map<String, byte[]> getResources() { return Map.of(); }
            @Override public Path getSourcePath() { return null; }
            @Override public boolean hasResource(String resourceName) { return false; }
            @Override public byte[] getResource(String resourceName) { return null; }
            @Override public String getResourceAsString(String resourceName) { return null; }
            @Override public String toPromptContext() {
                return "# " + name + "\n\n" + description + "\n\n" + instructions;
            }
            @Override public String getFormat() { return "test-format"; }
        };
    }
}
