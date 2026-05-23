package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryType - 三层记忆类型")
class MemoryTypeTest {

    @Test
    @DisplayName("包含三种类型：EPHEMERAL, DURABLE, SESSION")
    void threeTypes() {
        MemoryType[] types = MemoryType.values();
        assertEquals(3, types.length);
    }

    @Test
    @DisplayName("valueOf 匹配")
    void valueOfWorks() {
        assertEquals(MemoryType.EPHEMERAL, MemoryType.valueOf("EPHEMERAL"));
        assertEquals(MemoryType.DURABLE, MemoryType.valueOf("DURABLE"));
        assertEquals(MemoryType.SESSION, MemoryType.valueOf("SESSION"));
    }

    @Test
    @DisplayName("无效值抛 IllegalArgumentException")
    void invalidValueThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                MemoryType.valueOf("INVALID"));
    }
}
