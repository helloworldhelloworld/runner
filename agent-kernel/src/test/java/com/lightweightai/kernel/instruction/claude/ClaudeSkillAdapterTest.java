package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.skill.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeSkillAdapter - wraps Skill as InstructionPackage")
class ClaudeSkillAdapterTest {

    private Skill skill;
    private ClaudeSkillAdapter adapter;

    @BeforeEach
    void setUp() {
        skill = Skill.builder()
                .name("code-review")
                .description("Reviews code for quality")
                .instructions("Review the code for bugs, style, and performance.")
                .addMetadata("version", "2.0")
                .addMetadata("author", "team")
                .addResource("template.md", "# Review\n- [ ] Bugs\n- [ ] Style")
                .skillPath(Path.of("/skills/code-review"))
                .build();

        adapter = new ClaudeSkillAdapter(skill);
    }

    @Test
    @DisplayName("getName delegates to Skill")
    void testName() {
        assertEquals("code-review", adapter.getName());
    }

    @Test
    @DisplayName("getDescription delegates to Skill")
    void testDescription() {
        assertEquals("Reviews code for quality", adapter.getDescription());
    }

    @Test
    @DisplayName("getInstructions delegates to Skill")
    void testInstructions() {
        assertEquals("Review the code for bugs, style, and performance.", adapter.getInstructions());
    }

    @Test
    @DisplayName("getMetadata delegates to Skill")
    void testMetadata() {
        Map<String, String> metadata = adapter.getMetadata();
        assertEquals("2.0", metadata.get("version"));
        assertEquals("team", metadata.get("author"));
    }

    @Test
    @DisplayName("getResources delegates to Skill")
    void testResources() {
        assertFalse(adapter.getResources().isEmpty());
        assertTrue(adapter.getResources().containsKey("template.md"));
    }

    @Test
    @DisplayName("getSourcePath delegates to Skill skillPath")
    void testSourcePath() {
        assertEquals(Path.of("/skills/code-review"), adapter.getSourcePath());
    }

    @Test
    @DisplayName("hasResource delegates to Skill")
    void testHasResource() {
        assertTrue(adapter.hasResource("template.md"));
        assertFalse(adapter.hasResource("nonexistent.txt"));
    }

    @Test
    @DisplayName("getResource returns resource bytes")
    void testGetResource() {
        byte[] content = adapter.getResource("template.md");
        assertNotNull(content);
        assertTrue(new String(content).contains("Review"));
    }

    @Test
    @DisplayName("getResourceAsString returns resource as string")
    void testGetResourceAsString() {
        String content = adapter.getResourceAsString("template.md");
        assertNotNull(content);
        assertTrue(content.contains("Review"));
    }

    @Test
    @DisplayName("toPromptContext delegates to Skill")
    void testToPromptContext() {
        String context = adapter.toPromptContext();
        assertTrue(context.contains("code-review"));
        assertTrue(context.contains("Reviews code for quality"));
        assertTrue(context.contains("Review the code for bugs"));
    }

    @Test
    @DisplayName("getFormat returns 'anthropic-skill'")
    void testFormat() {
        assertEquals("anthropic-skill", adapter.getFormat());
    }

    @Test
    @DisplayName("getSkill returns the underlying Skill")
    void testGetSkill() {
        assertSame(skill, adapter.getSkill());
    }

    @Test
    @DisplayName("static from() factory method creates adapter")
    void testFromFactory() {
        ClaudeSkillAdapter fromFactory = ClaudeSkillAdapter.from(skill);
        assertEquals("code-review", fromFactory.getName());
        assertSame(skill, fromFactory.getSkill());
    }

    @Test
    @DisplayName("getVersion reads from metadata")
    void testVersion() {
        assertEquals("2.0", adapter.getVersion());
    }

    @Test
    @DisplayName("getAuthor reads from metadata")
    void testAuthor() {
        assertEquals("team", adapter.getAuthor());
    }
}
