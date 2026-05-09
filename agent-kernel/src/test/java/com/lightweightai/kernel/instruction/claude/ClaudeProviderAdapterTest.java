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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeProviderAdapter - instruction injection for Claude requests")
class ClaudeProviderAdapterTest {

    private ClaudeProviderAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = ClaudeProviderAdapter.getInstance();
    }

    @Nested
    @DisplayName("Provider identification")
    class ProviderIdentification {

        @Test
        @DisplayName("provider name is 'claude'")
        void providerName() {
            assertEquals("claude", adapter.getProviderName());
        }

        @Test
        @DisplayName("supports 'claude' (case-insensitive)")
        void supportsClaude() {
            assertTrue(adapter.supports("claude"));
            assertTrue(adapter.supports("Claude"));
            assertTrue(adapter.supports("CLAUDE"));
        }

        @Test
        @DisplayName("supports 'anthropic' (case-insensitive)")
        void supportsAnthropic() {
            assertTrue(adapter.supports("anthropic"));
            assertTrue(adapter.supports("Anthropic"));
        }

        @Test
        @DisplayName("does not support other providers")
        void doesNotSupportOthers() {
            assertFalse(adapter.supports("openai"));
            assertFalse(adapter.supports("gemini"));
            assertFalse(adapter.supports(null));
        }
    }

    @Nested
    @DisplayName("formatAsSystemMessage")
    class FormatAsSystem {

        @Test
        @DisplayName("creates SYSTEM role message with instruction content")
        void createsSystemMessage() {
            InstructionPackage pkg = stubPackage("test-skill", "Be helpful", "prompt context text");

            ConversationMessage msg = adapter.formatAsSystemMessage(pkg);

            assertEquals(MessageRole.SYSTEM, msg.getRole());
            assertEquals("prompt context text", msg.getTextContent());
        }
    }

    @Nested
    @DisplayName("formatAsUserPrefix")
    class FormatAsUserPrefix {

        @Test
        @DisplayName("combines instructions and user message as USER role")
        void combinesInstructionsAndMessage() {
            InstructionPackage pkg = stubPackage("skill", "Follow these rules", "context");

            ConversationMessage msg = adapter.formatAsUserPrefix(pkg, "Hello world");

            assertEquals(MessageRole.USER, msg.getRole());
            String text = msg.getTextContent();
            assertTrue(text.contains("Follow these rules"), "Should contain instructions");
            assertTrue(text.contains("Hello world"), "Should contain user message");
        }
    }

    @Nested
    @DisplayName("injectInstructions - transmission chain test")
    class InjectInstructions {

        @Test
        @DisplayName("empty instruction list returns original messages unchanged")
        void emptyInstructionsReturnOriginal() {
            List<ConversationMessage> original = List.of(
                ConversationMessage.builder()
                    .role(MessageRole.USER)
                    .textContent("Hi")
                    .build()
            );

            List<ConversationMessage> result = adapter.injectInstructions(original, Collections.emptyList());
            assertSame(original, result);
        }

        @Test
        @DisplayName("null instruction list returns original messages unchanged")
        void nullInstructionsReturnOriginal() {
            List<ConversationMessage> original = List.of(
                ConversationMessage.builder()
                    .role(MessageRole.USER)
                    .textContent("Hi")
                    .build()
            );

            List<ConversationMessage> result = adapter.injectInstructions(original, null);
            assertSame(original, result);
        }

        @Test
        @DisplayName("prepends system message containing all instruction packages")
        void prependsSystemWithAllPackages() {
            InstructionPackage pkg1 = stubPackage("skill-a", "Rule A", "Context A");
            InstructionPackage pkg2 = stubPackage("skill-b", "Rule B", "Context B");

            ConversationMessage userMsg = ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent("User question")
                .build();

            List<ConversationMessage> result = adapter.injectInstructions(
                List.of(userMsg), List.of(pkg1, pkg2));

            assertEquals(2, result.size());
            assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
            assertEquals(MessageRole.USER, result.get(1).getRole());

            String systemText = result.get(0).getTextContent();
            assertTrue(systemText.contains("Context A"), "System message must contain first package");
            assertTrue(systemText.contains("Context B"), "System message must contain second package");
        }

        @Test
        @DisplayName("original messages preserved after injection")
        void originalMessagesPreserved() {
            InstructionPackage pkg = stubPackage("skill", "rules", "context");

            ConversationMessage msg1 = ConversationMessage.builder()
                .role(MessageRole.USER).textContent("msg1").build();
            ConversationMessage msg2 = ConversationMessage.builder()
                .role(MessageRole.ASSISTANT).textContent("msg2").build();

            List<ConversationMessage> result = adapter.injectInstructions(
                List.of(msg1, msg2), List.of(pkg));

            assertEquals(3, result.size());
            assertEquals("msg1", result.get(1).getTextContent());
            assertEquals("msg2", result.get(2).getTextContent());
        }
    }

    @Nested
    @DisplayName("Capabilities")
    class Capabilities {

        @Test
        @DisplayName("Claude capabilities include system messages, resources, multi-instructions")
        void claudeCapabilities() {
            ProviderCapabilities caps = adapter.getCapabilities();

            assertTrue(caps.supportsSystemMessages());
            assertTrue(caps.supportsInstructions());
            assertTrue(caps.supportsResources());
            assertTrue(caps.supportsMultipleInstructions());
            assertEquals(200000, caps.getMaxInstructionLength());
        }
    }

    @Test
    @DisplayName("singleton returns same instance")
    void singletonIdentity() {
        assertSame(ClaudeProviderAdapter.getInstance(), ClaudeProviderAdapter.getInstance());
    }

    private InstructionPackage stubPackage(String name, String instructions, String promptContext) {
        return new InstructionPackage() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return "test"; }
            @Override public String getInstructions() { return instructions; }
            @Override public Map<String, String> getMetadata() { return Map.of(); }
            @Override public Map<String, byte[]> getResources() { return Map.of(); }
            @Override public Path getSourcePath() { return null; }
            @Override public boolean hasResource(String r) { return false; }
            @Override public byte[] getResource(String r) { return null; }
            @Override public String getResourceAsString(String r) { return null; }
            @Override public String toPromptContext() { return promptContext; }
            @Override public String getFormat() { return "test"; }
        };
    }
}
