package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class TestRunTest {

    @Test
    void twoArgConstructor() {
        TestRun run = new TestRun("run-001", "skill-abc");
        assertEquals("run-001", run.getId());
        assertEquals("skill-abc", run.getSkillId());
        assertNotNull(run.getStartedAt());
        assertEquals("running", run.getStatus());
    }

    @Test
    void defaultConstructor() {
        assertNull(new TestRun().getStatus());
    }

    @Test
    void passRateCalculation() {
        TestRun run = new TestRun("r1", "s1");
        run.setTotal(10); run.setPassed(7);
        assertEquals(0.7, run.getPassRate(), 0.001);
    }

    @Test
    void passRateZeroTotal() {
        TestRun run = new TestRun("r1", "s1");
        run.setTotal(0);
        assertEquals(0.0, run.getPassRate());
    }

    @Test
    void allFieldsRoundTrip() {
        Instant now = Instant.now();
        TestRun run = new TestRun();
        run.setId("run-xyz"); run.setSkillId("s-123"); run.setStartedAt(now);
        run.setCompletedAt(now.plusSeconds(30)); run.setTotal(5); run.setPassed(4);
        run.setFailed(1); run.setStatus("completed");
        assertEquals("run-xyz", run.getId());
        assertEquals(5, run.getTotal());
        assertEquals("completed", run.getStatus());
    }
}
