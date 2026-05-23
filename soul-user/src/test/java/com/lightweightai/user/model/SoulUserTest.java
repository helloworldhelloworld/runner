package com.lightweightai.user.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SoulUser - 用户模型")
class SoulUserTest {

    @Test
    @DisplayName("无参构造默认 role=USER, enabled=true")
    void defaultConstruction() {
        SoulUser user = new SoulUser();

        assertEquals("USER", user.getRole());
        assertTrue(user.isEnabled());
        assertNull(user.getId());
        assertNull(user.getUsername());
    }

    @Test
    @DisplayName("8参构造设置基本字段，默认 role=USER, enabled=true")
    void eightArgConstruction() {
        SoulUser user = new SoulUser("u1", "alice", "hash123", "wx-openid",
                "小红", "gold", 1000L, 2000L);

        assertEquals("u1", user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("hash123", user.getPasswordHash());
        assertEquals("wx-openid", user.getOpenid());
        assertEquals("小红", user.getNickname());
        assertEquals("gold", user.getMemberLevel());
        assertEquals("USER", user.getRole());
        assertTrue(user.isEnabled());
        assertEquals(1000L, user.getCreatedAt());
        assertEquals(2000L, user.getLastActive());
    }

    @Test
    @DisplayName("10参构造设置所有字段包括 role 和 enabled")
    void fullConstruction() {
        SoulUser user = new SoulUser("u2", "bob", "hash", "openid", "小明",
                "silver", "ADMIN", false, 3000L, 4000L);

        assertEquals("ADMIN", user.getRole());
        assertFalse(user.isEnabled());
    }

    @Test
    @DisplayName("10参构造 role 为 null 时默认 USER")
    void nullRoleDefaultsToUser() {
        SoulUser user = new SoulUser("u3", "carol", null, null, null,
                null, null, true, 0L, 0L);

        assertEquals("USER", user.getRole());
    }

    @Test
    @DisplayName("setter 可修改所有字段")
    void setters() {
        SoulUser user = new SoulUser();
        user.setId("u4");
        user.setUsername("dave");
        user.setPasswordHash("newhash");
        user.setOpenid("new-openid");
        user.setNickname("大卫");
        user.setMemberLevel("platinum");
        user.setRole("ADMIN");
        user.setEnabled(false);
        user.setCreatedAt(5000L);
        user.setLastActive(6000L);

        assertEquals("u4", user.getId());
        assertEquals("dave", user.getUsername());
        assertEquals("newhash", user.getPasswordHash());
        assertEquals("new-openid", user.getOpenid());
        assertEquals("大卫", user.getNickname());
        assertEquals("platinum", user.getMemberLevel());
        assertEquals("ADMIN", user.getRole());
        assertFalse(user.isEnabled());
        assertEquals(5000L, user.getCreatedAt());
        assertEquals(6000L, user.getLastActive());
    }
}
