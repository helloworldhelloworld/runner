package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.skill.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeSkillAdapter - Skill→InstructionPackage bridge")
class ClaudeSkillAdapterTest {

    private Skill skill;
    private ClaudeSkillAdapter adapter;

    @BeforeEach
    void setUp() {
        skill = Skill.builder()
                .name("code-review")
                .description("Review code for quality")
                .instructions("1. Read the diff\n2. Check style\n3. Report issues")
                .addMetadata("author", "team")
                .addMetadata("version", "1.0")
                .addResource("template.md", "## Review\n")
                .skillPath(Path.of("/skills/code-review"))
                .build();
        adapter = new ClaudeSkillAdapter(skill);
    }

    @Test
    @DisplayName("delegates getName to underlying Skill")
    void shouldDelegateName() {
        assertEquals("code-review", adapter.getName());
    }

    @Test
    @DisplayName("delegates getDescription to underlying Skill")
    void shouldDelegateDescription() {
        assertEquals("Review code for quality", adapter.getDescription());
    }

    @Test
    @DisplayName("delegates getInstructions to underlying Skill")
    void shouldDelegateInstructions() {
        assertEquals("1. Read the diff\n2. Check style\n3. Report issues", adapter.getInstructions());
    }

    @Test
    @DisplayName("delegates getMetadata to underlying Skill")
    void shouldDelegateMetadata() {
        assertEquals("team", adapter.getMetadata().get("author"));
        assertEquals("1.0", adapter.getMetadata().get("version"));
    }

    @Test
    @DisplayName("delegates getResources to underlying Skill")
    void shouldDelegateResources() {
        assertFalse(adapter.getResources().isEmpty());
        assertTrue(adapter.hasResource("template.md"));
    }

    @Test
    @DisplayName("delegates hasResource and getResource")
    void shouldDelegateResourceAccess() {
        assertTrue(adapter.hasResource("template.md"));
        assertFalse(adapter.hasResource("nonexistent.md"));

        byte[] content = adapter.getResource("template.md");
        assertNotNull(content);
        assertEquals("## Review\n", new String(content));
    }

    @Test
    @DisplayName("delegates getResourceAsString")
    void shouldDelegateGetResourceAsString() {
        assertEquals("## Review\n", adapter.getResourceAsString("template.md"));
        assertNull(adapter.getResourceAsString("nonexistent.md"));
    }

    @Test
    @DisplayName("getSourcePath returns skill path")
    void shouldReturnSkillPath() {
        assertEquals(Path.of("/skills/code-review"), adapter.getSourcePath());
    }

    @Test
    @DisplayName("getFormat returns anthropic-skill")
    void shouldReturnAnthropicSkillFormat() {
        assertEquals("anthropic-skill", adapter.getFormat());
    }

    @Test
    @DisplayName("toPromptContext delegates to Skill")
    void shouldDelegateToPromptContext() {
        String context = adapter.toPromptContext();
        assertTrue(context.contains("code-review"));
        assertTrue(context.contains("Review code for quality"));
        assertTrue(context.contains("Read the diff"));
    }

    @Test
    @DisplayName("getSkill returns the underlying Skill instance")
    void shouldReturnUnderlyingSkill() {
        assertSame(skill, adapter.getSkill());
    }

    @Test
    @DisplayName("from factory method creates adapter")
    void fromFactoryMethodShouldCreateAdapter() {
        ClaudeSkillAdapter fromFactory = ClaudeSkillAdapter.from(skill);
        assertNotNull(fromFactory);
        assertEquals("code-review", fromFactory.getName());
        assertSame(skill, fromFactory.getSkill());
    }
}
