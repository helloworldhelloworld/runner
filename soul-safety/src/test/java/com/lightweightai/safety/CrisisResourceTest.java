package com.lightweightai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrisisResource - 危机资源")
class CrisisResourceTest {

    @Test
    @DisplayName("record 字段正确填充")
    void recordFields() {
        CrisisResource resource = new CrisisResource("测试热线", "123-456", "24小时服务");

        assertEquals("测试热线", resource.name());
        assertEquals("123-456", resource.phone());
        assertEquals("24小时服务", resource.description());
    }

    @Test
    @DisplayName("DEFAULTS 包含预置的中国危机热线")
    void defaultsNotEmpty() {
        assertNotNull(CrisisResource.DEFAULTS);
        assertFalse(CrisisResource.DEFAULTS.isEmpty());
        assertTrue(CrisisResource.DEFAULTS.size() >= 3);
    }

    @Test
    @DisplayName("DEFAULTS 中每个资源都有 name、phone、description")
    void defaultsAllFieldsPopulated() {
        for (CrisisResource resource : CrisisResource.DEFAULTS) {
            assertNotNull(resource.name(), "name should not be null");
            assertFalse(resource.name().isBlank(), "name should not be blank");
            assertNotNull(resource.phone(), "phone should not be null");
            assertFalse(resource.phone().isBlank(), "phone should not be blank");
            assertNotNull(resource.description(), "description should not be null");
            assertFalse(resource.description().isBlank(), "description should not be blank");
        }
    }

    @Test
    @DisplayName("DEFAULTS 包含北京心理危机研究与干预中心")
    void defaultsContainsBeijingCrisisCenter() {
        boolean found = CrisisResource.DEFAULTS.stream()
                .anyMatch(r -> r.name().contains("北京心理危机"));
        assertTrue(found, "Should contain Beijing crisis center");
    }

    @Test
    @DisplayName("DEFAULTS 是不可变列表")
    void defaultsImmutable() {
        assertThrows(UnsupportedOperationException.class, () ->
                CrisisResource.DEFAULTS.add(new CrisisResource("fake", "000", "test")));
    }
}
