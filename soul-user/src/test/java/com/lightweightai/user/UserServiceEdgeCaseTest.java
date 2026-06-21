package com.lightweightai.user;

import com.lightweightai.user.model.SoulUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserService - Edge Cases")
class UserServiceEdgeCaseTest {

    private UserService service;
    private UserRepository userRepo;
    private EmotionRepository emotionRepo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test.db").toString();
        userRepo = new UserRepository(dbPath);
        emotionRepo = new EmotionRepository(dbPath);
        service = new UserService(userRepo, emotionRepo);
    }

    // ==================== register() ====================

    @Test
    @DisplayName("register() with duplicate username throws IllegalArgumentException with correct message")
    void registerDuplicateUsernameThrows() {
        service.register("alice", "hash123", "Alice");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.register("alice", "hash456", "Alice2")
        );
        assertEquals("用户名已存在", ex.getMessage());
    }

    @Test
    @DisplayName("register() with null nickname uses username as nickname")
    void registerNullNicknameUsesUsername() {
        SoulUser user = service.register("bob", "hash123", null);

        assertEquals("bob", user.getNickname());
    }

    @Test
    @DisplayName("register() with blank nickname uses username as nickname")
    void registerBlankNicknameUsesUsername() {
        SoulUser user = service.register("charlie", "hash123", "   ");

        assertEquals("charlie", user.getNickname());
    }

    // ==================== updateUserRole() ====================

    @Test
    @DisplayName("updateUserRole() with invalid role throws IllegalArgumentException")
    void updateUserRoleInvalidRoleThrows() {
        SoulUser user = service.register("dave", "hash123", "Dave");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.updateUserRole(user.getId(), "SUPERADMIN")
        );
        assertTrue(ex.getMessage().contains("无效角色"));
    }

    @Test
    @DisplayName("updateUserRole() with null role throws IllegalArgumentException")
    void updateUserRoleNullRoleThrows() {
        SoulUser user = service.register("eve", "hash123", "Eve");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            service.updateUserRole(user.getId(), null)
        );
        assertTrue(ex.getMessage().contains("无效角色"));
    }

    // ==================== seedAdminIfNeeded() ====================

    @Test
    @DisplayName("seedAdminIfNeeded() creates admin when no users exist")
    void seedAdminCreatesAdminWhenEmpty() {
        service.seedAdminIfNeeded("encoded_pw");

        List<SoulUser> all = userRepo.findAll();
        assertEquals(1, all.size());
        SoulUser admin = all.get(0);
        assertEquals("admin", admin.getUsername());
        assertEquals("ADMIN", admin.getRole());
        assertEquals("encoded_pw", admin.getPasswordHash());
        assertTrue(admin.isEnabled());
    }

    @Test
    @DisplayName("seedAdminIfNeeded() is idempotent - calling twice creates only one admin")
    void seedAdminIdempotent() {
        service.seedAdminIfNeeded("pw1");
        service.seedAdminIfNeeded("pw2");

        List<SoulUser> all = userRepo.findAll();
        long adminCount = all.stream().filter(u -> "ADMIN".equals(u.getRole())).count();
        assertEquals(1, adminCount);
    }

    @Test
    @DisplayName("seedAdminIfNeeded() does not create admin if one already exists")
    void seedAdminSkipsWhenAdminExists() {
        // Manually create an admin user
        long now = System.currentTimeMillis();
        SoulUser existingAdmin = new SoulUser("custom-admin", "superuser", "hash", null,
            "Custom Admin", "FREE", "ADMIN", true, now, now);
        userRepo.save(existingAdmin);

        service.seedAdminIfNeeded("new_pw");

        List<SoulUser> all = userRepo.findAll();
        long adminCount = all.stream().filter(u -> "ADMIN".equals(u.getRole())).count();
        assertEquals(1, adminCount);
        // The existing admin should still be the only admin
        assertEquals("custom-admin", all.stream()
            .filter(u -> "ADMIN".equals(u.getRole()))
            .findFirst().orElseThrow().getId());
    }

    // ==================== disableUser / enableUser ====================

    @Test
    @DisplayName("disableUser() then enableUser() toggles user state")
    void disableAndEnableUserTogglesState() {
        SoulUser user = service.register("frank", "hash123", "Frank");
        assertTrue(user.isEnabled());

        service.disableUser(user.getId());
        SoulUser disabled = userRepo.findById(user.getId()).orElseThrow();
        assertFalse(disabled.isEnabled());

        service.enableUser(user.getId());
        SoulUser enabled = userRepo.findById(user.getId()).orElseThrow();
        assertTrue(enabled.isEnabled());
    }

    // ==================== getStats() ====================

    @Test
    @DisplayName("getStats() with no emotion records returns streak=0, totalDays=0, empty distribution")
    void getStatsNoRecordsReturnsDefaults() {
        Map<String, Object> stats = service.getStats("nonexistent-user");

        assertEquals(0, (int) stats.get("streak"));
        assertEquals(0, (int) stats.get("totalDays"));
        @SuppressWarnings("unchecked")
        Map<String, Long> distribution = (Map<String, Long>) stats.get("distribution");
        assertTrue(distribution.isEmpty());
        assertFalse((boolean) stats.get("checkedInToday"));
    }

    @Test
    @DisplayName("Multiple checkins on same day count as 1 unique day")
    void multipleCheckinsOnSameDayCountAsOneDay() {
        service.checkin("user1", "happy", "morning");
        service.checkin("user1", "calm", "afternoon");
        service.checkin("user1", "tired", "evening");

        Map<String, Object> stats = service.getStats("user1");
        assertEquals(1, (int) stats.get("totalDays"));
    }
}
