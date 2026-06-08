package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryType - 记忆类型枚举")
class MemoryTypeTest {

    @Test
    @DisplayName("包含所有预期的枚举值")
    void allEnumValues() {
        MemoryType[] values = MemoryType.values();
        assertEquals(3, values.length);
        assertNotNull(MemoryType.valueOf("EPHEMERAL"));
        assertNotNull(MemoryType.valueOf("DURABLE"));
        assertNotNull(MemoryType.valueOf("SESSION"));
    }
}
