package com.lightweightai.web.skillcreator;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class SkillVersionTest {

    @Test
    void defaultConstructorCreatesEmptyObject() {
        SkillVersion v = new SkillVersion();
        assertNull(v.getId());
        assertNull(v.getStatus());
    }

    @Test
    void allFieldsRoundTrip() {
        Instant now = Instant.now();
        SkillVersion v = new SkillVersion();
        v.setId("v-001"); v.setSkillId("skill-abc"); v.setVersionTag("1.0.0");
        v.setSnapshotJson("{\"name\":\"test\"}"); v.setTestRunId("run-xyz");
        v.setStatus("active"); v.setCreatedAt(now); v.setActivatedAt(now.plusSeconds(60));

        assertEquals("v-001", v.getId());
        assertEquals("skill-abc", v.getSkillId());
        assertEquals("1.0.0", v.getVersionTag());
        assertEquals("active", v.getStatus());
        assertEquals(now, v.getCreatedAt());
    }
}
