package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SkillTestCaseRepository 测试")
class SkillTestCaseRepositoryTest {

    private SkillTestCaseRepository repo;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        repo = new SkillTestCaseRepository(tempDir.resolve("test-cases.db").toString());
    }

    @Test
    @DisplayName("保存和查询测试用例")
    void saveAndFind() {
        SkillTestCase tc = new SkillTestCase();
        tc.setSkillId("skill-1");
        tc.setCategory("基础流程");
        tc.setDescription("基础导航");
        tc.setInput("导航去新街口");
        tc.setAssertions(List.of("先调用 GetPoi 再调用 Navigate"));

        SkillTestCase saved = repo.save(tc);
        assertNotNull(saved.getId());

        List<SkillTestCase> found = repo.findBySkillId("skill-1");
        assertEquals(1, found.size());
        assertEquals("基础导航", found.get(0).getDescription());
        assertEquals("导航去新街口", found.get(0).getInput());
        assertEquals(List.of("先调用 GetPoi 再调用 Navigate"), found.get(0).getAssertions());
    }

    @Test
    @DisplayName("保存含 memory 和 userClarifications 的用例")
    void saveWithMemoryAndClarifications() {
        SkillTestCase tc = new SkillTestCase();
        tc.setSkillId("skill-1");
        tc.setCategory("记忆消解");
        tc.setDescription("导航回家");
        tc.setInput("导航回家");
        tc.setMemory("家：中海·云熙");
        tc.setUserClarifications(List.of("选第一个"));
        tc.setAssertions(List.of("先调用 GetPoi 再调用 Navigate"));

        repo.save(tc);
        List<SkillTestCase> found = repo.findBySkillId("skill-1");
        assertEquals("家：中海·云熙", found.get(0).getMemory());
        assertEquals(List.of("选第一个"), found.get(0).getUserClarifications());
    }

    @Test
    @DisplayName("按 skillId 查询 — 多个用例")
    void findBySkillId() {
        for (int i = 0; i < 3; i++) {
            SkillTestCase tc = new SkillTestCase();
            tc.setSkillId("skill-1");
            tc.setCategory("cat-" + i);
            tc.setDescription("desc-" + i);
            tc.setInput("input-" + i);
            tc.setAssertions(List.of("assert-" + i));
            repo.save(tc);
        }
        // 另一个 skill
        SkillTestCase other = new SkillTestCase();
        other.setSkillId("skill-2");
        other.setDescription("other");
        other.setInput("other");
        other.setAssertions(List.of());
        repo.save(other);

        assertEquals(3, repo.findBySkillId("skill-1").size());
        assertEquals(1, repo.findBySkillId("skill-2").size());
    }

    @Test
    @DisplayName("删除测试用例")
    void delete() {
        SkillTestCase tc = new SkillTestCase();
        tc.setSkillId("skill-1");
        tc.setDescription("to delete");
        tc.setInput("input");
        tc.setAssertions(List.of());
        SkillTestCase saved = repo.save(tc);

        assertTrue(repo.delete(saved.getId()));
        assertEquals(0, repo.findBySkillId("skill-1").size());
    }

    @Test
    @DisplayName("批量保存")
    void saveAll() {
        List<SkillTestCase> cases = List.of(
            createCase("skill-1", "cat1", "desc1", "input1", List.of("a1")),
            createCase("skill-1", "cat2", "desc2", "input2", List.of("a2"))
        );
        repo.saveAll(cases);
        assertEquals(2, repo.findBySkillId("skill-1").size());
    }

    @Test
    @DisplayName("按 skillId 删除全部")
    void deleteBySkillId() {
        repo.save(createCase("skill-1", "c", "d1", "i1", List.of()));
        repo.save(createCase("skill-1", "c", "d2", "i2", List.of()));
        repo.save(createCase("skill-2", "c", "d3", "i3", List.of()));

        int deleted = repo.deleteBySkillId("skill-1");
        assertEquals(2, deleted);
        assertEquals(0, repo.findBySkillId("skill-1").size());
        assertEquals(1, repo.findBySkillId("skill-2").size());
    }

    private SkillTestCase createCase(String skillId, String category, String desc, String input, List<String> assertions) {
        SkillTestCase tc = new SkillTestCase();
        tc.setSkillId(skillId);
        tc.setCategory(category);
        tc.setDescription(desc);
        tc.setInput(input);
        tc.setAssertions(assertions);
        return tc;
    }
}
