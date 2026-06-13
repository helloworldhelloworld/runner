package com.lightweightai.user;

import com.lightweightai.user.model.SoulUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserService - 管理员与用户管理操作")
class UserServiceAdminTest {

    private UserService service;
    private UserRepository userRepo;
    private EmotionRepository emotionRepo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test_admin.db").toString();
        userRepo = new UserRepository(dbPath);
        emotionRepo = new EmotionRepository(dbPath);
        service = new UserService(userRepo, emotionRepo);
    }

    @Nested
    @DisplayName("seedAdminIfNeeded")
    class SeedAdmin {

        @Test
        @DisplayName("无管理员时创建默认管理员")
        void shouldSeedAdminWhenNoneExists() {
            service.seedAdminIfNeeded("encoded-password");

            List<SoulUser> all = service.listAllUsers();
            assertEquals(1, all.size());
            SoulUser admin = all.get(0);
            assertEquals("admin", admin.getUsername());
            assertEquals("ADMIN", admin.getRole());
            assertEquals("管理员", admin.getNickname());
            assertTrue(admin.getId().startsWith("usr-admin-"));
            assertTrue(admin.isEnabled());
        }

        @Test
        @DisplayName("已存在管理员时不重复创建")
        void shouldNotSeedWhenAdminExists() {
            service.seedAdminIfNeeded("pass1");
            int countAfterFirst = service.listAllUsers().size();

            service.seedAdminIfNeeded("pass2");
            int countAfterSecond = service.listAllUsers().size();

            assertEquals(countAfterFirst, countAfterSecond);
        }

        @Test
        @DisplayName("密码哈希正确存储")
        void shouldStorePasswordHash() {
            service.seedAdminIfNeeded("hashed-secret");

            Optional<SoulUser> admin = service.findByUsername("admin");
            assertTrue(admin.isPresent());
            assertEquals("hashed-secret", admin.get().getPasswordHash());
        }
    }

    @Nested
    @DisplayName("updateUserRole")
    class UpdateRole {

        @Test
        @DisplayName("USER升级为ADMIN")
        void shouldUpgradeToAdmin() {
            SoulUser user = service.register("testuser", "hash", "Test");

            service.updateUserRole(user.getId(), "ADMIN");

            SoulUser updated = service.findById(user.getId()).orElseThrow();
            assertEquals("ADMIN", updated.getRole());
        }

        @Test
        @DisplayName("ADMIN降级为USER")
        void shouldDowngradeToUser() {
            service.seedAdminIfNeeded("pass");
            SoulUser admin = service.findByUsername("admin").orElseThrow();

            service.updateUserRole(admin.getId(), "USER");

            SoulUser updated = service.findById(admin.getId()).orElseThrow();
            assertEquals("USER", updated.getRole());
        }

        @Test
        @DisplayName("无效角色抛出异常")
        void shouldRejectInvalidRole() {
            SoulUser user = service.register("testuser", "hash", "Test");

            assertThrows(IllegalArgumentException.class,
                () -> service.updateUserRole(user.getId(), "SUPERADMIN"));
        }

        @Test
        @DisplayName("空角色抛出异常")
        void shouldRejectNullRole() {
            SoulUser user = service.register("testuser", "hash", "Test");

            assertThrows(IllegalArgumentException.class,
                () -> service.updateUserRole(user.getId(), null));
        }
    }

    @Nested
    @DisplayName("enableUser / disableUser")
    class EnableDisable {

        @Test
        @DisplayName("禁用用户")
        void shouldDisableUser() {
            SoulUser user = service.register("testuser", "hash", "Test");
            assertTrue(user.isEnabled());

            service.disableUser(user.getId());

            SoulUser updated = service.findById(user.getId()).orElseThrow();
            assertFalse(updated.isEnabled());
        }

        @Test
        @DisplayName("启用已禁用的用户")
        void shouldEnableDisabledUser() {
            SoulUser user = service.register("testuser", "hash", "Test");
            service.disableUser(user.getId());
            assertFalse(service.findById(user.getId()).orElseThrow().isEnabled());

            service.enableUser(user.getId());

            SoulUser updated = service.findById(user.getId()).orElseThrow();
            assertTrue(updated.isEnabled());
        }
    }

    @Nested
    @DisplayName("findByUsername / findById")
    class FindOperations {

        @Test
        @DisplayName("按用户名查找已注册用户")
        void shouldFindByUsername() {
            service.register("alice", "hash", "Alice");

            Optional<SoulUser> found = service.findByUsername("alice");
            assertTrue(found.isPresent());
            assertEquals("Alice", found.get().getNickname());
        }

        @Test
        @DisplayName("按用户名查找不存在的用户返回empty")
        void shouldReturnEmptyForMissingUsername() {
            Optional<SoulUser> found = service.findByUsername("nonexistent");
            assertTrue(found.isEmpty());
        }

        @Test
        @DisplayName("按ID查找已有用户")
        void shouldFindById() {
            SoulUser created = service.register("bob", "hash", "Bob");

            Optional<SoulUser> found = service.findById(created.getId());
            assertTrue(found.isPresent());
            assertEquals("bob", found.get().getUsername());
        }

        @Test
        @DisplayName("按ID查找不存在的用户返回empty")
        void shouldReturnEmptyForMissingId() {
            Optional<SoulUser> found = service.findById("nonexistent-id");
            assertTrue(found.isEmpty());
        }
    }

    @Nested
    @DisplayName("listAllUsers")
    class ListUsers {

        @Test
        @DisplayName("空数据库返回空列表")
        void shouldReturnEmptyListWhenNoUsers() {
            List<SoulUser> users = service.listAllUsers();
            assertTrue(users.isEmpty());
        }

        @Test
        @DisplayName("返回所有用户按创建时间降序")
        void shouldReturnAllUsersDescending() {
            service.register("user1", "h1", "User1");
            service.register("user2", "h2", "User2");
            service.register("user3", "h3", "User3");

            List<SoulUser> users = service.listAllUsers();
            assertEquals(3, users.size());
            // Latest created should be first (DESC order)
            assertEquals("user3", users.get(0).getUsername());
        }

        @Test
        @DisplayName("包含匿名用户和注册用户")
        void shouldIncludeBothAnonymousAndRegistered() {
            service.createAnonymousUser();
            service.register("registered", "hash", "Reg");

            List<SoulUser> users = service.listAllUsers();
            assertEquals(2, users.size());
        }
    }

    @Nested
    @DisplayName("register 边界条件")
    class RegisterEdgeCases {

        @Test
        @DisplayName("重复用户名抛出异常")
        void shouldRejectDuplicateUsername() {
            service.register("duplicate", "hash", "First");

            assertThrows(IllegalArgumentException.class,
                () -> service.register("duplicate", "hash2", "Second"));
        }

        @Test
        @DisplayName("空昵称使用用户名作为昵称")
        void shouldUseUsernameWhenNicknameBlank() {
            SoulUser user = service.register("myuser", "hash", "");
            assertEquals("myuser", user.getNickname());
        }

        @Test
        @DisplayName("null昵称使用用户名作为昵称")
        void shouldUseUsernameWhenNicknameNull() {
            SoulUser user = service.register("myuser", "hash", null);
            assertEquals("myuser", user.getNickname());
        }

        @Test
        @DisplayName("注册用户默认角色为USER")
        void shouldDefaultToUserRole() {
            SoulUser user = service.register("newuser", "hash", "New");
            assertEquals("USER", user.getRole());
        }

        @Test
        @DisplayName("注册用户默认已启用")
        void shouldDefaultToEnabled() {
            SoulUser user = service.register("newuser", "hash", "New");
            assertTrue(user.isEnabled());
        }
    }
}
