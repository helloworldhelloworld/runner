package com.lightweightai.user.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SoulUser")
class SoulUserTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTests {

        @Test
        void shouldCreateWithDefaultConstructor() {
            SoulUser user = new SoulUser();

            assertEquals("USER", user.getRole());
            assertTrue(user.isEnabled());
        }

        @Test
        void shouldCreateWithBasicConstructor() {
            SoulUser user = new SoulUser("id-1", "alice", "hash", "wx123",
                    "Alice", "premium", 1000L, 2000L);

            assertEquals("id-1", user.getId());
            assertEquals("alice", user.getUsername());
            assertEquals("hash", user.getPasswordHash());
            assertEquals("wx123", user.getOpenid());
            assertEquals("Alice", user.getNickname());
            assertEquals("premium", user.getMemberLevel());
            assertEquals("USER", user.getRole());
            assertTrue(user.isEnabled());
            assertEquals(1000L, user.getCreatedAt());
            assertEquals(2000L, user.getLastActive());
        }

        @Test
        void shouldCreateWithFullConstructor() {
            SoulUser user = new SoulUser("id-1", "admin", "hash", null,
                    "Admin", "gold", "ADMIN", true, 1000L, 2000L);

            assertEquals("ADMIN", user.getRole());
            assertTrue(user.isEnabled());
        }

        @Test
        void shouldDefaultRoleToUserWhenNull() {
            SoulUser user = new SoulUser("id", "u", "h", null,
                    "n", "l", null, true, 0L, 0L);

            assertEquals("USER", user.getRole());
        }
    }

    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test
        void shouldUpdateAllFields() {
            SoulUser user = new SoulUser();

            user.setId("id-1");
            user.setUsername("bob");
            user.setPasswordHash("newhash");
            user.setOpenid("wx456");
            user.setNickname("Bob");
            user.setMemberLevel("gold");
            user.setRole("ADMIN");
            user.setEnabled(false);
            user.setCreatedAt(3000L);
            user.setLastActive(4000L);

            assertEquals("id-1", user.getId());
            assertEquals("bob", user.getUsername());
            assertEquals("newhash", user.getPasswordHash());
            assertEquals("wx456", user.getOpenid());
            assertEquals("Bob", user.getNickname());
            assertEquals("gold", user.getMemberLevel());
            assertEquals("ADMIN", user.getRole());
            assertFalse(user.isEnabled());
            assertEquals(3000L, user.getCreatedAt());
            assertEquals(4000L, user.getLastActive());
        }
    }
}
