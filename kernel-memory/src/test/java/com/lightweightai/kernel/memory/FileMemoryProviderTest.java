package com.lightweightai.kernel.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TDD: FileMemoryProvider 测试
 *
 * 验证 FileMemoryProvider 正确适配 MemoryProvider 接口。
 */
@DisplayName("FileMemoryProvider - 文件存储记忆提供者")
class FileMemoryProviderTest {

    private static Path tempDir;
    private FileMemoryProvider provider;
    private FileMemoryManager fileManager;

    @BeforeAll
    static void setUpClass() throws IOException {
        tempDir = Files.createTempDirectory("file-memory-test");
    }

    @AfterAll
    static void tearDownClass() throws IOException {
        // 清理临时目录
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                    }
                });
        }
    }

    @BeforeEach
    void setUp() {
        fileManager = new FileMemoryManager(tempDir, "test-agent", new MockEmbeddingProvider());
        provider = new FileMemoryProvider(fileManager);
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.close();
        }
    }

    // ==================== 会话记忆测试 ====================

    @Test
    @DisplayName("添加并获取会话消息")
    void shouldAddAndGetSessionMessages() {
        // Given
        String sessionId = "test-session-" + System.currentTimeMillis();
        Message userMsg = Message.user("你好，我叫李四");
        Message assistantMsg = Message.assistant("你好李四，很高兴认识你");

        // When
        provider.addMessage(sessionId, userMsg);
        provider.addMessage(sessionId, assistantMsg);
        List<Message> history = provider.getHistory(sessionId, 10);

        // Then
        assertEquals(2, history.size());
        assertEquals("user", history.get(0).getRole());
        assertEquals("你好，我叫李四", history.get(0).getContent());
    }

    @Test
    @DisplayName("会话持久化 - 重新加载后仍可读取")
    void shouldPersistSessionMessages() {
        // Given
        String sessionId = "persist-test-" + System.currentTimeMillis();
        provider.addMessage(sessionId, Message.user("持久化测试消息"));
        provider.close();

        // When - 重新创建 provider
        FileMemoryManager newManager = new FileMemoryManager(tempDir, "test-agent", new MockEmbeddingProvider());
        FileMemoryProvider newProvider = new FileMemoryProvider(newManager);
        List<Message> history = newProvider.getHistory(sessionId, 10);

        // Then
        assertEquals(1, history.size());
        assertEquals("持久化测试消息", history.get(0).getContent());

        newProvider.close();
    }

    // ==================== 持久化记忆测试 ====================

    @Test
    @DisplayName("写入并读取临时记忆")
    void shouldWriteAndReadEphemeralMemory() {
        // When
        provider.writeEphemeral("今日测试记录1");
        provider.writeEphemeral("今日测试记录2");

        // Then
        String content = provider.readTodayEphemeral();
        assertTrue(content.contains("今日测试记录1"));
        assertTrue(content.contains("今日测试记录2"));
    }

    @Test
    @DisplayName("写入并读取长期记忆")
    void shouldWriteAndReadDurableMemory() {
        // When
        provider.writeDurable("用户信息", "姓名: 测试用户");
        provider.writeDurable("爱好", "编程、阅读");

        // Then
        String content = provider.readDurable();
        assertTrue(content.contains("用户信息"));
        assertTrue(content.contains("测试用户"));
    }

    // ==================== 搜索测试 ====================

    @Test
    @DisplayName("搜索临时记忆")
    void shouldSearchEphemeralMemory() {
        // Given
        provider.writeEphemeral("用户王五喜欢喝咖啡");
        provider.writeEphemeral("用户讨厌下雨天");

        // When
        List<MemorySearchResult> results = provider.search("咖啡");

        // Then
        assertFalse(results.isEmpty());
        assertTrue(results.get(0).getContent().contains("咖啡"));
    }

    @Test
    @DisplayName("搜索结果按相关性排序")
    void shouldSortSearchResultsByRelevance() {
        // Given
        provider.writeEphemeral("用户喜欢Python编程");
        provider.writeEphemeral("用户学习Java和Python");
        provider.writeEphemeral("用户不喜欢运动");

        // When
        List<MemorySearchResult> results = provider.search("Python");

        // Then
        assertTrue(results.size() >= 2);
        // 确保有分数且排序
        for (int i = 1; i < results.size(); i++) {
            assertTrue(results.get(i - 1).getScore() >= results.get(i).getScore());
        }
    }
}
