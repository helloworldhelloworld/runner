package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.skill.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClaudeSkillAdapter
 */
class ClaudeSkillAdapterTest {

    private Skill skill;
    private ClaudeSkillAdapter adapter;

    @BeforeEach
    void setUp() {
        skill = Skill.builder()
                .name("test-skill")
                .description("A test skill")
                .instructions("Do the thing")
                .addMetadata("author", "tester")
                .addMetadata("version", "2.0.0")
                .addResource("template.txt", "hello world")
                .build();
        adapter = new ClaudeSkillAdapter(skill);
    }

    @Test
    void delegatesName() {
        assertEquals(skill.getName(), adapter.getName());
    }

    @Test
    void delegatesDescription() {
        assertEquals(skill.getDescription(), adapter.getDescription());
    }

    @Test
    void delegatesInstructions() {
        assertEquals(skill.getInstructions(), adapter.getInstructions());
    }

    @Test
    void delegatesMetadata() {
        assertEquals(skill.getMetadata(), adapter.getMetadata());
    }

    @Test
    void delegatesResources() {
        assertEquals(skill.getResources().keySet(), adapter.getResources().keySet());
        assertArrayEquals(
                skill.getResources().get("template.txt"),
                adapter.getResources().get("template.txt")
        );
    }

    @Test
    void delegatesSourcePath() {
        assertEquals(skill.getSkillPath(), adapter.getSourcePath());
    }

    @Test
    void delegatesHasResource() {
        assertTrue(adapter.hasResource("template.txt"));
        assertFalse(adapter.hasResource("nonexistent.txt"));
    }

    @Test
    void delegatesGetResource() {
        assertArrayEquals(
                skill.getResource("template.txt"),
                adapter.getResource("template.txt")
        );
    }

    @Test
    void delegatesGetResourceAsString() {
        assertEquals(
                skill.getResourceAsString("template.txt"),
                adapter.getResourceAsString("template.txt")
        );
    }

    @Test
    void delegatesToPromptContext() {
        assertEquals(skill.toPromptContext(), adapter.toPromptContext());
    }

    @Test
    void formatIsAnthropicSkill() {
        assertEquals("anthropic-skill", adapter.getFormat());
    }

    @Test
    void getSkillReturnsSameInstance() {
        assertSame(skill, adapter.getSkill());
    }

    @Test
    void fromFactoryMethodCreatesAdapter() {
        ClaudeSkillAdapter fromAdapter = ClaudeSkillAdapter.from(skill);

        assertNotNull(fromAdapter);
        assertEquals(skill.getName(), fromAdapter.getName());
        assertEquals(skill.getDescription(), fromAdapter.getDescription());
        assertEquals(skill.getInstructions(), fromAdapter.getInstructions());
        assertSame(skill, fromAdapter.getSkill());
    }

    @Test
    void versionDefaultsFromMetadata() {
        // Skill with explicit version metadata
        assertEquals("2.0.0", adapter.getVersion());

        // Skill without version metadata — should default to "1.0.0"
        Skill noVersionSkill = Skill.builder()
                .name("no-ver")
                .description("No version")
                .instructions("Do stuff")
                .build();
        ClaudeSkillAdapter noVersionAdapter = new ClaudeSkillAdapter(noVersionSkill);
        assertEquals("1.0.0", noVersionAdapter.getVersion());
    }

    @Test
    void authorDefaultsFromMetadata() {
        // Skill with explicit author metadata
        assertEquals("tester", adapter.getAuthor());

        // Skill without author metadata — should default to "Unknown"
        Skill noAuthorSkill = Skill.builder()
                .name("no-author")
                .description("No author")
                .instructions("Do stuff")
                .build();
        ClaudeSkillAdapter noAuthorAdapter = new ClaudeSkillAdapter(noAuthorSkill);
        assertEquals("Unknown", noAuthorAdapter.getAuthor());
    }
}
