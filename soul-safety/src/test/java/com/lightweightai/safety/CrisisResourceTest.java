package com.lightweightai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrisisResource")
class CrisisResourceTest {

    @Test
    void shouldCreateWithAllFields() {
        CrisisResource resource = new CrisisResource("Test Hotline", "123-456", "24/7 support");

        assertEquals("Test Hotline", resource.name());
        assertEquals("123-456", resource.phone());
        assertEquals("24/7 support", resource.description());
    }

    @Test
    void shouldHaveDefaultResources() {
        assertNotNull(CrisisResource.DEFAULTS);
        assertFalse(CrisisResource.DEFAULTS.isEmpty());
    }

    @Test
    void shouldHaveAtLeastThreeDefaults() {
        assertTrue(CrisisResource.DEFAULTS.size() >= 3,
                "Should have at least 3 crisis resources");
    }

    @Test
    void shouldHavePhoneNumbersForAllDefaults() {
        for (CrisisResource resource : CrisisResource.DEFAULTS) {
            assertNotNull(resource.phone(), "Phone should not be null for " + resource.name());
            assertFalse(resource.phone().isBlank(), "Phone should not be blank for " + resource.name());
        }
    }

    @Test
    void shouldHaveDescriptionsForAllDefaults() {
        for (CrisisResource resource : CrisisResource.DEFAULTS) {
            assertNotNull(resource.description());
            assertFalse(resource.description().isBlank());
        }
    }
}
