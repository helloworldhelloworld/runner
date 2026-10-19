package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.skill.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeSkillAdapter -- wraps Skill as InstructionPackage")
class ClaudeSkillAdapterTest {

    private Skill skill;
    private ClaudeSkillAdapter adapter;

    @BeforeEach
    void setUp() {
        skill = Skill.builder()
                .name("test-skill")
                .description("A test skill for unit testing")
                .instructions("Step 1: Read the input. Step 2: Process it.")
                .addMetadata("version", "2.0.0")
                .addMetadata("author", "test-author")
                .addResource("template.txt", "Hello {{name}}")
                .skillPath(Path.of("/skills/test-skill"))
                .build();

        adapter = new ClaudeSkillAdapter(skill);
    }

    // ==================== Delegation ====================

    @Nested
    @DisplayName("Delegation to underlying Skill")
    class Delegation {

        @Test
        @DisplayName("getName() delegates to skill.getName()")
        void getNameDelegates() {
            assertEquals("test-skill", adapter.getName());
            assertEquals(skill.getName(), adapter.getName());
        }

        @Test
        @DisplayName("getDescription() delegates to skill.getDescription()")
        void getDescriptionDelegates() {
            assertEquals("A test skill for unit testing", adapter.getDescription());
            assertEquals(skill.getDescription(), adapter.getDescription());
        }

        @Test
        @DisplayName("getInstructions() delegates to skill.getInstructions()")
        void getInstructionsDelegates() {
            assertEquals("Step 1: Read the input. Step 2: Process it.", adapter.getInstructions());
            assertEquals(skill.getInstructions(), adapter.getInstructions());
        }

        @Test
        @DisplayName("getMetadata() delegates to skill.getMetadata()")
        void getMetadataDelegates() {
            Map<String, String> metadata = adapter.getMetadata();

            assertEquals(skill.getMetadata(), metadata);
            assertEquals("2.0.0", metadata.get("version"));
            assertEquals("test-author", metadata.get("author"));
        }

        @Test
        @DisplayName("getResources() delegates to skill.getResources()")
        void getResourcesDelegates() {
            Map<String, byte[]> resources = adapter.getResources();

            assertEquals(skill.getResources().size(), resources.size());
            assertTrue(resources.containsKey("template.txt"));
        }

        @Test
        @DisplayName("getSourcePath() delegates to skill.getSkillPath()")
        void getSourcePathDelegates() {
            assertEquals(Path.of("/skills/test-skill"), adapter.getSourcePath());
            assertEquals(skill.getSkillPath(), adapter.getSourcePath());
        }

        @Test
        @DisplayName("hasResource() delegates to skill.hasResource()")
        void hasResourceDelegates() {
            assertTrue(adapter.hasResource("template.txt"));
            assertFalse(adapter.hasResource("nonexistent.txt"));
        }

        @Test
        @DisplayName("getResource() delegates to skill.getResource()")
        void getResourceDelegates() {
            byte[] resource = adapter.getResource("template.txt");

            assertNotNull(resource);
            assertArrayEquals(skill.getResource("template.txt"), resource);
        }

        @Test
        @DisplayName("getResource() returns null for nonexistent resource")
        void getResourceReturnsNullForNonexistent() {
            assertNull(adapter.getResource("does-not-exist"));
        }

        @Test
        @DisplayName("getResourceAsString() delegates to skill.getResourceAsString()")
        void getResourceAsStringDelegates() {
            String content = adapter.getResourceAsString("template.txt");

            assertEquals("Hello {{name}}", content);
            assertEquals(skill.getResourceAsString("template.txt"), content);
        }

        @Test
        @DisplayName("getResourceAsString() returns null for nonexistent resource")
        void getResourceAsStringReturnsNullForNonexistent() {
            assertNull(adapter.getResourceAsString("missing.txt"));
        }

        @Test
        @DisplayName("toPromptContext() delegates to skill.toPromptContext()")
        void toPromptContextDelegates() {
            String context = adapter.toPromptContext();

            assertEquals(skill.toPromptContext(), context);
            assertTrue(context.contains("test-skill"), "prompt context should contain skill name");
            assertTrue(context.contains("A test skill for unit testing"),
                    "prompt context should contain description");
        }
    }

    // ==================== getFormat ====================

    @Nested
    @DisplayName("getFormat()")
    class GetFormat {

        @Test
        @DisplayName("returns 'anthropic-skill'")
        void returnsAnthropicSkill() {
            assertEquals("anthropic-skill", adapter.getFormat());
        }
    }

    // ==================== getSkill ====================

    @Nested
    @DisplayName("getSkill()")
    class GetSkill {

        @Test
        @DisplayName("returns the underlying Skill instance")
        void returnsUnderlyingSkill() {
            assertSame(skill, adapter.getSkill(),
                    "getSkill() should return the exact same Skill instance");
        }
    }

    // ==================== Static factory ====================

    @Nested
    @DisplayName("from() static factory method")
    class FromFactory {

        @Test
        @DisplayName("creates adapter that wraps the given Skill")
        void createsAdapterFromSkill() {
            Skill anotherSkill = Skill.builder()
                    .name("factory-skill")
                    .description("Created via factory")
                    .instructions("Factory instructions")
                    .build();

            ClaudeSkillAdapter factoryAdapter = ClaudeSkillAdapter.from(anotherSkill);

            assertNotNull(factoryAdapter);
            assertSame(anotherSkill, factoryAdapter.getSkill());
            assertEquals("factory-skill", factoryAdapter.getName());
            assertEquals("anthropic-skill", factoryAdapter.getFormat());
        }
    }

    // ==================== InstructionPackage default methods ====================

    @Nested
    @DisplayName("InstructionPackage default methods")
    class DefaultMethods {

        @Test
        @DisplayName("getVersion() returns version from metadata")
        void getVersionFromMetadata() {
            assertEquals("2.0.0", adapter.getVersion(),
                    "getVersion() should return version from skill metadata");
        }

        @Test
        @DisplayName("getAuthor() returns author from metadata")
        void getAuthorFromMetadata() {
            assertEquals("test-author", adapter.getAuthor(),
                    "getAuthor() should return author from skill metadata");
        }
    }

    // ==================== Edge cases ====================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("works with minimal Skill (no resources, no metadata, no path)")
        void worksWithMinimalSkill() {
            Skill minimal = Skill.builder()
                    .name("minimal")
                    .description("Bare minimum")
                    .instructions("Just do it")
                    .build();

            ClaudeSkillAdapter minAdapter = ClaudeSkillAdapter.from(minimal);

            assertEquals("minimal", minAdapter.getName());
            assertEquals("Bare minimum", minAdapter.getDescription());
            assertEquals("Just do it", minAdapter.getInstructions());
            assertTrue(minAdapter.getResources().isEmpty());
            assertNull(minAdapter.getSourcePath());
            assertFalse(minAdapter.hasResource("anything"));
            assertEquals("anthropic-skill", minAdapter.getFormat());
        }
    }
}
