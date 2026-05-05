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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeProviderAdapter — instruction formatting for Claude/Anthropic")
class ClaudeProviderAdapterTest {

    private ClaudeProviderAdapter adapter;

    @BeforeEach
    void setup() {
        adapter = ClaudeProviderAdapter.getInstance();
    }

    @Nested
    @DisplayName("supports")
    class Supports {

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
        @DisplayName("returns SYSTEM role message with prompt context content")
        void returnsSystemMessage() {
            InstructionPackage pkg = stubPackage("test-skill", "Do testing", "prompt context here");

            ConversationMessage msg = adapter.formatAsSystemMessage(pkg);

            assertEquals(MessageRole.SYSTEM, msg.getRole());
            assertEquals("prompt context here", msg.getTextContent());
        }
    }

    @Nested
    @DisplayName("formatAsUserPrefix")
    class FormatAsUserPrefix {

        @Test
        @DisplayName("combines instructions and user message into USER role")
        void combinesInstructionsAndUserMessage() {
            InstructionPackage pkg = stubPackage("skill", "Follow these rules", "ctx");

            ConversationMessage msg = adapter.formatAsUserPrefix(pkg, "What is 2+2?");

            assertEquals(MessageRole.USER, msg.getRole());
            assertTrue(msg.getTextContent().contains("Follow these rules"),
                    "Should contain the instructions text");
            assertTrue(msg.getTextContent().contains("What is 2+2?"),
                    "Should contain the user message");
        }
    }

    @Nested
    @DisplayName("injectInstructions")
    class InjectInstructions {

        @Test
        @DisplayName("prepends combined SYSTEM message to conversation")
        void prependsSystemMessage() {
            InstructionPackage pkg = stubPackage("skill1", "desc1", "context1");
            List<ConversationMessage> messages = List.of(
                    ConversationMessage.builder().role(MessageRole.USER).textContent("Hello").build()
            );

            List<ConversationMessage> enriched = adapter.injectInstructions(messages, List.of(pkg));

            assertEquals(2, enriched.size());
            assertEquals(MessageRole.SYSTEM, enriched.get(0).getRole());
            assertTrue(enriched.get(0).getTextContent().contains("context1"));
            assertEquals(MessageRole.USER, enriched.get(1).getRole());
            assertEquals("Hello", enriched.get(1).getTextContent());
        }

        @Test
        @DisplayName("returns original messages when no instruction packages")
        void returnsOriginalWhenEmpty() {
            List<ConversationMessage> messages = List.of(
                    ConversationMessage.builder().role(MessageRole.USER).textContent("Hi").build()
            );

            List<ConversationMessage> result = adapter.injectInstructions(messages, List.of());
            assertSame(messages, result);
        }

        @Test
        @DisplayName("returns original messages when null instruction packages")
        void returnsOriginalWhenNull() {
            List<ConversationMessage> messages = List.of(
                    ConversationMessage.builder().role(MessageRole.USER).textContent("Hi").build()
            );

            List<ConversationMessage> result = adapter.injectInstructions(messages, null);
            assertSame(messages, result);
        }

        @Test
        @DisplayName("combines multiple instruction packages into single system message")
        void combinesMultiplePackages() {
            InstructionPackage pkg1 = stubPackage("skill1", "desc1", "ctx1");
            InstructionPackage pkg2 = stubPackage("skill2", "desc2", "ctx2");

            List<ConversationMessage> messages = List.of(
                    ConversationMessage.builder().role(MessageRole.USER).textContent("Go").build()
            );

            List<ConversationMessage> enriched = adapter.injectInstructions(messages, List.of(pkg1, pkg2));

            assertEquals(2, enriched.size());
            String systemContent = enriched.get(0).getTextContent();
            assertTrue(systemContent.contains("ctx1"));
            assertTrue(systemContent.contains("ctx2"));
        }
    }

    @Nested
    @DisplayName("getCapabilities")
    class Capabilities {

        @Test
        @DisplayName("reports Claude capabilities: system messages, resources, large context")
        void claudeCapabilities() {
            ProviderAdapter.ProviderCapabilities caps = adapter.getCapabilities();
            assertTrue(caps.supportsSystemMessages());
            assertTrue(caps.supportsInstructions());
            assertTrue(caps.supportsResources());
            assertTrue(caps.supportsMultipleInstructions());
            assertEquals(200000, caps.getMaxInstructionLength());
        }
    }

    @Nested
    @DisplayName("singleton")
    class Singleton {

        @Test
        @DisplayName("getInstance returns same instance")
        void sameInstance() {
            assertSame(ClaudeProviderAdapter.getInstance(), ClaudeProviderAdapter.getInstance());
        }

        @Test
        @DisplayName("provider name is 'claude'")
        void providerName() {
            assertEquals("claude", adapter.getProviderName());
        }
    }

    private static InstructionPackage stubPackage(String name, String instructions, String promptContext) {
        return new InstructionPackage() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " description"; }
            @Override public String getInstructions() { return instructions; }
            @Override public Map<String, String> getMetadata() { return Collections.emptyMap(); }
            @Override public Map<String, byte[]> getResources() { return Collections.emptyMap(); }
            @Override public Path getSourcePath() { return null; }
            @Override public boolean hasResource(String r) { return false; }
            @Override public byte[] getResource(String r) { return null; }
            @Override public String getResourceAsString(String r) { return null; }
            @Override public String toPromptContext() { return promptContext; }
            @Override public String getFormat() { return "test"; }
        };
    }
}
