package com.lightweightai.kernel.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for Claude Skills loading
 */
class SkillLoaderTest {

    @Test
    void shouldLoadSkillFromDirectory(@TempDir Path tempDir) throws Exception {
        // Create a test skill
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectories(skillDir);

        String skillContent = """
            ---
            name: test-skill
            description: A test skill for unit testing
            version: 1.0.0
            ---

            # Test Skill

            This is a test skill for demonstration.

            ## Instructions
            1. Do step one
            2. Do step two

            ## Examples
            - Example 1
            - Example 2
            """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillContent);

        // Add a resource
        Files.writeString(skillDir.resolve("example.txt"), "Example resource");

        // Load the skill
        Skill skill = SkillLoader.loadSkill(skillDir);

        // Verify
        assertNotNull(skill);
        assertEquals("test-skill", skill.getName());
        assertEquals("A test skill for unit testing", skill.getDescription());
        assertTrue(skill.getInstructions().contains("Do step one"));
        assertEquals("1.0.0", skill.getMetadata().get("version"));

        // Verify resources
        assertTrue(skill.hasResource("example.txt"));
        assertEquals("Example resource", skill.getResourceAsString("example.txt"));
    }

    @Test
    void shouldLoadMultipleSkills(@TempDir Path tempDir) throws Exception {
        // Create skill 1
        Path skill1Dir = tempDir.resolve("skill-1");
        Files.createDirectories(skill1Dir);
        Files.writeString(skill1Dir.resolve("SKILL.md"), """
            ---
            name: skill-1
            description: First skill
            ---
            # Skill 1
            Instructions for skill 1
            """);

        // Create skill 2
        Path skill2Dir = tempDir.resolve("skill-2");
        Files.createDirectories(skill2Dir);
        Files.writeString(skill2Dir.resolve("SKILL.md"), """
            ---
            name: skill-2
            description: Second skill
            ---
            # Skill 2
            Instructions for skill 2
            """);

        // Load all skills
        Map<String, Skill> skills = SkillLoader.loadSkills(tempDir);

        assertEquals(2, skills.size());
        assertTrue(skills.containsKey("skill-1"));
        assertTrue(skills.containsKey("skill-2"));
    }

    @Test
    void shouldFailWithoutFrontmatter(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("bad-skill");
        Files.createDirectories(skillDir);

        String badContent = """
            # This is a skill without frontmatter

            This should fail to load.
            """;

        Files.writeString(skillDir.resolve("SKILL.md"), badContent);

        assertThrows(Exception.class, () -> SkillLoader.loadSkill(skillDir));
    }

    @Test
    void shouldFailWithoutRequiredFields(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("incomplete-skill");
        Files.createDirectories(skillDir);

        String incompleteContent = """
            ---
            name: incomplete
            ---
            # Missing description
            """;

        Files.writeString(skillDir.resolve("SKILL.md"), incompleteContent);

        assertThrows(Exception.class, () -> SkillLoader.loadSkill(skillDir));
    }

    @Test
    void shouldCreateSampleSkill(@TempDir Path tempDir) throws Exception {
        SkillLoader.createSampleSkill(tempDir, "my-sample-skill");

        Path skillDir = tempDir.resolve("my-sample-skill");
        assertTrue(Files.exists(skillDir.resolve("SKILL.md")));
        assertTrue(Files.exists(skillDir.resolve("example.txt")));

        Skill skill = SkillLoader.loadSkill(skillDir);
        assertEquals("my-sample-skill", skill.getName());
    }

    @Test
    void shouldConvertSkillToPromptContext(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("context-skill");
        Files.createDirectories(skillDir);

        Files.writeString(skillDir.resolve("SKILL.md"), """
            ---
            name: context-skill
            description: Skill for context testing
            author: Test Author
            ---

            # Context Skill

            Do these things when using this skill.
            """);

        Files.writeString(skillDir.resolve("data.txt"), "Some data");

        Skill skill = SkillLoader.loadSkill(skillDir);
        String context = skill.toPromptContext();

        assertTrue(context.contains("# Skill: context-skill"));
        assertTrue(context.contains("Description"));
        assertTrue(context.contains("Skill for context testing"));
        assertTrue(context.contains("Do these things"));
        assertTrue(context.contains("Available Resources"));
        assertTrue(context.contains("data.txt"));
    }
}
