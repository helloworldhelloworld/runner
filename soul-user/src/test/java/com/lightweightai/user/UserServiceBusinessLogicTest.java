package com.lightweightai.user;

import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserService - 业务逻辑验证")
class UserServiceBusinessLogicTest {

    private UserRepository userRepo;
    private EmotionRepository emotionRepo;
    private UserService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("biz_test.db").toString();
        userRepo = new UserRepository(dbPath);
        emotionRepo = new EmotionRepository(dbPath);
        service = new UserService(userRepo, emotionRepo);
    }

    @Test
    @DisplayName("createAnonymousUser 创建以 anon- 前缀的匿名用户")
    void createAnonymousUser() {
        SoulUser user = service.createAnonymousUser();
        assertNotNull(user);
        assertTrue(user.getId().startsWith("anon-"));
        assertEquals("匿名用户", user.getNickname());
        assertEquals("FREE", user.getMemberLevel());
        assertEquals("USER", user.getRole());
        assertTrue(user.isEnabled());
    }

    @Test
    @DisplayName("getOrCreate 对已存在用户返回相同实例")
    void getOrCreateReturnsExisting() {
        SoulUser created = service.createAnonymousUser();
        SoulUser fetched = service.getOrCreate(created.getId());
        assertEquals(created.getId(), fetched.getId());
    }

    @Test
    @DisplayName("getOrCreate 对不存在用户自动创建")
    void getOrCreateCreatesNew() {
        SoulUser user = service.getOrCreate("new-user-123");
        assertNotNull(user);
        assertEquals("new-user-123", user.getId());
        assertEquals("匿名用户", user.getNickname());
    }

    @Test
    @DisplayName("register 创建用户并设置 username/nickname")
    void registerCreatesUser() {
        SoulUser user = service.register("john", "hash123", "Johnny");
        assertNotNull(user);
        assertTrue(user.getId().startsWith("usr-"));
        assertEquals("john", user.getUsername());
        assertEquals("Johnny", user.getNickname());
    }

    @Test
    @DisplayName("register 用户名已存在时抛异常")
    void registerDuplicateUsernameThrows() {
        service.register("john", "hash1", "J1");
        assertThrows(IllegalArgumentException.class,
                () -> service.register("john", "hash2", "J2"));
    }

    @Test
    @DisplayName("register 昵称为空/blank 时使用 username 作为昵称")
    void registerBlankNicknameUsesUsername() {
        SoulUser user1 = service.register("alice", "hash", null);
        assertEquals("alice", user1.getNickname());

        SoulUser user2 = service.register("bob", "hash", "  ");
        assertEquals("bob", user2.getNickname());
    }

    @Test
    @DisplayName("updateUserRole 仅接受 ADMIN 或 USER")
    void updateRoleValidation() {
        SoulUser user = service.createAnonymousUser();
        assertDoesNotThrow(() -> service.updateUserRole(user.getId(), "ADMIN"));
        assertDoesNotThrow(() -> service.updateUserRole(user.getId(), "USER"));
        assertThrows(IllegalArgumentException.class,
                () -> service.updateUserRole(user.getId(), "SUPERADMIN"));
    }

    @Test
    @DisplayName("enableUser / disableUser 切换用户状态")
    void enableDisableUser() {
        SoulUser user = service.createAnonymousUser();
        service.disableUser(user.getId());
        service.enableUser(user.getId());
    }

    @Test
    @DisplayName("checkin 创建情绪记录并更新 lastActive")
    void checkinCreatesRecord() {
        SoulUser user = service.createAnonymousUser();
        EmotionRecord record = service.checkin(user.getId(), "happy", "feeling good");

        assertNotNull(record);
        assertEquals(user.getId(), record.getUserId());
        assertEquals("happy", record.getEmotion());
        assertEquals("feeling good", record.getNote());
        assertTrue(record.getRecordedAt() > 0);
    }

    @Test
    @DisplayName("checkin 对不存在用户先自动创建再记录")
    void checkinAutoCreatesUser() {
        EmotionRecord record = service.checkin("brand-new", "calm", null);
        assertNotNull(record);
        assertEquals("brand-new", record.getUserId());

        Optional<SoulUser> user = service.findById("brand-new");
        assertTrue(user.isPresent());
    }

    @Test
    @DisplayName("getEmotions 返回指定天数内的记录")
    void getEmotionsWithinDays() {
        SoulUser user = service.createAnonymousUser();
        service.checkin(user.getId(), "happy", null);

        List<EmotionRecord> recent = service.getEmotions(user.getId(), 7);
        assertFalse(recent.isEmpty());
    }

    @Test
    @DisplayName("seedAdminIfNeeded 仅在无 admin 时创建")
    void seedAdminOnlyWhenNoAdminExists() {
        service.seedAdminIfNeeded("enc-pass");
        List<SoulUser> users = service.listAllUsers();
        long adminCount = users.stream().filter(u -> "ADMIN".equals(u.getRole())).count();
        assertEquals(1, adminCount);

        service.seedAdminIfNeeded("enc-pass-2");
        users = service.listAllUsers();
        adminCount = users.stream().filter(u -> "ADMIN".equals(u.getRole())).count();
        assertEquals(1, adminCount, "Should not create another admin");
    }

    @Test
    @DisplayName("getStats 返回含 streak、distribution、checkedInToday 的结果")
    void getStatsReturnsExpectedKeys() {
        SoulUser user = service.createAnonymousUser();
        service.checkin(user.getId(), "happy", null);

        Map<String, Object> stats = service.getStats(user.getId());
        assertTrue(stats.containsKey("streak"));
        assertTrue(stats.containsKey("distribution"));
        assertTrue(stats.containsKey("checkedInToday"));
        assertTrue(stats.containsKey("totalDays"));
    }

    @Test
    @DisplayName("findByUsername 找到已注册用户")
    void findByUsername() {
        service.register("testuser", "hash", "Test");
        Optional<SoulUser> found = service.findByUsername("testuser");
        assertTrue(found.isPresent());
        assertEquals("testuser", found.get().getUsername());
    }

    @Test
    @DisplayName("findByUsername 未注册返回 empty")
    void findByUsernameNotFound() {
        Optional<SoulUser> found = service.findByUsername("nonexistent");
        assertFalse(found.isPresent());
    }
}
