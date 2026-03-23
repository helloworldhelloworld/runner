package com.lightweightai.user;

import com.lightweightai.user.model.SoulUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserRepository - 用户持久化")
class UserRepositoryTest {

    private UserRepository repository;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        String dbPath = tempDir.resolve("test_users.db").toString();
        repository = new UserRepository(dbPath);
    }

    @Test
    @DisplayName("保存并查询用户")
    void shouldSaveAndFindUser() {
        long now = System.currentTimeMillis();
        SoulUser user = new SoulUser("user1", null, null, "wx_openid_123", "测试用户", "FREE", now, now);
        repository.save(user);

        Optional<SoulUser> found = repository.findById("user1");
        assertTrue(found.isPresent());
        assertEquals("user1", found.get().getId());
        assertEquals("wx_openid_123", found.get().getOpenid());
        assertEquals("测试用户", found.get().getNickname());
        assertEquals("FREE", found.get().getMemberLevel());
        assertEquals(now, found.get().getCreatedAt());
    }

    @Test
    @DisplayName("查询不存在的用户返回空Optional")
    void shouldReturnEmptyForNonExistentUser() {
        Optional<SoulUser> found = repository.findById("no-such-user");
        assertTrue(found.isEmpty());
    }

    @Test
    @DisplayName("Upsert: 更新已存在用户")
    void shouldUpdateExistingUser() {
        long now = System.currentTimeMillis();
        SoulUser user = new SoulUser("user1", null, null, null, "匿名用户", "FREE", now, now);
        repository.save(user);

        // Update nickname and member level
        SoulUser updated = new SoulUser("user1", null, null, "wx_123", "注册用户", "VIP", now, now + 1000);
        repository.save(updated);

        Optional<SoulUser> found = repository.findById("user1");
        assertTrue(found.isPresent());
        assertEquals("注册用户", found.get().getNickname());
        assertEquals("VIP", found.get().getMemberLevel());
        assertEquals("wx_123", found.get().getOpenid());
    }

    @Test
    @DisplayName("openid可以为null")
    void shouldHandleNullOpenid() {
        long now = System.currentTimeMillis();
        SoulUser user = new SoulUser("user1", null, null, null, "匿名用户", "FREE", now, now);
        repository.save(user);

        Optional<SoulUser> found = repository.findById("user1");
        assertTrue(found.isPresent());
        assertNull(found.get().getOpenid());
    }

    @Test
    @DisplayName("更新最后活跃时间")
    void shouldUpdateLastActive() {
        long now = System.currentTimeMillis();
        SoulUser user = new SoulUser("user1", null, null, null, "匿名用户", "FREE", now, now);
        repository.save(user);

        long newTime = now + 60000;
        repository.updateLastActive("user1", newTime);

        Optional<SoulUser> found = repository.findById("user1");
        assertTrue(found.isPresent());
        assertEquals(newTime, found.get().getLastActive());
    }
}
