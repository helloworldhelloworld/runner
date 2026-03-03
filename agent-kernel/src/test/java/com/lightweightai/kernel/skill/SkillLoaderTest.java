package com.lightweightai.kernel.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Claude Skills loading
 */
class SkillLoaderTest {

    @Test
    void shouldLoadSkillFromDirectory(@TempDir Path tempDir) throws Exception {
        // Create a test skill
        Path skillDir = tempDir.resolve("test-skill");
        Files.createDirectories(skillDir);

        String skillContent =
            "---\n" +
            "name: test-skill\n" +
            "description: A test skill for unit testing\n" +
            "version: 1.0.0\n" +
            "---\n" +
            "\n" +
            "# Test Skill\n" +
            "\n" +
            "This is a test skill for demonstration.\n" +
            "\n" +
            "## Instructions\n" +
            "1. Do step one\n" +
            "2. Do step two\n" +
            "\n" +
            "## Examples\n" +
            "- Example 1\n" +
            "- Example 2\n";

        Files.write(skillDir.resolve("SKILL.md"), skillContent.getBytes(StandardCharsets.UTF_8));

        // Add a resource
        Files.write(skillDir.resolve("example.txt"), "Example resource".getBytes(StandardCharsets.UTF_8));

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
        Files.write(skill1Dir.resolve("SKILL.md"), (
            "---\n" +
            "name: skill-1\n" +
            "description: First skill\n" +
            "---\n" +
            "# Skill 1\n" +
            "Instructions for skill 1\n").getBytes(StandardCharsets.UTF_8));

        // Create skill 2
        Path skill2Dir = tempDir.resolve("skill-2");
        Files.createDirectories(skill2Dir);
        Files.write(skill2Dir.resolve("SKILL.md"), (
            "---\n" +
            "name: skill-2\n" +
            "description: Second skill\n" +
            "---\n" +
            "# Skill 2\n" +
            "Instructions for skill 2\n").getBytes(StandardCharsets.UTF_8));

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

        String badContent =
            "# This is a skill without frontmatter\n" +
            "\n" +
            "This should fail to load.\n";

        Files.write(skillDir.resolve("SKILL.md"), badContent.getBytes(StandardCharsets.UTF_8));

        assertThrows(Exception.class, () -> SkillLoader.loadSkill(skillDir));
    }

    @Test
    void shouldFailWithoutRequiredFields(@TempDir Path tempDir) throws Exception {
        Path skillDir = tempDir.resolve("incomplete-skill");
        Files.createDirectories(skillDir);

        String incompleteContent =
            "---\n" +
            "name: incomplete\n" +
            "---\n" +
            "# Missing description\n";

        Files.write(skillDir.resolve("SKILL.md"), incompleteContent.getBytes(StandardCharsets.UTF_8));

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

        Files.write(skillDir.resolve("SKILL.md"), (
            "---\n" +
            "name: context-skill\n" +
            "description: Skill for context testing\n" +
            "author: Test Author\n" +
            "---\n" +
            "\n" +
            "# Context Skill\n" +
            "\n" +
            "Do these things when using this skill.\n").getBytes(StandardCharsets.UTF_8));

        Files.write(skillDir.resolve("data.txt"), "Some data".getBytes(StandardCharsets.UTF_8));

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
