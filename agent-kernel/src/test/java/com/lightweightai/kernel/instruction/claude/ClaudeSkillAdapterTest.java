package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.skill.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeSkillAdapter — Skill → InstructionPackage 适配")
class ClaudeSkillAdapterTest {

    private Skill skill;
    private ClaudeSkillAdapter adapter;

    @BeforeEach
    void setUp() {
        skill = Skill.builder()
                .name("code-review")
                .description("Review code changes")
                .instructions("Review all diffs for bugs")
                .addMetadata("version", "2.0")
                .addResource("template.md", "# Review\n")
                .build();
        adapter = new ClaudeSkillAdapter(skill);
    }

    @Test
    @DisplayName("getName 委托给底层 Skill")
    void nameFromSkill() {
        assertEquals("code-review", adapter.getName());
    }

    @Test
    @DisplayName("getDescription 委托给底层 Skill")
    void descriptionFromSkill() {
        assertEquals("Review code changes", adapter.getDescription());
    }

    @Test
    @DisplayName("getInstructions 委托给底层 Skill")
    void instructionsFromSkill() {
        assertEquals("Review all diffs for bugs", adapter.getInstructions());
    }

    @Test
    @DisplayName("getMetadata 包含 Skill 的 metadata")
    void metadataFromSkill() {
        assertEquals("2.0", adapter.getMetadata().get("version"));
    }

    @Test
    @DisplayName("hasResource 检查资源存在性")
    void hasResourceDelegates() {
        assertTrue(adapter.hasResource("template.md"));
        assertFalse(adapter.hasResource("nonexistent.txt"));
    }

    @Test
    @DisplayName("getResourceAsString 返回文本内容")
    void getResourceAsStringDelegates() {
        assertEquals("# Review\n", adapter.getResourceAsString("template.md"));
    }

    @Test
    @DisplayName("getFormat 返回 anthropic-skill")
    void formatIsAnthropicSkill() {
        assertEquals("anthropic-skill", adapter.getFormat());
    }

    @Test
    @DisplayName("toPromptContext 委托给 Skill 并包含 skill name")
    void promptContextFromSkill() {
        String context = adapter.toPromptContext();
        assertNotNull(context);
        assertTrue(context.contains("code-review"));
    }

    @Test
    @DisplayName("getSkill 返回原始 Skill 引用")
    void getSkillReturnsOriginal() {
        assertSame(skill, adapter.getSkill());
    }

    @Test
    @DisplayName("from() 静态工厂方法创建适配器")
    void staticFactoryMethod() {
        ClaudeSkillAdapter a = ClaudeSkillAdapter.from(skill);
        assertNotNull(a);
        assertEquals("code-review", a.getName());
        assertSame(skill, a.getSkill());
    }
}
