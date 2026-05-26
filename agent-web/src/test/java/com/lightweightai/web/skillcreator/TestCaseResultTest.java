package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TestCaseResultTest {

    @Test
    void defaultConstructor() {
        TestCaseResult r = new TestCaseResult();
        assertNull(r.getId());
        assertFalse(r.isPassed());
        assertEquals(0, r.getDurationMs());
    }

    @Test
    void allFieldsRoundTrip() {
        TestCaseResult r = new TestCaseResult();
        r.setId("r-001"); r.setRunId("run-001"); r.setTestCaseId("tc-001");
        r.setPassed(true); r.setLlmOutput("Hello"); r.setDurationMs(150);
        r.setToolCalls(List.of(Map.of("name", "greet")));
        r.setAssertions(List.of(Map.of("text", "ok", "pass", true)));
        assertEquals("r-001", r.getId());
        assertTrue(r.isPassed());
        assertEquals(150, r.getDurationMs());
    }

    @Test
    void failedResultHasErrorMessage() {
        TestCaseResult r = new TestCaseResult();
        r.setPassed(false); r.setErrorMessage("LLM timeout");
        assertFalse(r.isPassed());
        assertEquals("LLM timeout", r.getErrorMessage());
    }
}
