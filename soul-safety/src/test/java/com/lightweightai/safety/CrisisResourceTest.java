package com.lightweightai.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrisisResource - 危机资源")
class CrisisResourceTest {

    @Test
    @DisplayName("DEFAULTS 包含预定义危机热线")
    void shouldHaveDefaults() {
        assertFalse(CrisisResource.DEFAULTS.isEmpty());
        assertTrue(CrisisResource.DEFAULTS.size() >= 3);
    }

    @Test
    @DisplayName("每条默认资源都有完整字段")
    void shouldHaveCompleteFields() {
        for (CrisisResource resource : CrisisResource.DEFAULTS) {
            assertNotNull(resource.name(), "name should not be null");
            assertNotNull(resource.phone(), "phone should not be null");
            assertNotNull(resource.description(), "description should not be null");
            assertFalse(resource.name().isBlank(), "name should not be blank");
            assertFalse(resource.phone().isBlank(), "phone should not be blank");
        }
    }

    @Test
    @DisplayName("record 属性访问正确")
    void shouldAccessRecordProperties() {
        CrisisResource resource = new CrisisResource("测试热线", "400-000-0000", "测试描述");

        assertEquals("测试热线", resource.name());
        assertEquals("400-000-0000", resource.phone());
        assertEquals("测试描述", resource.description());
    }

    @Test
    @DisplayName("record equals 和 hashCode")
    void shouldImplementEqualsAndHashCode() {
        CrisisResource a = new CrisisResource("热线A", "111", "描述");
        CrisisResource b = new CrisisResource("热线A", "111", "描述");
        CrisisResource c = new CrisisResource("热线B", "222", "描述");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    @DisplayName("DEFAULTS 列表不可变")
    void shouldBeImmutable() {
        assertThrows(UnsupportedOperationException.class,
            () -> CrisisResource.DEFAULTS.add(new CrisisResource("x", "y", "z")));
    }

    @Test
    @DisplayName("包含北京心理危机热线")
    void shouldContainBeijingCrisisHotline() {
        boolean found = CrisisResource.DEFAULTS.stream()
            .anyMatch(r -> r.phone().contains("010-82951332"));
        assertTrue(found, "Should contain Beijing crisis hotline");
    }
}
