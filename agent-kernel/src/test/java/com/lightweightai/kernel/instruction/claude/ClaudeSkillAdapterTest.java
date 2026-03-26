package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.skill.Skill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClaudeSkillAdapter - Skill 到 InstructionPackage 适配器")
class ClaudeSkillAdapterTest {

    private Skill skill;
    private ClaudeSkillAdapter adapter;

    @BeforeEach
    void setUp() {
        skill = Skill.builder()
                .name("test-skill")
                .description("A test skill")
                .instructions("Follow these steps...")
                .addMetadata("version", "1.0")
                .addResource("template.md", "Hello {{name}}")
                .build();

        adapter = ClaudeSkillAdapter.from(skill);
    }

    @Test
    @DisplayName("from() 静态工厂方法创建适配器")
    void shouldCreateAdapterFromSkill() {
        assertNotNull(adapter);
        assertSame(skill, adapter.getSkill());
    }

    @Test
    @DisplayName("构造函数创建适配器")
    void shouldCreateAdapterViaConstructor() {
        ClaudeSkillAdapter a = new ClaudeSkillAdapter(skill);
        assertSame(skill, a.getSkill());
    }

    @Test
    @DisplayName("委托 getName() 到 Skill")
    void shouldDelegateGetName() {
        assertEquals("test-skill", adapter.getName());
    }

    @Test
    @DisplayName("委托 getDescription() 到 Skill")
    void shouldDelegateGetDescription() {
        assertEquals("A test skill", adapter.getDescription());
    }

    @Test
    @DisplayName("委托 getInstructions() 到 Skill")
    void shouldDelegateGetInstructions() {
        assertEquals("Follow these steps...", adapter.getInstructions());
    }

    @Test
    @DisplayName("委托 getMetadata() 到 Skill")
    void shouldDelegateGetMetadata() {
        assertEquals("1.0", adapter.getMetadata().get("version"));
    }

    @Test
    @DisplayName("委托 getResources() 到 Skill")
    void shouldDelegateGetResources() {
        assertFalse(adapter.getResources().isEmpty());
        assertTrue(adapter.hasResource("template.md"));
    }

    @Test
    @DisplayName("委托 getResource() 到 Skill")
    void shouldDelegateGetResource() {
        assertNotNull(adapter.getResource("template.md"));
    }

    @Test
    @DisplayName("委托 getResourceAsString() 到 Skill")
    void shouldDelegateGetResourceAsString() {
        assertEquals("Hello {{name}}", adapter.getResourceAsString("template.md"));
    }

    @Test
    @DisplayName("hasResource 不存在的资源返回 false")
    void shouldReturnFalseForMissingResource() {
        assertFalse(adapter.hasResource("nonexistent"));
    }

    @Test
    @DisplayName("getFormat() 返回 anthropic-skill")
    void shouldReturnAnthropicSkillFormat() {
        assertEquals("anthropic-skill", adapter.getFormat());
    }

    @Test
    @DisplayName("toPromptContext() 委托到 Skill")
    void shouldDelegateToPromptContext() {
        String context = adapter.toPromptContext();
        assertTrue(context.contains("test-skill"));
        assertTrue(context.contains("Follow these steps..."));
    }

    @Test
    @DisplayName("getSourcePath() 委托到 Skill.getSkillPath()")
    void shouldDelegateGetSourcePath() {
        // skill without path should return null
        assertNull(adapter.getSourcePath());
    }
}
