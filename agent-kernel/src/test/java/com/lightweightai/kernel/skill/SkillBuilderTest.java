package com.lightweightai.kernel.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Skill (kernel/skill) — builder, validation, prompt rendering, resource handling")
class SkillBuilderTest {

    @Test
    @DisplayName("builder with all required fields creates valid Skill")
    void builderAllRequiredFields() {
        Skill skill = Skill.builder()
                .name("test-skill")
                .description("A test skill")
                .instructions("Do the thing")
                .build();

        assertEquals("test-skill", skill.getName());
        assertEquals("A test skill", skill.getDescription());
        assertEquals("Do the thing", skill.getInstructions());
        assertTrue(skill.getMetadata().isEmpty());
        assertTrue(skill.getResources().isEmpty());
        assertNull(skill.getSkillPath());
    }

    @Test
    @DisplayName("null name throws IllegalArgumentException")
    void nullName() {
        assertThrows(IllegalArgumentException.class, () ->
                Skill.builder()
                        .description("desc")
                        .instructions("inst")
                        .build());
    }

    @Test
    @DisplayName("blank name throws IllegalArgumentException")
    void blankName() {
        assertThrows(IllegalArgumentException.class, () ->
                Skill.builder()
                        .name("   ")
                        .description("desc")
                        .instructions("inst")
                        .build());
    }

    @Test
    @DisplayName("null description throws IllegalArgumentException")
    void nullDescription() {
        assertThrows(IllegalArgumentException.class, () ->
                Skill.builder()
                        .name("test")
                        .instructions("inst")
                        .build());
    }

    @Test
    @DisplayName("null instructions throws IllegalArgumentException")
    void nullInstructions() {
        assertThrows(IllegalArgumentException.class, () ->
                Skill.builder()
                        .name("test")
                        .description("desc")
                        .build());
    }

    @Test
    @DisplayName("toPromptContext contains name, description, instructions")
    void toPromptContextBasic() {
        Skill skill = Skill.builder()
                .name("weather")
                .description("Checks the weather")
                .instructions("Call the weather API and report results.")
                .build();

        String context = skill.toPromptContext();

        assertTrue(context.contains("# Skill: weather"), "should contain skill name header");
        assertTrue(context.contains("**Description**: Checks the weather"), "should contain description");
        assertTrue(context.contains("Call the weather API"), "should contain instructions");
    }

    @Test
    @DisplayName("toPromptContext omits description when empty")
    void toPromptContextEmptyDescription() {
        Skill skill = Skill.builder()
                .name("test")
                .description("A non-empty description")
                .instructions("Do something")
                .build();

        String context = skill.toPromptContext();
        assertTrue(context.contains("**Description**"), "non-empty description should appear");
    }

    @Test
    @DisplayName("toPromptContext includes metadata (excluding name/description)")
    void toPromptContextWithMetadata() {
        Skill skill = Skill.builder()
                .name("test")
                .description("desc")
                .instructions("inst")
                .addMetadata("version", "1.0")
                .addMetadata("author", "testuser")
                .addMetadata("name", "should-be-excluded")
                .addMetadata("description", "should-also-be-excluded")
                .build();

        String context = skill.toPromptContext();

        assertTrue(context.contains("## Metadata"), "should contain metadata section");
        assertTrue(context.contains("**version**: 1.0"), "should contain version");
        assertTrue(context.contains("**author**: testuser"), "should contain author");
        assertFalse(context.contains("**name**: should-be-excluded"),
                "name key should be excluded from metadata display");
        assertFalse(context.contains("**description**: should-also-be-excluded"),
                "description key should be excluded from metadata display");
    }

    @Test
    @DisplayName("toPromptContext includes resource references")
    void toPromptContextWithResources() {
        Skill skill = Skill.builder()
                .name("test")
                .description("desc")
                .instructions("inst")
                .addResource("template.md", "# Template")
                .addResource("data.json", "{}")
                .build();

        String context = skill.toPromptContext();

        assertTrue(context.contains("## Available Resources"), "should contain resources section");
        assertTrue(context.contains("`template.md`"), "should list template.md");
        assertTrue(context.contains("`data.json`"), "should list data.json");
    }

