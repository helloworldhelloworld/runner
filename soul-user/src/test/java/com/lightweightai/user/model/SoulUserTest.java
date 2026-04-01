package com.lightweightai.user.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SoulUser - 用户模型")
class SoulUserTest {

    @Test
    @DisplayName("无参构造默认值")
    void defaultConstruction() {
        SoulUser user = new SoulUser();
        assertEquals("USER", user.getRole());
        assertTrue(user.isEnabled());
        assertNull(user.getId());
        assertNull(user.getUsername());
    }

    @Test
    @DisplayName("8参数构造 - 默认 role=USER, enabled=true")
    void eightArgConstruction() {
        long now = System.currentTimeMillis();
        SoulUser user = new SoulUser("u1", "alice", "hash123", "openid1",
                "Alice", "PREMIUM", now, now);

        assertEquals("u1", user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("hash123", user.getPasswordHash());
        assertEquals("openid1", user.getOpenid());
        assertEquals("Alice", user.getNickname());
        assertEquals("PREMIUM", user.getMemberLevel());
        assertEquals("USER", user.getRole());
        assertTrue(user.isEnabled());
        assertEquals(now, user.getCreatedAt());
        assertEquals(now, user.getLastActive());
    }

    @Test
    @DisplayName("10参数构造 - 指定 role 和 enabled")
    void tenArgConstruction() {
        SoulUser user = new SoulUser("u1", "admin", "hash", "oid",
                "Admin", "VIP", "ADMIN", false, 100L, 200L);

        assertEquals("ADMIN", user.getRole());
        assertFalse(user.isEnabled());
    }

    @Test
    @DisplayName("10参数构造 - null role 默认为 USER")
    void nullRoleDefault() {
        SoulUser user = new SoulUser("u1", "bob", "hash", "oid",
                "Bob", "FREE", null, true, 100L, 200L);
        assertEquals("USER", user.getRole());
    }

    @Test
    @DisplayName("setter 正确更新字段")
    void setters() {
        SoulUser user = new SoulUser();
        user.setId("u2");
        user.setUsername("charlie");
        user.setPasswordHash("newhash");
        user.setOpenid("oid2");
        user.setNickname("Charlie");
        user.setMemberLevel("VIP");
        user.setRole("ADMIN");
        user.setEnabled(false);
        user.setCreatedAt(1000L);
        user.setLastActive(2000L);

        assertEquals("u2", user.getId());
        assertEquals("charlie", user.getUsername());
        assertEquals("newhash", user.getPasswordHash());
        assertEquals("oid2", user.getOpenid());
        assertEquals("Charlie", user.getNickname());
        assertEquals("VIP", user.getMemberLevel());
        assertEquals("ADMIN", user.getRole());
        assertFalse(user.isEnabled());
        assertEquals(1000L, user.getCreatedAt());
        assertEquals(2000L, user.getLastActive());
    }
}
