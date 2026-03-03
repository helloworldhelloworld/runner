package com.lightweightai.kernel.instruction;

import com.lightweightai.kernel.instruction.claude.ClaudeProviderAdapter;
import com.lightweightai.kernel.instruction.claude.ClaudeSkillAdapter;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.skill.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for provider-agnostic InstructionRegistry
 */
class InstructionRegistryTest {

    private InstructionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InstructionRegistry(ClaudeProviderAdapter.getInstance());
    }

    @Test
    void shouldRegisterAndRetrievePackages() {
        InstructionPackage pkg = createTestPackage("test-pkg");

        registry.registerPackage(pkg);

        assertTrue(registry.hasPackage("test-pkg"));
        assertEquals(1, registry.getPackageCount());
        assertEquals(pkg, registry.getPackage("test-pkg"));
    }

    @Test
    void shouldPreventDuplicateRegistration() {
        InstructionPackage pkg1 = createTestPackage("duplicate");
        InstructionPackage pkg2 = createTestPackage("duplicate");

        registry.registerPackage(pkg1);

        assertThrows(IllegalArgumentException.class, () ->
            registry.registerPackage(pkg2)
        );
    }

    @Test
    void shouldActivateAndDeactivatePackages() {
        InstructionPackage pkg = createTestPackage("active-test");
        registry.registerPackage(pkg);

        assertFalse(registry.isActive("active-test"));

        registry.activatePackage("active-test");

        assertTrue(registry.isActive("active-test"));
        assertEquals(1, registry.getActivePackageCount());

        registry.deactivatePackage("active-test");

        assertFalse(registry.isActive("active-test"));
    }

    @Test
    void shouldInjectInstructionsIntoConversation() {
        InstructionPackage pkg = createTestPackage("inject-test");
        registry.registerPackage(pkg);
        registry.activatePackage("inject-test");

        List<ConversationMessage> messages = Collections.singletonList(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Hello")
                .build()
        );

        List<ConversationMessage> enriched = registry.injectInstructions(messages);

        // Should have system message + original message
        assertEquals(2, enriched.size());
        assertEquals(ConversationMessage.MessageRole.SYSTEM, enriched.get(0).getRole());
        assertTrue(enriched.get(0).getTextContent().contains("inject-test"));
    }

    @Test
    void shouldNotInjectWhenNoActivePackages() {
        List<ConversationMessage> messages = Collections.singletonList(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Hello")
                .build()
        );

        List<ConversationMessage> result = registry.injectInstructions(messages);

        assertEquals(messages, result);
    }

    @Test
    void shouldSuggestRelevantPackages() {
        InstructionPackage docPkg = createTestPackage(
            "document-creator",
            "Creates professional documents and reports"
        );
        InstructionPackage weatherPkg = createTestPackage(
            "weather-checker",
            "Checks weather conditions"
        );

        registry.registerPackage(docPkg);
        registry.registerPackage(weatherPkg);

        List<String> suggestions = registry.suggestPackages("Create a document for me");

        assertTrue(suggestions.contains("document-creator"));
        assertFalse(suggestions.contains("weather-checker"));
    }

    @Test
    void shouldWorkWithClaudeSkillAdapter() {
        // Test backward compatibility with existing Skill class
        Skill skill = Skill.builder()
            .name("legacy-skill")
            .description("Legacy skill test")
            .instructions("Follow these instructions")
            .build();

        InstructionPackage pkg = new ClaudeSkillAdapter(skill);

        registry.registerPackage(pkg);
        registry.activatePackage("legacy-skill");

        assertEquals("legacy-skill", pkg.getName());
        assertEquals("anthropic-skill", pkg.getFormat());
        assertTrue(registry.isActive("legacy-skill"));
    }

    @Test
    void shouldGetProviderCapabilities() {
        ProviderAdapter.ProviderCapabilities caps = registry.getCapabilities();

        assertNotNull(caps);
        assertTrue(caps.supportsSystemMessages());
        assertTrue(caps.supportsInstructions());
        assertEquals(200000, caps.getMaxInstructionLength());
    }

    @Test
    void shouldSwitchProviderAdapters() {
        // Create a mock minimal adapter
        ProviderAdapter minimalAdapter = new ProviderAdapter() {
            @Override
            public String getProviderName() {
                return "minimal";
            }

            @Override
            public boolean supports(String providerName) {
                return "minimal".equals(providerName);
            }

            @Override
            public ConversationMessage formatAsSystemMessage(InstructionPackage pkg) {
                return ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent(pkg.getInstructions())
                    .build();
            }

            @Override
            public ConversationMessage formatAsUserPrefix(InstructionPackage pkg, String userMessage) {
                return ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent(pkg.getInstructions() + "\n\n" + userMessage)
                    .build();
            }

            @Override
            public List<ConversationMessage> injectInstructions(
                List<ConversationMessage> messages,
                List<InstructionPackage> packages
            ) {
                // Minimal: prepend to first user message
                if (packages.isEmpty() || messages.isEmpty()) {
                    return messages;
                }

                List<ConversationMessage> result = new java.util.ArrayList<>(messages);
                ConversationMessage first = result.get(0);

                StringBuilder instructions = new StringBuilder();
                for (InstructionPackage pkg : packages) {
                    instructions.append(pkg.getInstructions()).append("\n\n");
                }

                result.set(0, ConversationMessage.builder()
                    .role(first.getRole())
                    .textContent(instructions + first.getTextContent())
                    .build());

                return result;
            }

            @Override
            public ProviderCapabilities getCapabilities() {
                return ProviderCapabilities.minimal();
            }
        };

        registry.setProviderAdapter(minimalAdapter);

        assertEquals("minimal", registry.getProviderAdapter().getProviderName());
        assertFalse(registry.getCapabilities().supportsSystemMessages());
    }

    // Helper methods

    private InstructionPackage createTestPackage(String name) {
        return createTestPackage(name, "Test description for " + name);
    }

    private InstructionPackage createTestPackage(String name, String description) {
        return new InstructionPackage() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String getInstructions() {
                return "Test instructions for " + name;
            }

            @Override
            public java.util.Map<String, String> getMetadata() {
                return java.util.Collections.singletonMap("version", "1.0.0");
            }

            @Override
            public java.util.Map<String, byte[]> getResources() {
                return java.util.Collections.emptyMap();
            }

            @Override
            public java.nio.file.Path getSourcePath() {
                return null;
            }

            @Override
            public boolean hasResource(String resourceName) {
                return false;
            }

            @Override
            public byte[] getResource(String resourceName) {
                return null;
            }

            @Override
            public String getResourceAsString(String resourceName) {
                return null;
            }

            @Override
            public String toPromptContext() {
                return "# " + name + "\n\n" + description + "\n\n" + getInstructions();
            }

            @Override
            public String getFormat() {
                return "test-format";
            }
        };
    }
}
