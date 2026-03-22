package com.lightweightai.user;

import com.lightweightai.user.model.EmotionRecord;
import com.lightweightai.user.model.SoulUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserService - 用户服务集成")
class UserServiceTest {

    private UserService service;
    private UserRepository userRepo;
    private EmotionRepository emotionRepo;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test_service.db").toString();
        userRepo = new UserRepository(dbPath);
        emotionRepo = new EmotionRepository(dbPath);
        service = new UserService(userRepo, emotionRepo);
    }

    // ==================== 匿名用户 ====================

    @Test
    @DisplayName("创建匿名用户")
    void shouldCreateAnonymousUser() {
        SoulUser user = service.createAnonymousUser();

        assertNotNull(user);
        assertTrue(user.getId().startsWith("anon-"));
        assertEquals(21, user.getId().length()); // "anon-" + 16 chars
        assertEquals("匿名用户", user.getNickname());
        assertEquals("FREE", user.getMemberLevel());
        assertNull(user.getOpenid());
        assertTrue(user.getCreatedAt() > 0);
    }

    @Test
    @DisplayName("匿名用户ID唯一")
    void shouldCreateUniqueAnonymousUsers() {
        SoulUser user1 = service.createAnonymousUser();
        SoulUser user2 = service.createAnonymousUser();
        assertNotEquals(user1.getId(), user2.getId());
    }

    // ==================== getOrCreate ====================

    @Test
    @DisplayName("getOrCreate: 创建新用户")
    void shouldCreateNewUserOnGetOrCreate() {
        SoulUser user = service.getOrCreate("test-user-1");

        assertNotNull(user);
        assertEquals("test-user-1", user.getId());
        assertEquals("匿名用户", user.getNickname());
        assertEquals("FREE", user.getMemberLevel());
    }

    @Test
    @DisplayName("getOrCreate: 返回已存在用户")
    void shouldReturnExistingUserOnGetOrCreate() {
        long now = System.currentTimeMillis();
        userRepo.save(new SoulUser("existing", "wx_1", "已注册", "VIP", now, now));

        SoulUser user = service.getOrCreate("existing");
        assertEquals("已注册", user.getNickname());
        assertEquals("VIP", user.getMemberLevel());
    }

    // ==================== 情绪签到 ====================

    @Test
    @DisplayName("签到创建情绪记录")
    void shouldCreateEmotionRecordOnCheckin() {
        EmotionRecord record = service.checkin("user1", "😊", "开心的一天");

        assertNotNull(record);
        assertTrue(record.getId().startsWith("er-"));
        assertEquals("user1", record.getUserId());
        assertEquals("😊", record.getEmotion());
        assertEquals("开心的一天", record.getNote());
        assertTrue(record.getRecordedAt() > 0);
    }

    @Test
    @DisplayName("签到自动创建不存在的用户")
    void shouldAutoCreateUserOnCheckin() {
        service.checkin("new-user", "😊", null);

        // User should now exist
        SoulUser user = service.getOrCreate("new-user");
        assertEquals("new-user", user.getId());
    }

    @Test
    @DisplayName("签到更新最后活跃时间")
    void shouldUpdateLastActiveOnCheckin() {
        long before = System.currentTimeMillis();
        service.checkin("user1", "😊", null);

        SoulUser user = service.getOrCreate("user1");
        assertTrue(user.getLastActive() >= before);
    }

    // ==================== 情绪查询 ====================

    @Test
    @DisplayName("获取最近N天情绪记录")
    void shouldGetEmotionsWithinDays() {
        service.checkin("user1", "😊", null);
        service.checkin("user1", "😢", null);

        List<EmotionRecord> emotions = service.getEmotions("user1", 7);
        assertEquals(2, emotions.size());
    }

    // ==================== 统计 ====================

    @Test
    @DisplayName("获取统计数据结构")
    void shouldReturnStatsMap() {
        service.checkin("user1", "😊", null);
        service.checkin("user1", "😢", null);
        service.checkin("user1", "😊", null);

        Map<String, Object> stats = service.getStats("user1");

        assertTrue(stats.containsKey("streak"));
        assertTrue(stats.containsKey("distribution"));
        assertTrue(stats.containsKey("checkedInToday"));
        assertTrue(stats.containsKey("totalDays"));
    }

    @Test
    @DisplayName("统计: 签到连续天数(今天签到)")
    void shouldCalculateStreakForToday() {
        service.checkin("user1", "😊", null);

        Map<String, Object> stats = service.getStats("user1");
        int streak = (int) stats.get("streak");
        assertEquals(1, streak);
    }

    @Test
    @DisplayName("统计: 情绪分布")
    void shouldCalculateEmotionDistribution() {
        service.checkin("user1", "😊", null);
        service.checkin("user1", "😊", null);
        service.checkin("user1", "😢", null);

        Map<String, Object> stats = service.getStats("user1");
        @SuppressWarnings("unchecked")
        Map<String, Long> distribution = (Map<String, Long>) stats.get("distribution");
        assertEquals(2L, distribution.get("😊"));
        assertEquals(1L, distribution.get("😢"));
    }

    @Test
    @DisplayName("统计: 今日已签到")
    void shouldDetectCheckedInToday() {
        service.checkin("user1", "😊", null);

        Map<String, Object> stats = service.getStats("user1");
        assertTrue((boolean) stats.get("checkedInToday"));
    }

    @Test
    @DisplayName("统计: 今日未签到")
    void shouldDetectNotCheckedInToday() {
        Map<String, Object> stats = service.getStats("new-user");
        assertFalse((boolean) stats.get("checkedInToday"));
    }

    @Test
    @DisplayName("统计: 唯一天数")
    void shouldCountUniqueDays() {
        // Multiple checkins on the same day
        service.checkin("user1", "😊", null);
        service.checkin("user1", "😢", null);

        Map<String, Object> stats = service.getStats("user1");
        assertEquals(1, (int) stats.get("totalDays"));
    }

    @Test
    @DisplayName("统计: 空记录")
    void shouldHandleEmptyStats() {
        Map<String, Object> stats = service.getStats("no-records");
        assertEquals(0, (int) stats.get("streak"));
        assertEquals(0, (int) stats.get("totalDays"));
    }
}