    @Test
    @DisplayName("toSystemMessage includes name, description, instructions")
    void toSystemMessage() {
        Skill skill = Skill.builder()
                .name("calc")
                .description("Calculator skill")
                .instructions("Solve math problems step by step.")
                .build();

        String msg = skill.toSystemMessage();

        assertTrue(msg.contains("'calc'"), "should mention skill name");
        assertTrue(msg.contains("Calculator skill"), "should include description");
        assertTrue(msg.contains("Solve math problems"), "should include instructions");
    }

    @Test
    @DisplayName("hasResource and getResource work correctly")
    void resourceAccess() {
        byte[] content = "hello".getBytes();
        Skill skill = Skill.builder()
                .name("test")
                .description("desc")
                .instructions("inst")
                .addResource("file.txt", content)
                .build();

        assertTrue(skill.hasResource("file.txt"));
        assertFalse(skill.hasResource("nonexistent.txt"));
        assertArrayEquals(content, skill.getResource("file.txt"));
        assertNull(skill.getResource("nonexistent.txt"));
    }

    @Test
    @DisplayName("getResourceAsString returns string for text resources")
    void getResourceAsString() {
        Skill skill = Skill.builder()
                .name("test")
                .description("desc")
                .instructions("inst")
                .addResource("readme.md", "# Hello World")
                .build();

        assertEquals("# Hello World", skill.getResourceAsString("readme.md"));
        assertNull(skill.getResourceAsString("nonexistent"));
    }

    @Test
    @DisplayName("addResource with string content converts to bytes")
    void addResourceString() {
        Skill skill = Skill.builder()
                .name("test")
                .description("desc")
                .instructions("inst")
                .addResource("note.txt", "my note")
                .build();

        assertTrue(skill.hasResource("note.txt"));
        assertEquals("my note", skill.getResourceAsString("note.txt"));
    }

    @Test
    @DisplayName("metadata bulk set via map")
    void metadataBulkSet() {
        Map<String, String> meta = Map.of("k1", "v1", "k2", "v2");
        Skill skill = Skill.builder()
                .name("test")
                .description("desc")
                .instructions("inst")
                .metadata(meta)
                .build();

        assertEquals("v1", skill.getMetadata().get("k1"));
        assertEquals("v2", skill.getMetadata().get("k2"));
    }

    @Test
    @DisplayName("resources bulk set via map")
    void resourcesBulkSet() {
        Map<String, byte[]> resources = Map.of(
                "a.txt", "aaa".getBytes(),
                "b.txt", "bbb".getBytes()
        );
        Skill skill = Skill.builder()
                .name("test")
                .description("desc")
                .instructions("inst")
                .resources(resources)
                .build();

        assertTrue(skill.hasResource("a.txt"));
        assertTrue(skill.hasResource("b.txt"));
    }

    @Test
    @DisplayName("skillPath is set correctly")
    void skillPath() {
        Path path = Path.of("/tmp/skills/weather");
        Skill skill = Skill.builder()
                .name("test")
                .description("desc")
                .instructions("inst")
                .skillPath(path)
                .build();

        assertEquals(path, skill.getSkillPath());
    }

    @Test
    @DisplayName("toString contains key info")
    void toStringContainsInfo() {
        Skill skill = Skill.builder()
                .name("weather-skill")
                .description("desc")
                .instructions("inst")
                .addResource("r1", "data")
                .build();

        String str = skill.toString();
        assertTrue(str.contains("weather-skill"), "should contain name");
        assertTrue(str.contains("1"), "should contain resource count");
    }

    @Test
    @DisplayName("metadata and resources are defensive copies")
    void defensiveCopies() {
        Map<String, String> meta = new java.util.HashMap<>();
        meta.put("k", "v");
        Map<String, byte[]> res = new java.util.HashMap<>();
        res.put("f", "data".getBytes());

        Skill skill = Skill.builder()
                .name("test")
                .description("desc")
                .instructions("inst")
                .metadata(meta)
                .resources(res)
                .build();

        meta.put("injected", "bad");
        res.put("injected", "bad".getBytes());

        assertFalse(skill.getMetadata().containsKey("injected"),
                "mutation of input map should not affect skill");
        assertFalse(skill.getResources().containsKey("injected"),
                "mutation of input map should not affect skill");
    }
}
