package com.lightweightai.kernel.skill;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Skill (kernel) - Instruction-based task package")
class SkillTest {

    @Nested
    @DisplayName("Builder 验证")
    class BuilderValidation {

        @Test
        @DisplayName("必填字段完整时正常构建")
        void shouldBuildWithAllRequiredFields() {
            Skill skill = Skill.builder()
                    .name("code-review")
                    .description("Review code changes")
                    .instructions("Read the diff and provide feedback")
                    .build();

            assertEquals("code-review", skill.getName());
            assertEquals("Review code changes", skill.getDescription());
            assertEquals("Read the diff and provide feedback", skill.getInstructions());
            assertTrue(skill.getMetadata().isEmpty());
            assertTrue(skill.getResources().isEmpty());
            assertNull(skill.getSkillPath());
        }

        @Test
        @DisplayName("缺少 name 时抛异常")
        void shouldRejectMissingName() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    Skill.builder()
                            .description("desc")
                            .instructions("instr")
                            .build());
            assertTrue(ex.getMessage().contains("name"));
        }

        @Test
        @DisplayName("空白 name 时抛异常")
        void shouldRejectBlankName() {
            assertThrows(IllegalArgumentException.class, () ->
                    Skill.builder()
                            .name("  ")
                            .description("desc")
                            .instructions("instr")
                            .build());
        }

        @Test
        @DisplayName("缺少 description 时抛异常")
        void shouldRejectMissingDescription() {
            assertThrows(IllegalArgumentException.class, () ->
                    Skill.builder()
                            .name("test")
                            .instructions("instr")
                            .build());
        }

        @Test
        @DisplayName("缺少 instructions 时抛异常")
        void shouldRejectMissingInstructions() {
            assertThrows(IllegalArgumentException.class, () ->
                    Skill.builder()
                            .name("test")
                            .description("desc")
                            .build());
        }
    }

    @Nested
    @DisplayName("Resource 管理")
    class ResourceManagement {

        @Test
        @DisplayName("添加 byte[] 资源并检索")
        void shouldStoreAndRetrieveByteResource() {
            byte[] data = "template content".getBytes();
            Skill skill = minimalSkill()
                    .addResource("template.txt", data)
                    .build();

            assertTrue(skill.hasResource("template.txt"));
            assertArrayEquals(data, skill.getResource("template.txt"));
        }

        @Test
        @DisplayName("添加 String 资源并检索")
        void shouldStoreAndRetrieveStringResource() {
            Skill skill = minimalSkill()
                    .addResource("config.yaml", "key: value")
                    .build();

            assertTrue(skill.hasResource("config.yaml"));
            assertEquals("key: value", skill.getResourceAsString("config.yaml"));
        }

        @Test
        @DisplayName("不存在的资源返回 null")
        void shouldReturnNullForMissingResource() {
            Skill skill = minimalSkill().build();

            assertFalse(skill.hasResource("missing.txt"));
            assertNull(skill.getResource("missing.txt"));
            assertNull(skill.getResourceAsString("missing.txt"));
        }

        @Test
        @DisplayName("批量设置资源")
        void shouldSetMultipleResources() {
            Skill skill = minimalSkill()
                    .resources(Map.of("a.txt", "aaa".getBytes(), "b.txt", "bbb".getBytes()))
                    .build();

            assertEquals(2, skill.getResources().size());
            assertTrue(skill.hasResource("a.txt"));
            assertTrue(skill.hasResource("b.txt"));
        }
    }

    @Nested
    @DisplayName("Prompt 生成")
    class PromptGeneration {

        @Test
        @DisplayName("toPromptContext 包含 name、description、instructions")
        void promptContextContainsAllFields() {
            Skill skill = minimalSkill().build();

            String context = skill.toPromptContext();
            assertTrue(context.contains("# Skill: test-skill"));
            assertTrue(context.contains("**Description**: A test skill"));
            assertTrue(context.contains("Do the thing"));
        }

        @Test
        @DisplayName("toPromptContext 包含 metadata（排除 name/description）")
        void promptContextIncludesMetadata() {
            Skill skill = minimalSkill()
                    .addMetadata("version", "1.0")
                    .addMetadata("author", "test")
                    .addMetadata("name", "should-be-excluded")
                    .addMetadata("description", "should-be-excluded")
                    .build();

            String context = skill.toPromptContext();
            assertTrue(context.contains("version"));
            assertTrue(context.contains("1.0"));
            assertTrue(context.contains("author"));
            assertFalse(context.contains("should-be-excluded"));
        }

        @Test
        @DisplayName("toPromptContext 包含资源引用列表")
        void promptContextIncludesResourceReferences() {
            Skill skill = minimalSkill()
                    .addResource("script.sh", "#!/bin/bash")
                    .addResource("data.json", "{}")
                    .build();

            String context = skill.toPromptContext();
            assertTrue(context.contains("Available Resources"));
            assertTrue(context.contains("`script.sh`"));
            assertTrue(context.contains("`data.json`"));
        }

        @Test
        @DisplayName("toSystemMessage 格式正确")
        void systemMessageFormatCorrect() {
            Skill skill = minimalSkill().build();

            String msg = skill.toSystemMessage();
            assertTrue(msg.contains("'test-skill' skill"));
            assertTrue(msg.contains("A test skill"));
            assertTrue(msg.contains("Do the thing"));
        }
    }

    @Nested
    @DisplayName("其他")
    class Misc {

        @Test
        @DisplayName("skillPath 正确设置")
        void skillPathIsStored() {
            Path path = Path.of("/skills/code-review");
            Skill skill = minimalSkill().skillPath(path).build();

            assertEquals(path, skill.getSkillPath());
        }

        @Test
        @DisplayName("toString 包含关键信息")
        void toStringContainsKeyInfo() {
            Skill skill = minimalSkill()
                    .addResource("file.txt", "content")
                    .build();

            String str = skill.toString();
            assertTrue(str.contains("test-skill"));
            assertTrue(str.contains("resourceCount=1"));
        }

        @Test
        @DisplayName("metadata 可通过 Map 批量设置")
        void metadataBulkSet() {
            Skill skill = minimalSkill()
                    .metadata(Map.of("k1", "v1", "k2", "v2"))
                    .build();

            assertEquals(2, skill.getMetadata().size());
            assertEquals("v1", skill.getMetadata().get("k1"));
        }
    }

    private Skill.Builder minimalSkill() {
        return Skill.builder()
                .name("test-skill")
                .description("A test skill")
                .instructions("Do the thing");
    }
}
