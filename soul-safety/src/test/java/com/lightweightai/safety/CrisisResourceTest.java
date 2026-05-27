package com.lightweightai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrisisResource — record and defaults validation")
class CrisisResourceTest {

    @Test
    @DisplayName("record fields accessible via accessors")
    void recordFields() {
        CrisisResource r = new CrisisResource("Test Hotline", "400-123-4567", "24h support");

        assertEquals("Test Hotline", r.name());
        assertEquals("400-123-4567", r.phone());
        assertEquals("24h support", r.description());
    }

    @Test
    @DisplayName("DEFAULTS contains at least 3 crisis resources")
    void defaultsNotEmpty() {
        assertNotNull(CrisisResource.DEFAULTS);
        assertTrue(CrisisResource.DEFAULTS.size() >= 3,
                "should have at least 3 default crisis resources");
    }

    @Test
    @DisplayName("all defaults have non-blank name, phone, and description")
    void allDefaultsHaveRequiredFields() {
        for (CrisisResource r : CrisisResource.DEFAULTS) {
            assertNotNull(r.name(), "name should not be null");
            assertFalse(r.name().isBlank(), "name should not be blank");
            assertNotNull(r.phone(), "phone should not be null");
            assertFalse(r.phone().isBlank(), "phone should not be blank");
            assertNotNull(r.description(), "description should not be null");
            assertFalse(r.description().isBlank(), "description should not be blank");
        }
    }

    @Test
    @DisplayName("defaults list is immutable")
    void defaultsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> CrisisResource.DEFAULTS.add(
                        new CrisisResource("Fake", "000", "test")));
    }

    @Test
    @DisplayName("record equality based on field values")
    void recordEquality() {
        CrisisResource a = new CrisisResource("A", "111", "d");
        CrisisResource b = new CrisisResource("A", "111", "d");
        CrisisResource c = new CrisisResource("B", "111", "d");

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
