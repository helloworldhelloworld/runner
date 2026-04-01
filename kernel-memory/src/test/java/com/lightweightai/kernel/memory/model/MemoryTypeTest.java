package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryType - 记忆类型枚举")
class MemoryTypeTest {

    @Test
    @DisplayName("三种记忆类型存在")
    void allTypes() {
        MemoryType[] values = MemoryType.values();
        assertEquals(3, values.length);
        assertNotNull(MemoryType.valueOf("EPHEMERAL"));
        assertNotNull(MemoryType.valueOf("DURABLE"));
        assertNotNull(MemoryType.valueOf("SESSION"));
    }
}
