package com.lightweightai.user.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SoulUser — 用户领域模型")
class SoulUserTest {

    @Nested
    @DisplayName("默认构造器")
    class DefaultConstructorTests {

        @Test
        @DisplayName("role 默认为 USER")
        void shouldDefaultRoleToUser() {
            SoulUser user = new SoulUser();
            assertEquals("USER", user.getRole());
        }

        @Test
        @DisplayName("enabled 默认为 true")
        void shouldDefaultEnabledToTrue() {
            SoulUser user = new SoulUser();
            assertTrue(user.isEnabled());
        }
    }

    @Nested
    @DisplayName("8参数构造器")
    class EightArgConstructorTests {

        @Test
        @DisplayName("所有字段正确赋值")
        void shouldSetAllFields() {
            long now = System.currentTimeMillis();
            SoulUser user = new SoulUser("u1", "alice", "hash", "wx_1",
                    "Alice", "VIP", now, now + 100);

            assertEquals("u1", user.getId());
            assertEquals("alice", user.getUsername());
            assertEquals("hash", user.getPasswordHash());
            assertEquals("wx_1", user.getOpenid());
            assertEquals("Alice", user.getNickname());
            assertEquals("VIP", user.getMemberLevel());
            assertEquals(now, user.getCreatedAt());
            assertEquals(now + 100, user.getLastActive());
        }

        @Test
        @DisplayName("8参数构造器 role 默认为 USER")
        void shouldDefaultRoleToUser() {
            SoulUser user = new SoulUser("u1", null, null, null,
                    "Test", "FREE", 0, 0);
            assertEquals("USER", user.getRole());
        }

        @Test
        @DisplayName("8参数构造器 enabled 默认为 true")
        void shouldDefaultEnabledToTrue() {
            SoulUser user = new SoulUser("u1", null, null, null,
                    "Test", "FREE", 0, 0);
            assertTrue(user.isEnabled());
        }
    }

    @Nested
    @DisplayName("10参数构造器")
    class TenArgConstructorTests {

        @Test
        @DisplayName("显式设置 role 和 enabled")
        void shouldSetRoleAndEnabled() {
            SoulUser user = new SoulUser("u1", "admin", "hash", null,
                    "Admin", "PREMIUM", "ADMIN", false, 100, 200);

            assertEquals("ADMIN", user.getRole());
            assertFalse(user.isEnabled());
        }

        @Test
        @DisplayName("所有10个字段正确赋值")
        void shouldSetAllTenFields() {
            SoulUser user = new SoulUser("u1", "bob", "pw", "wx_2",
                    "Bob", "VIP", "MODERATOR", true, 1000, 2000);

            assertEquals("u1", user.getId());
            assertEquals("bob", user.getUsername());
            assertEquals("pw", user.getPasswordHash());
            assertEquals("wx_2", user.getOpenid());
            assertEquals("Bob", user.getNickname());
            assertEquals("VIP", user.getMemberLevel());
            assertEquals(1000, user.getCreatedAt());
            assertEquals(2000, user.getLastActive());
            assertEquals("MODERATOR", user.getRole());
            assertTrue(user.isEnabled());
        }
    }

    @Nested
    @DisplayName("Setter 修改")
    class SetterTests {

        @Test
        @DisplayName("setRole 修改角色")
        void shouldUpdateRole() {
            SoulUser user = new SoulUser();
            user.setRole("ADMIN");
            assertEquals("ADMIN", user.getRole());
        }

        @Test
        @DisplayName("setEnabled 修改启用状态")
        void shouldUpdateEnabled() {
            SoulUser user = new SoulUser();
            user.setEnabled(false);
            assertFalse(user.isEnabled());
        }

        @Test
        @DisplayName("可以设置空字段")
        void shouldAcceptNullFields() {
            SoulUser user = new SoulUser();
            user.setUsername(null);
            user.setNickname(null);
            user.setOpenid(null);
            assertNull(user.getUsername());
            assertNull(user.getNickname());
            assertNull(user.getOpenid());
        }
    }
}
