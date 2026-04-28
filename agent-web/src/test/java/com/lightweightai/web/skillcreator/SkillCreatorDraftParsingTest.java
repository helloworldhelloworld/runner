package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillCreatorService 的 draft 解析逻辑测试
 *
 * 直接测试 <skill_draft> 正则解析和 JSON 反序列化，
 * 不依赖 LLMProvider 和 AgentLoop。
 */
@DisplayName("SkillCreatorService - Draft 解析逻辑")
class SkillCreatorDraftParsingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern DRAFT_PATTERN = Pattern.compile(
            "<skill_draft>\\s*(\\{.*?})\\s*</skill_draft>",
            Pattern.DOTALL
    );

    private SkillDraft parseDraft(String fullText) {
        Matcher matcher = DRAFT_PATTERN.matcher(fullText);
        if (matcher.find()) {
            String json = matcher.group(1);
            try {
                return MAPPER.readValue(json, SkillDraft.class);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse draft JSON", e);
            }
        }
        return null;
    }

    @Nested
    @DisplayName("正常解析")
    class NormalParsingTests {

        @Test
        @DisplayName("从 LLM 输出中提取完整 skill_draft JSON")
        void parsesCompleteDraft() {
            String llmOutput = """
                好的，根据你的描述，我已经为你创建了以下 Skill 草稿：

                <skill_draft>
                {
                  "name": "weather-query",
                  "description": "天气查询技能",
                  "version": "1.0.0",
                  "category": "工具类",
                  "systemPrompt": "# 天气查询\\n\\n查询指定城市的天气信息。",
                  "toolNames": ["WeatherAPI", "LocationResolver"],
                  "triggers": ["天气", "温度", "下雨"],
                  "priority": 10
                }
                </skill_draft>

                你觉得这个定义如何？
                """;

            SkillDraft draft = parseDraft(llmOutput);

            assertNotNull(draft);
            assertEquals("weather-query", draft.getName());
            assertEquals("天气查询技能", draft.getDescription());
            assertEquals("1.0.0", draft.getVersion());
            assertEquals("工具类", draft.getCategory());
            assertTrue(draft.getSystemPrompt().contains("天气查询"));
            assertEquals(2, draft.getToolNames().size());
            assertTrue(draft.getToolNames().contains("WeatherAPI"));
            assertEquals(3, draft.getTriggers().size());
            assertEquals(10, draft.getPriority());
        }

        @Test
        @DisplayName("最小 draft（只有必填字段）")
        void parsesMinimalDraft() {
            String llmOutput = """
                <skill_draft>
                {"name": "hello", "description": "简单问候", "systemPrompt": "你好世界"}
                </skill_draft>
                """;

            SkillDraft draft = parseDraft(llmOutput);

            assertNotNull(draft);
            assertEquals("hello", draft.getName());
            assertTrue(draft.isValid());
        }

        @Test
        @DisplayName("draft 标签前后有大量文本时仍能解析")
        void parsesWithSurroundingText() {
            StringBuilder sb = new StringBuilder();
            sb.append("x".repeat(5000));
            sb.append("<skill_draft>{\"name\":\"test\",\"description\":\"desc\",\"systemPrompt\":\"sp\"}</skill_draft>");
            sb.append("y".repeat(5000));

            SkillDraft draft = parseDraft(sb.toString());

            assertNotNull(draft);
            assertEquals("test", draft.getName());
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCaseTests {

        @Test
        @DisplayName("无 skill_draft 标签时返回 null")
        void returnsNullWhenNoDraftTag() {
            String llmOutput = "我们来讨论一下这个 Skill 的功能，你希望它能做什么？";

            SkillDraft draft = parseDraft(llmOutput);

            assertNull(draft);
        }

        @Test
        @DisplayName("空文本返回 null")
        void returnsNullForEmptyText() {
            assertNull(parseDraft(""));
        }

        @Test
        @DisplayName("JSON 中包含换行符正确解析（DOTALL 模式）")
        void parsesMultilineJson() {
            String llmOutput = """
                <skill_draft>
                {
                  "name": "multiline",
                  "description": "多行描述\\n第二行",
                  "systemPrompt": "# 标题\\n\\n内容\\n\\n## 子标题\\n\\n更多内容"
                }
                </skill_draft>
                """;

            SkillDraft draft = parseDraft(llmOutput);

            assertNotNull(draft);
            assertEquals("multiline", draft.getName());
            assertTrue(draft.getSystemPrompt().contains("# 标题"));
        }

        @Test
        @DisplayName("多个 skill_draft 标签只取第一个")
        void takesFirstDraftOnly() {
            String llmOutput = """
                <skill_draft>
                {"name": "first", "description": "d", "systemPrompt": "s"}
                </skill_draft>
                <skill_draft>
                {"name": "second", "description": "d", "systemPrompt": "s"}
                </skill_draft>
                """;

            SkillDraft draft = parseDraft(llmOutput);

            assertNotNull(draft);
            assertEquals("first", draft.getName());
        }

        @Test
        @DisplayName("未知字段被忽略（@JsonIgnoreProperties）")
        void unknownFieldsIgnored() {
            String llmOutput = """
                <skill_draft>
                {
                  "name": "test",
                  "description": "desc",
                  "systemPrompt": "sp",
                  "unknownField": "ignored",
                  "anotherUnknown": 42
                }
                </skill_draft>
                """;

            SkillDraft draft = parseDraft(llmOutput);

            assertNotNull(draft);
            assertEquals("test", draft.getName());
        }
    }

    @Nested
    @DisplayName("isValid 守卫")
    class ValidationTests {

        @Test
        @DisplayName("完整 draft 通过 isValid")
        void completeDraftIsValid() {
            SkillDraft draft = new SkillDraft();
            draft.setName("test");
            draft.setDescription("description");
            draft.setSystemPrompt("prompt");

            assertTrue(draft.isValid());
        }

        @Test
        @DisplayName("缺少 name 不通过 isValid")
        void missingNameInvalid() {
            SkillDraft draft = new SkillDraft();
            draft.setDescription("description");
            draft.setSystemPrompt("prompt");

            assertFalse(draft.isValid());
        }

        @Test
        @DisplayName("缺少 description 不通过 isValid")
        void missingDescriptionInvalid() {
            SkillDraft draft = new SkillDraft();
            draft.setName("test");
            draft.setSystemPrompt("prompt");

            assertFalse(draft.isValid());
        }

        @Test
        @DisplayName("缺少 systemPrompt 不通过 isValid")
        void missingSystemPromptInvalid() {
            SkillDraft draft = new SkillDraft();
            draft.setName("test");
            draft.setDescription("description");

            assertFalse(draft.isValid());
        }

        @Test
        @DisplayName("空白字段不通过 isValid")
        void blankFieldsInvalid() {
            SkillDraft draft = new SkillDraft();
            draft.setName("  ");
            draft.setDescription("description");
            draft.setSystemPrompt("prompt");

            assertFalse(draft.isValid());
        }
    }
}
