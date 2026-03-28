package com.lightweightai.kernel.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Skill - 能力单元构建与触发匹配")
class SkillTest {

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("完整构建")
        void fullBuild() {
            Skill skill = Skill.builder()
                .name("weather")
                .description("Weather lookup")
                .systemPrompt("You can check weather.")
                .addTool("get_weather", "Get weather", Map.of("city", Map.of("type", "string")))
                .addTrigger("weather")
                .addTrigger("forecast")
                .priority(50)
                .metadata("source", "mcp")
                .build();

            assertEquals("weather", skill.getName());
            assertEquals("Weather lookup", skill.getDescription());
            assertEquals("You can check weather.", skill.getSystemPrompt());
            assertEquals(1, skill.getTools().size());
            assertEquals("get_weather", skill.getTools().get(0).getName());
            assertEquals(List.of("weather", "forecast"), skill.getTriggers());
            assertEquals(50, skill.getPriority());
            assertEquals("mcp", skill.getMetadata().get("source"));
        }

        @Test
        @DisplayName("name 必填")
        void nameRequired() {
            assertThrows(NullPointerException.class,
                () -> Skill.builder().build());
        }

        @Test
        @DisplayName("默认值")
        void defaults() {
            Skill skill = Skill.builder().name("test").build();

            assertEquals("", skill.getDescription());
            assertEquals("", skill.getSystemPrompt());
            assertTrue(skill.getTools().isEmpty());
            assertTrue(skill.getToolInstances().isEmpty());
            assertTrue(skill.getTriggers().isEmpty());
            assertEquals(100, skill.getPriority());
            assertTrue(skill.getMetadata().isEmpty());
        }

        @Test
        @DisplayName("triggers(List) 批量设置")
        void triggersListSetter() {
            Skill skill = Skill.builder()
                .name("test")
                .triggers(List.of("a", "b", "c"))
                .build();

            assertEquals(3, skill.getTriggers().size());
        }
    }

    @Nested
    @DisplayName("matchesTrigger")
    class TriggerMatchTests {

        @Test
        @DisplayName("消息包含触发词时匹配")
        void matchesWhenContainsTrigger() {
            Skill skill = Skill.builder()
                .name("weather")
                .addTrigger("天气")
                .addTrigger("weather")
                .build();

            assertTrue(skill.matchesTrigger("今天天气怎么样？"));
            assertTrue(skill.matchesTrigger("What's the weather like?"));
        }

        @Test
        @DisplayName("大小写不敏感匹配")
        void caseInsensitiveMatch() {
            Skill skill = Skill.builder()
                .name("search")
                .addTrigger("search")
                .build();

            assertTrue(skill.matchesTrigger("SEARCH for something"));
            assertTrue(skill.matchesTrigger("Search Results"));
        }

        @Test
        @DisplayName("无触发词时不匹配")
        void noTriggersNeverMatches() {
            Skill skill = Skill.builder().name("test").build();
            assertFalse(skill.matchesTrigger("anything"));
        }

        @Test
        @DisplayName("消息不包含触发词时不匹配")
        void doesNotMatchUnrelated() {
            Skill skill = Skill.builder()
                .name("weather")
                .addTrigger("天气")
                .build();

            assertFalse(skill.matchesTrigger("你好"));
        }
    }

    @Nested
    @DisplayName("ToolDefinition")
    class ToolDefinitionTests {

        @Test
        @DisplayName("字段和 toSchema")
        void fieldsAndToSchema() {
            Skill.ToolDefinition td = new Skill.ToolDefinition(
                "search", "Search the web", Map.of("query", Map.of("type", "string")));

            assertEquals("search", td.getName());
            assertEquals("Search the web", td.getDescription());
            assertEquals(1, td.getParameters().size());

            Map<String, Object> schema = td.toSchema();
            assertEquals("search", schema.get("name"));
            assertEquals("Search the web", schema.get("description"));
            assertNotNull(schema.get("input_schema"));
        }

        @Test
        @DisplayName("null parameters 默认为空 map")
        void nullParametersDefault() {
            Skill.ToolDefinition td = new Skill.ToolDefinition("test", "desc", null);
            assertNotNull(td.getParameters());
            assertTrue(td.getParameters().isEmpty());
        }

        @Test
        @DisplayName("getParameters 返回防御性副本")
        void parametersDefensiveCopy() {
            Map<String, Object> params = Map.of("key", "value");
            Skill.ToolDefinition td = new Skill.ToolDefinition("test", "desc", params);

            Map<String, Object> copy = td.getParameters();
            copy.put("new", "entry");
            assertEquals(1, td.getParameters().size()); // Original unchanged
        }
    }

    @Nested
    @DisplayName("getter 防御性副本")
    class DefensiveCopyTests {

        @Test
        @DisplayName("getTools 返回副本")
        void toolsDefensiveCopy() {
            Skill skill = Skill.builder()
                .name("test")
                .addTool("t1", "desc", Map.of())
                .build();

            skill.getTools().clear();
            assertEquals(1, skill.getTools().size());
        }

        @Test
        @DisplayName("getTriggers 返回副本")
        void triggersDefensiveCopy() {
            Skill skill = Skill.builder()
                .name("test")
                .addTrigger("trig")
                .build();

            skill.getTriggers().clear();
            assertEquals(1, skill.getTriggers().size());
        }

        @Test
        @DisplayName("getMetadata 返回副本")
        void metadataDefensiveCopy() {
            Skill skill = Skill.builder()
                .name("test")
                .metadata("k", "v")
                .build();

            skill.getMetadata().clear();
            assertEquals(1, skill.getMetadata().size());
        }
    }

    @Test
    @DisplayName("toString 包含名称、工具数和优先级")
    void toStringFormat() {
        Skill skill = Skill.builder()
            .name("weather")
            .addTool("get_weather", "Get weather", Map.of())
            .priority(10)
            .build();

        String str = skill.toString();
        assertTrue(str.contains("weather"));
        assertTrue(str.contains("1")); // 1 tool
        assertTrue(str.contains("10")); // priority
    }
}
