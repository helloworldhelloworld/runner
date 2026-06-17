package com.lightweightai.kernel.memory.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MemoryType — 三层记忆类型枚举")
class MemoryTypeTest {

    @Test
    @DisplayName("包含所有三种记忆类型")
    void allTypesPresent() {
        MemoryType[] types = MemoryType.values();
        assertEquals(3, types.length);
        assertNotNull(MemoryType.valueOf("EPHEMERAL"));
        assertNotNull(MemoryType.valueOf("DURABLE"));
        assertNotNull(MemoryType.valueOf("SESSION"));
    }

    @Test
    @DisplayName("valueOf 往返一致")
    void valueOfRoundTrip() {
        for (MemoryType type : MemoryType.values()) {
            assertEquals(type, MemoryType.valueOf(type.name()));
        }
    }
}
