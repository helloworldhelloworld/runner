package com.lightweightai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CrisisResource - 危机资源")
class CrisisResourceTest {

    @Test
    @DisplayName("记录属性正确")
    void recordProperties() {
        CrisisResource res = new CrisisResource("Test Center", "123-456", "24h hotline");

        assertEquals("Test Center", res.name());
        assertEquals("123-456", res.phone());
        assertEquals("24h hotline", res.description());
    }

    @Test
    @DisplayName("默认资源列表非空")
    void defaultsNotEmpty() {
        assertFalse(CrisisResource.DEFAULTS.isEmpty());
        assertTrue(CrisisResource.DEFAULTS.size() >= 3);
    }

    @Test
    @DisplayName("默认资源包含北京心理危机中心")
    void defaultsContainBeijingCenter() {
        boolean found = CrisisResource.DEFAULTS.stream()
                .anyMatch(r -> r.name().contains("北京心理危机"));
        assertTrue(found);
    }

    @Test
    @DisplayName("默认资源全部有电话号码")
    void defaultsAllHavePhone() {
        for (CrisisResource r : CrisisResource.DEFAULTS) {
            assertNotNull(r.phone());
            assertFalse(r.phone().isBlank());
        }
    }
}
