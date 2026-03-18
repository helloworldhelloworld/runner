package com.lightweightai.kernel.memory.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.file.SessionTranscript;
import com.lightweightai.kernel.memory.model.SearchResult;
import com.lightweightai.kernel.memory.model.TranscriptEntry;
import com.lightweightai.kernel.memory.queue.LaneQueueManager;
import com.lightweightai.kernel.memory.tools.MemoryToolkit;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end integration test demonstrating the complete OpenClaw-style
 * memory system workflow.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EndToEndTest {

    @TempDir
    static Path tempDir;

    static FileMemoryManager memoryManager;
    static LaneQueueManager laneManager;
    static MemoryToolkit toolkit;

    @BeforeAll
    static void setUp() {
        Path agentRoot = tempDir.resolve("soul-comfort-agent");
        memoryManager = new FileMemoryManager(
            agentRoot,
            "soul-comfort",
            new MockEmbeddingProvider(128)
        );
        laneManager = new LaneQueueManager();
        toolkit = MemoryToolkit.create(memoryManager);

        System.out.println("\n========================================");
        System.out.println("  OpenClaw-Style Memory System Demo");
        System.out.println("========================================\n");
    }

    @AfterAll
    static void tearDown() {
        memoryManager.close();
        laneManager.close();
    }

    // ==================== Scenario 1: User Profile Setup ====================

    @Test
    @Order(1)
    @DisplayName("1. User introduces themselves - Store in durable memory")
    void testUserIntroduction() {
        System.out.println(">>> User: 我叫小明，喜欢深夜聊天，最近在学编程");

        // Store user profile in durable memory
        var result = toolkit.getWriteTool().execute(Map.of(
            "content", "- 名字：小明\n- 偏好：深夜聊天\n- 兴趣：学习编程",
            "type", "durable",
            "section", "用户档案"
        ));

        assertTrue(result.success());
        System.out.println("<<< Agent: 很高兴认识你，小明！学编程是个很好的选择~");
        System.out.println("    [已保存用户档案到长期记忆]\n");
    }

    @Test
    @Order(2)
    @DisplayName("2. User shares feelings - Store in ephemeral memory")
    void testUserSharesFeelings() {
        System.out.println(">>> User: 今天工作压力很大，感觉很累");

        // Store today's context in ephemeral memory
        memoryManager.appendEphemeral("用户情绪：疲惫、压力大\n原因：工作压力");

        System.out.println("<<< Agent: 工作压力确实让人疲惫...能跟我说说发生了什么吗？");
        System.out.println("    [已记录到今日日志]\n");

        String todayMemory = memoryManager.readTodayEphemeral();
        assertTrue(todayMemory.contains("压力大"));
    }

    // ==================== Scenario 2: Conversation with History ====================

    @Test
    @Order(3)
    @DisplayName("3. Multi-turn conversation with session transcript")
    void testConversationWithTranscript() {
        System.out.println("--- 开始新对话会话 ---\n");

        SessionTranscript session = memoryManager.createSession("late-night-chat");

        // Simulate multi-turn conversation
        String[][] conversation = {
            {"user", "睡不着，脑子里一直想着明天的会议"},
            {"assistant", "深夜的思绪总是格外清晰...明天的会议让你担心什么呢？"},
            {"user", "怕做不好presentation，被领导批评"},
            {"assistant", "这种担心是很正常的。你之前做过presentation吗？"},
            {"user", "做过几次，其实反馈还不错"},
            {"assistant", "那你已经有成功的经验了！相信这次也会顺利的。"}
        };

        for (String[] turn : conversation) {
            String role = turn[0];
            String content = turn[1];

            if (role.equals("user")) {
                session.append(TranscriptEntry.userMessage(content));
                System.out.println(">>> User: " + content);
            } else {
                session.append(TranscriptEntry.assistantMessage(content));
                System.out.println("<<< Agent: " + content);
            }
        }

        // Verify transcript
        var entries = session.readAll();
        assertEquals(6, entries.size());
        System.out.println("\n    [会话已保存: " + session.getSessionId() + ".jsonl]\n");
    }

    // ==================== Scenario 3: Memory Recall ====================

    @Test
    @Order(4)
    @DisplayName("4. Agent recalls user info during conversation")
    void testMemoryRecall() {
        System.out.println("--- 第二天的对话 ---\n");
        System.out.println(">>> User: 嗨，今天会议结束了");

        // Search for user context
        var searchResult = toolkit.getSearchTool().execute(Map.of(
            "query", "用户 会议 presentation",
            "top_k", 3
        ));

        assertTrue(searchResult.success());
        System.out.println("    [搜索记忆: 找到 " + searchResult.matches().size() + " 条相关记录]");

        // Search for user profile
        var profileResult = toolkit.getSearchTool().execute(Map.of(
            "query", "用户档案 名字 偏好"
        ));

        if (!profileResult.matches().isEmpty()) {
            System.out.println("    [回忆起用户档案]");
        }

        System.out.println("<<< Agent: 小明！昨晚你还在担心presentation呢，今天怎么样？\n");
    }

    // ==================== Scenario 4: Lane Queue for Concurrent Sessions ====================

    @Test
    @Order(5)
    @DisplayName("5. Handle multiple users with Lane Queue")
    void testConcurrentSessions() throws Exception {
        System.out.println("--- 模拟多用户并发 ---\n");

        AtomicInteger user1Tasks = new AtomicInteger(0);
        AtomicInteger user2Tasks = new AtomicInteger(0);

        // User 1 sends multiple messages
        CompletableFuture<Void> u1t1 = laneManager.submitVoid("user-xiaoming", () -> {
            simulateProcessing("小明", "消息1", 50);
            user1Tasks.incrementAndGet();
        });
        CompletableFuture<Void> u1t2 = laneManager.submitVoid("user-xiaoming", () -> {
            simulateProcessing("小明", "消息2", 30);
            user1Tasks.incrementAndGet();
        });

        // User 2 sends messages (should process in parallel with User 1)
        CompletableFuture<Void> u2t1 = laneManager.submitVoid("user-xiaohong", () -> {
            simulateProcessing("小红", "消息1", 40);
            user2Tasks.incrementAndGet();
        });
        CompletableFuture<Void> u2t2 = laneManager.submitVoid("user-xiaohong", () -> {
            simulateProcessing("小红", "消息2", 20);
            user2Tasks.incrementAndGet();
        });

        // Wait for all
        CompletableFuture.allOf(u1t1, u1t2, u2t1, u2t2).get(5, TimeUnit.SECONDS);

        assertEquals(2, user1Tasks.get());
        assertEquals(2, user2Tasks.get());

        System.out.println("\n    [Lane Queue: 每个用户的消息串行处理，不同用户并行]");
        System.out.println("    - 小明的队列: 2 个任务完成");
        System.out.println("    - 小红的队列: 2 个任务完成\n");
    }

    // ==================== Scenario 5: Tool Integration for AI Agent ====================

    @Test
    @Order(6)
    @DisplayName("6. AI Agent uses memory tools")
    void testAgentToolUsage() {
        System.out.println("--- AI Agent 工具调用模拟 ---\n");

        // Simulate tool calls from LLM
        System.out.println("LLM 决定调用 memory_search 工具...");

        var toolSchemas = toolkit.getToolSchemas();
        System.out.println("可用工具: " + toolSchemas.stream()
            .map(s -> s.get("name").toString())
            .toList());

        // Execute tool through unified interface
        var executor = toolkit.getExecutor();

        String searchResult = executor.apply(new MemoryToolkit.ToolCall(
            "memory_search",
            Map.of("query", "小明 编程")
        ));
        System.out.println("\nmemory_search 结果:\n" + searchResult);

        String writeResult = executor.apply(new MemoryToolkit.ToolCall(
            "write_memory",
            Map.of(
                "content", "小明今天完成了会议presentation，表现很好",
                "type", "ephemeral"
            )
        ));
        System.out.println("write_memory 结果: " + writeResult + "\n");
    }

    // ==================== Scenario 6: Full Search Demo ====================

    @Test
    @Order(7)
    @DisplayName("7. Hybrid search demo")
    void testHybridSearch() {
        System.out.println("--- 混合搜索演示 ---\n");

        // Add more content for search
        memoryManager.appendDurable("重要记录", "小明在学习Java编程，目标是成为后端工程师");
        memoryManager.appendEphemeral("今日话题：职业规划、编程学习");

        // Hybrid search (BM25 + Vector)
        List<SearchResult> results = memoryManager.search("编程 职业发展");

        System.out.println("查询: \"编程 职业发展\"");
        System.out.println("搜索结果 (" + results.size() + " 条):\n");

        for (int i = 0; i < Math.min(3, results.size()); i++) {
            SearchResult r = results.get(i);
            System.out.printf("%d. [%s] 得分: %.4f%n", i + 1, r.getChunk().getSourceFile(), r.getScore());
            System.out.println("   " + truncate(r.getSnippet(), 60) + "\n");
        }
    }

    // ==================== Final Summary ====================

    @Test
    @Order(8)
    @DisplayName("8. System status summary")
    void testSystemStatus() {
        System.out.println("========================================");
        System.out.println("           系统状态总结");
        System.out.println("========================================\n");

        var stats = memoryManager.getIndexStats();
        System.out.println("索引统计:");
        System.out.println("  - BM25 索引块数: " + stats.bm25ChunkCount());
        System.out.println("  - 向量索引块数: " + stats.vectorChunkCount());
        System.out.println("  - 向量维度: " + stats.embeddingDimensions());

        System.out.println("\n会话列表:");
        memoryManager.listSessions().forEach(s -> System.out.println("  - " + s));

        System.out.println("\n持久记忆 (MEMORY.md):");
        String durable = memoryManager.readDurable();
        System.out.println(durable.isEmpty() ? "  [空]" : indent(durable, "  "));

        System.out.println("\n今日记忆:");
        String ephemeral = memoryManager.readTodayEphemeral();
        System.out.println(ephemeral.isEmpty() ? "  [空]" : indent(truncate(ephemeral, 200), "  "));

        System.out.println("\n========================================\n");
    }

    // Helper methods

    private void simulateProcessing(String user, String msg, long delayMs) {
        try {
            Thread.sleep(delayMs);
            System.out.println("    [" + user + "] 处理完成: " + msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        s = s.replace("\n", " ");
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private String indent(String s, String prefix) {
        return prefix + s.replace("\n", "\n" + prefix);
    }
}
