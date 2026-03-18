package com.lightweightai.kernel.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lightweightai.kernel.llm.ConversationMessage;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for Skill Registry
 */
class SkillRegistryTest {

    private SkillRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SkillRegistry();
    }

    @Test
    void shouldRegisterSkill() {
        Skill skill = Skill.builder()
            .name("test-skill")
            .description("Test description")
            .instructions("Test instructions")
            .build();

        registry.registerSkill(skill);

        assertTrue(registry.hasSkill("test-skill"));
        assertEquals(1, registry.getSkillCount());
        assertEquals(skill, registry.getSkill("test-skill"));
    }

    @Test
    void shouldPreventDuplicateRegistration() {
        Skill skill1 = Skill.builder()
            .name("duplicate")
            .description("First")
            .instructions("Instructions")
            .build();

        Skill skill2 = Skill.builder()
            .name("duplicate")
            .description("Second")
            .instructions("Instructions")
            .build();

        registry.registerSkill(skill1);
        assertThrows(IllegalArgumentException.class, () -> registry.registerSkill(skill2));
    }

    @Test
    void shouldActivateAndDeactivateSkills() {
        Skill skill = Skill.builder()
            .name("active-skill")
            .description("Test")
            .instructions("Instructions")
            .build();

        registry.registerSkill(skill);

        assertFalse(registry.isActive("active-skill"));
        assertEquals(0, registry.getActiveSkillCount());

        registry.activateSkill("active-skill");

        assertTrue(registry.isActive("active-skill"));
        assertEquals(1, registry.getActiveSkillCount());

        registry.deactivateSkill("active-skill");

        assertFalse(registry.isActive("active-skill"));
        assertEquals(0, registry.getActiveSkillCount());
    }

    @Test
    void shouldActivateMultipleSkills() {
        Skill skill1 = Skill.builder()
            .name("skill-1")
            .description("First")
            .instructions("Instructions 1")
            .build();

        Skill skill2 = Skill.builder()
            .name("skill-2")
            .description("Second")
            .instructions("Instructions 2")
            .build();

        registry.registerSkill(skill1);
        registry.registerSkill(skill2);

        registry.activateSkills("skill-1", "skill-2");

        assertEquals(2, registry.getActiveSkillCount());
        assertTrue(registry.isActive("skill-1"));
        assertTrue(registry.isActive("skill-2"));
    }

    @Test
    void shouldDeactivateAllSkills() {
        for (int i = 1; i <= 3; i++) {
            Skill skill = Skill.builder()
                .name("skill-" + i)
                .description("Skill " + i)
                .instructions("Instructions")
                .build();
            registry.registerSkill(skill);
            registry.activateSkill("skill-" + i);
        }

        assertEquals(3, registry.getActiveSkillCount());

        registry.deactivateAllSkills();

        assertEquals(0, registry.getActiveSkillCount());
    }

    @Test
    void shouldBuildSystemMessage() {
        Skill skill = Skill.builder()
            .name("test-skill")
            .description("A test skill")
            .instructions("Follow these instructions carefully")
            .build();

        registry.registerSkill(skill);
        registry.activateSkill("test-skill");

        String systemMessage = registry.buildSkillsSystemMessage();

        assertNotNull(systemMessage);
        assertTrue(systemMessage.contains("Active Skills"));
        assertTrue(systemMessage.contains("test-skill"));
        assertTrue(systemMessage.contains("A test skill"));
        assertTrue(systemMessage.contains("Follow these instructions"));
    }

    @Test
    void shouldReturnEmptySystemMessageWhenNoActiveSkills() {
        Skill skill = Skill.builder()
            .name("inactive-skill")
            .description("Not active")
            .instructions("Instructions")
            .build();

        registry.registerSkill(skill);

        String systemMessage = registry.buildSkillsSystemMessage();

        assertEquals("", systemMessage);
    }

    @Test
    void shouldCreateConversationMessage() {
        Skill skill = Skill.builder()
            .name("conv-skill")
            .description("Conversation skill")
            .instructions("Instructions")
            .build();

        registry.registerSkill(skill);
        registry.activateSkill("conv-skill");

        ConversationMessage message = registry.createSkillsSystemMessage();

        assertNotNull(message);
        assertEquals(ConversationMessage.MessageRole.SYSTEM, message.getRole());
        assertTrue(message.getTextContent().contains("conv-skill"));
    }

    @Test
    void shouldInjectSkillsIntoConversation() {
        Skill skill = Skill.builder()
            .name("injected-skill")
            .description("Skill to inject")
            .instructions("Use this skill wisely")
            .build();

        registry.registerSkill(skill);
        registry.activateSkill("injected-skill");

        List<ConversationMessage> originalMessages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Hello")
                .build()
        );

        List<ConversationMessage> enriched = registry.injectSkills(originalMessages);

        assertEquals(2, enriched.size());
        assertEquals(ConversationMessage.MessageRole.SYSTEM, enriched.get(0).getRole());
        assertTrue(enriched.get(0).getTextContent().contains("injected-skill"));
        assertEquals("Hello", enriched.get(1).getTextContent());
    }

    @Test
    void shouldNotInjectWhenNoActiveSkills() {
        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Hello")
                .build()
        );

        List<ConversationMessage> result = registry.injectSkills(messages);

        assertEquals(messages, result);
        assertEquals(1, result.size());
    }

    @Test
    void shouldSuggestRelevantSkills() {
        Skill documentSkill = Skill.builder()
            .name("document-creator")
            .description("Creates professional documents and reports")
            .instructions("Instructions")
            .build();

        Skill weatherSkill = Skill.builder()
            .name("weather-checker")
            .description("Checks weather conditions")
            .instructions("Instructions")
            .build();

        registry.registerSkill(documentSkill);
        registry.registerSkill(weatherSkill);

        List<String> suggestions = registry.suggestSkills("Create a document for me");

        assertTrue(suggestions.contains("document-creator"));
        assertFalse(suggestions.contains("weather-checker"));
    }

    @Test
    void shouldLoadSkillsFromDirectory(@TempDir Path tempDir) throws Exception {
        // Create sample skills
        SkillLoader.createSampleSkill(tempDir, "skill-a");
        SkillLoader.createSampleSkill(tempDir, "skill-b");

        registry.loadSkills(tempDir);

        assertEquals(2, registry.getSkillCount());
        assertTrue(registry.hasSkill("skill-a"));
        assertTrue(registry.hasSkill("skill-b"));
    }

    @Test
    void shouldClearAllSkills() {
        Skill skill = Skill.builder()
            .name("clear-test")
            .description("Test")
            .instructions("Instructions")
            .build();

        registry.registerSkill(skill);
        registry.activateSkill("clear-test");

        assertEquals(1, registry.getSkillCount());
        assertEquals(1, registry.getActiveSkillCount());

        registry.clear();

        assertEquals(0, registry.getSkillCount());
        assertEquals(0, registry.getActiveSkillCount());
    }
}
