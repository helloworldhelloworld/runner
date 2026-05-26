package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SkillTestCaseTest {

    @Test
    void defaultListsNotNull() {
        SkillTestCase tc = new SkillTestCase();
        assertNotNull(tc.getUserClarifications());
        assertNotNull(tc.getAssertions());
    }

    @Test
    void allFieldsRoundTrip() {
        SkillTestCase tc = new SkillTestCase();
        tc.setId("tc-001"); tc.setSkillId("skill-1"); tc.setCategory("happy_path");
        tc.setDescription("Test"); tc.setInput("Hello"); tc.setMemory("user likes cats");
        tc.setAssertions(List.of("a1", "a2"));
        tc.setMockResponses(Map.of("tool1", "mock"));
        assertEquals("tc-001", tc.getId());
        assertEquals(2, tc.getAssertions().size());
    }

    @Test
    void nullClarificationsToEmpty() {
        SkillTestCase tc = new SkillTestCase();
        tc.setUserClarifications(null);
        assertNotNull(tc.getUserClarifications());
    }

    @Test
    void nullAssertionsToEmpty() {
        SkillTestCase tc = new SkillTestCase();
        tc.setAssertions(null);
        assertNotNull(tc.getAssertions());
    }
}
