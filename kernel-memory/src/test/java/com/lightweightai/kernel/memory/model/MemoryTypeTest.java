package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryType - 记忆类型枚举")
class MemoryTypeTest {

    @Test
    @DisplayName("包含三种记忆类型")
    void threeTypes() {
        MemoryType[] values = MemoryType.values();
        assertEquals(3, values.length);
    }

    @Test
    @DisplayName("EPHEMERAL / DURABLE / SESSION 均可解析")
    void allValuesResolvable() {
        assertNotNull(MemoryType.valueOf("EPHEMERAL"));
        assertNotNull(MemoryType.valueOf("DURABLE"));
        assertNotNull(MemoryType.valueOf("SESSION"));
    }
}
