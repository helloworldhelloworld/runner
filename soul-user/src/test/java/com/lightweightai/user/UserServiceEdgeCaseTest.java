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

@DisplayName("UserService — 边缘场景与未覆盖方法")
class UserServiceEdgeCaseTest {

    private UserService service;
    private UserRepository userRepo;
    private EmotionRepository emotionRepo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("edge_test.db").toString();
        userRepo = new UserRepository(dbPath);
        emotionRepo = new EmotionRepository(dbPath);
        service = new UserService(userRepo, emotionRepo);
    }

    // ==================== register ====================

    @Nested
    @DisplayName("register")
    class RegisterTests {

        @Test
        @DisplayName("注册成功返回用户")
        void shouldRegisterSuccessfully() {
            SoulUser user = service.register("alice", "hash123", "Alice");

            assertNotNull(user);
            assertTrue(user.getId().startsWith("usr-"));
            assertEquals("alice", user.getUsername());
            assertEquals("Alice", user.getNickname());
            assertEquals("FREE", user.getMemberLevel());
            assertEquals("USER", user.getRole());
            assertTrue(user.isEnabled());
        }

        @Test
        @DisplayName("重复用户名抛出异常")
        void shouldRejectDuplicateUsername() {
            service.register("bob", "hash", "Bob");

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.register("bob", "hash2", "Bob2"));
            assertTrue(ex.getMessage().contains("已存在"));
        }

        @Test
        @DisplayName("昵称为空时使用用户名作为昵称")
        void shouldFallbackToUsernameWhenNicknameBlank() {
            SoulUser user = service.register("charlie", "hash", "");
            assertEquals("charlie", user.getNickname());
        }

        @Test
        @DisplayName("昵称为null时使用用户名作为昵称")
        void shouldFallbackToUsernameWhenNicknameNull() {
            SoulUser user = service.register("dave", "hash", null);
            assertEquals("dave", user.getNickname());
        }
    }

    // ==================== updateUserRole ====================

    @Nested
    @DisplayName("updateUserRole")
    class UpdateRoleTests {

        @Test
        @DisplayName("设置为ADMIN成功")
        void shouldUpdateToAdmin() {
            SoulUser user = service.createAnonymousUser();
            service.updateUserRole(user.getId(), "ADMIN");

            SoulUser updated = service.findById(user.getId()).orElseThrow();
            assertEquals("ADMIN", updated.getRole());
        }

        @Test
        @DisplayName("设置为USER成功")
        void shouldUpdateToUser() {
            SoulUser user = service.register("test", "hash", "Test");
            service.updateUserRole(user.getId(), "ADMIN");
            service.updateUserRole(user.getId(), "USER");

            SoulUser updated = service.findById(user.getId()).orElseThrow();
            assertEquals("USER", updated.getRole());
        }

        @Test
        @DisplayName("无效角色抛出异常")
        void shouldRejectInvalidRole() {
            SoulUser user = service.createAnonymousUser();

            assertThrows(IllegalArgumentException.class,
                    () -> service.updateUserRole(user.getId(), "SUPERADMIN"));
        }
    }

    // ==================== enableUser / disableUser ====================

    @Nested
    @DisplayName("enableUser / disableUser")
    class EnableDisableTests {

        @Test
        @DisplayName("禁用用户")
        void shouldDisableUser() {
            SoulUser user = service.createAnonymousUser();
            service.disableUser(user.getId());

            SoulUser updated = service.findById(user.getId()).orElseThrow();
            assertFalse(updated.isEnabled());
        }

        @Test
        @DisplayName("重新启用用户")
        void shouldReEnableUser() {
            SoulUser user = service.createAnonymousUser();
            service.disableUser(user.getId());
            service.enableUser(user.getId());

            SoulUser updated = service.findById(user.getId()).orElseThrow();
            assertTrue(updated.isEnabled());
        }
    }

    // ==================== seedAdminIfNeeded ====================

    @Nested
    @DisplayName("seedAdminIfNeeded")
    class SeedAdminTests {

        @Test
        @DisplayName("无管理员时创建默认管理员")
        void shouldSeedAdminWhenNoneExists() {
            service.seedAdminIfNeeded("encoded-pw");

            List<SoulUser> all = service.listAllUsers();
            boolean hasAdmin = all.stream().anyMatch(u -> "ADMIN".equals(u.getRole()));
            assertTrue(hasAdmin);

            SoulUser admin = all.stream()
                    .filter(u -> "ADMIN".equals(u.getRole()))
                    .findFirst().orElseThrow();
            assertEquals("admin", admin.getUsername());
            assertEquals("管理员", admin.getNickname());
        }

        @Test
        @DisplayName("已有管理员时不重复创建")
        void shouldNotSeedWhenAdminExists() {
            service.seedAdminIfNeeded("pw1");
            int countBefore = service.listAllUsers().size();

            service.seedAdminIfNeeded("pw2");
            int countAfter = service.listAllUsers().size();

            assertEquals(countBefore, countAfter);
        }
    }

    // ==================== listAllUsers ====================

    @Nested
    @DisplayName("listAllUsers")
    class ListUsersTests {

        @Test
        @DisplayName("空库返回空列表")
        void shouldReturnEmptyListWhenNoUsers() {
            List<SoulUser> users = service.listAllUsers();
            assertTrue(users.isEmpty());
        }

        @Test
        @DisplayName("返回所有用户")
        void shouldReturnAllUsers() {
            service.createAnonymousUser();
            service.createAnonymousUser();
            service.register("test", "hash", "Test");

            List<SoulUser> users = service.listAllUsers();
            assertEquals(3, users.size());
        }
    }

    // ==================== findByUsername / findById ====================

    @Nested
    @DisplayName("findByUsername / findById")
    class FindTests {

        @Test
        @DisplayName("findByUsername 找到已注册用户")
        void shouldFindByUsername() {
            service.register("alice", "hash", "Alice");
            Optional<SoulUser> found = service.findByUsername("alice");
            assertTrue(found.isPresent());
            assertEquals("alice", found.get().getUsername());
        }

        @Test
        @DisplayName("findByUsername 不存在返回 empty")
        void shouldReturnEmptyForUnknownUsername() {
            Optional<SoulUser> found = service.findByUsername("nonexistent");
            assertTrue(found.isEmpty());
        }

        @Test
        @DisplayName("findById 不存在返回 empty")
        void shouldReturnEmptyForUnknownId() {
            Optional<SoulUser> found = service.findById("no-such-id");
            assertTrue(found.isEmpty());
        }
    }
}
