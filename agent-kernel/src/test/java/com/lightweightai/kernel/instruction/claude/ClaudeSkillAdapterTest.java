package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.instruction.InstructionPackage;
import com.lightweightai.kernel.skill.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClaudeSkillAdapter — Skill → InstructionPackage adapter.
 *
 * Transmission test: verifies every Skill property is correctly
 * delegated through the adapter to the InstructionPackage interface.
 */
@DisplayName("ClaudeSkillAdapter — Skill to InstructionPackage delegation")
class ClaudeSkillAdapterTest {

    private Skill skill;
    private ClaudeSkillAdapter adapter;

    @BeforeEach
    void setUp() {
        skill = Skill.builder()
                .name("code-review")
                .description("Review code for quality")
                .instructions("## Instructions\nReview the code carefully.")
                .addMetadata("version", "2.0.0")
                .addMetadata("author", "test-author")
                .addResource("template.md", "# Template\nReview template here")
                .addResource("checklist.json", "{\"items\":[\"security\",\"perf\"]}")
                .skillPath(Path.of("/skills/code-review"))
                .build();

        adapter = new ClaudeSkillAdapter(skill);
    }

    @Test
    @DisplayName("getName() delegates to skill")
    void nameIsDelegated() {
        assertEquals("code-review", adapter.getName());
    }

    @Test
    @DisplayName("getDescription() delegates to skill")
    void descriptionIsDelegated() {
        assertEquals("Review code for quality", adapter.getDescription());
    }

    @Test
    @DisplayName("getInstructions() delegates to skill")
    void instructionsAreDelegated() {
        assertEquals("## Instructions\nReview the code carefully.", adapter.getInstructions());
    }

    @Test
    @DisplayName("getMetadata() delegates to skill and carries full payload")
    void metadataIsDelegated() {
        Map<String, String> meta = adapter.getMetadata();
        assertEquals("2.0.0", meta.get("version"));
        assertEquals("test-author", meta.get("author"));
    }

    @Test
    @DisplayName("getResources() carries all resource bytes")
    void resourcesAreDelegated() {
        Map<String, byte[]> resources = adapter.getResources();
        assertEquals(2, resources.size());
        assertTrue(resources.containsKey("template.md"));
        assertTrue(resources.containsKey("checklist.json"));
    }

    @Test
    @DisplayName("getSourcePath() delegates to skillPath")
    void sourcePathIsDelegated() {
        assertEquals(Path.of("/skills/code-review"), adapter.getSourcePath());
    }

    @Test
    @DisplayName("hasResource() correctly checks via skill")
    void hasResourceDelegates() {
        assertTrue(adapter.hasResource("template.md"));
        assertFalse(adapter.hasResource("nonexistent.txt"));
    }

    @Test
    @DisplayName("getResource() returns correct bytes")
    void getResourceDelegates() {
        byte[] content = adapter.getResource("template.md");
        assertNotNull(content);
        assertEquals("# Template\nReview template here", new String(content));
    }

    @Test
    @DisplayName("getResourceAsString() returns correct string")
    void getResourceAsStringDelegates() {
        String content = adapter.getResourceAsString("checklist.json");
        assertNotNull(content);
        assertTrue(content.contains("security"));
    }

    @Test
    @DisplayName("toPromptContext() produces skill-formatted context")
    void toPromptContextDelegates() {
        String context = adapter.toPromptContext();
        assertNotNull(context);
        assertTrue(context.contains("code-review"));
        assertTrue(context.contains("Review the code carefully"));
    }

    @Test
    @DisplayName("getFormat() returns anthropic-skill")
    void formatIsAnthropicSkill() {
        assertEquals("anthropic-skill", adapter.getFormat());
    }

    @Test
    @DisplayName("getSkill() returns the underlying Skill")
    void getSkillReturnsOriginal() {
        assertSame(skill, adapter.getSkill());
    }

    @Test
    @DisplayName("static from() factory method works")
    void staticFactoryWorks() {
        ClaudeSkillAdapter fromFactory = ClaudeSkillAdapter.from(skill);
        assertEquals(adapter.getName(), fromFactory.getName());
        assertSame(skill, fromFactory.getSkill());
    }

    @Test
    @DisplayName("adapter implements InstructionPackage interface")
    void implementsInterface() {
        assertInstanceOf(InstructionPackage.class, adapter);
    }
}
