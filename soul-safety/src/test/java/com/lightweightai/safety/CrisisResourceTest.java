package com.lightweightai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrisisResource - 危机资源热线")
class CrisisResourceTest {

    @Test
    @DisplayName("record 字段正确")
    void recordFieldsAccessible() {
        CrisisResource r = new CrisisResource("Test Hotline", "123-456", "24h support");

        assertEquals("Test Hotline", r.name());
        assertEquals("123-456", r.phone());
        assertEquals("24h support", r.description());
    }

    @Test
    @DisplayName("DEFAULTS 包含 5 条资源")
    void defaultsContainsFiveResources() {
        assertEquals(5, CrisisResource.DEFAULTS.size());
    }

    @Test
    @DisplayName("每条资源字段不为空")
    void allDefaultsHaveNonEmptyFields() {
        for (CrisisResource r : CrisisResource.DEFAULTS) {
            assertNotNull(r.name(), "name should not be null");
            assertNotNull(r.phone(), "phone should not be null");
            assertNotNull(r.description(), "description should not be null");

            assertFalse(r.name().isBlank(), "name should not be blank: " + r);
            assertFalse(r.phone().isBlank(), "phone should not be blank: " + r);
            assertFalse(r.description().isBlank(), "description should not be blank: " + r);
        }
    }

    @Test
    @DisplayName("电话号码格式合法（数字和横线）")
    void phoneNumbersHaveValidFormat() {
        for (CrisisResource r : CrisisResource.DEFAULTS) {
            assertTrue(r.phone().matches("[0-9\\-]+"),
                "phone should contain only digits and dashes: " + r.phone());
        }
    }

    @Test
    @DisplayName("电话号码无重复（排除已知共用号码）")
    void phoneNumbersAreDistinctExceptKnownDuplicates() {
        List<String> phones = CrisisResource.DEFAULTS.stream()
            .map(CrisisResource::phone)
            .toList();
        // At least 4 unique phone numbers (two hotlines may share 400-161-9995)
        Set<String> unique = new HashSet<>(phones);
        assertTrue(unique.size() >= 4,
            "Expected at least 4 unique phone numbers, got " + unique.size());
    }

    @Test
    @DisplayName("DEFAULTS 列表不可变")
    void defaultsListIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
            () -> CrisisResource.DEFAULTS.add(new CrisisResource("X", "0", "Y")));
    }

    @Test
    @DisplayName("包含 24 小时服务")
    void containsTwentyFourHourServices() {
        boolean has24h = CrisisResource.DEFAULTS.stream()
            .anyMatch(r -> r.description().contains("24"));
        assertTrue(has24h, "Should contain at least one 24-hour service");
    }

    @Test
    @DisplayName("record equals 和 hashCode")
    void equalsAndHashCode() {
        CrisisResource a = new CrisisResource("A", "1", "d");
        CrisisResource b = new CrisisResource("A", "1", "d");
        CrisisResource c = new CrisisResource("B", "2", "d");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }
}
