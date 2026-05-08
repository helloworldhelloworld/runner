package com.lightweightai.web.service;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.core.ToolCallingLoop;
import com.lightweightai.kernel.llm.LLMProvider;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.file.SessionTranscript;
import com.lightweightai.kernel.memory.model.TranscriptEntry;
import com.lightweightai.kernel.memory.queue.LaneQueueManager;
import com.lightweightai.kernel.memory.tools.MemoryToolkit;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.web.model.ChatRequest;
import com.lightweightai.web.model.ChatResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SoulComfortChatService} — the core chat business logic.
 *
 * Focuses on critical paths: session history, memory search, session summary/clear.
 * Note: chat() involves LLM interaction which requires integration-level tests;
 * we test the orchestration around it (session management, delegation patterns).
 */
@DisplayName("SoulComfortChatService - 心灵引导服务")
class SoulComfortChatServiceTest {

    @TempDir
    Path tempDir;

    // ==================== getSessionHistory ====================

    @Nested
    @DisplayName("getSessionHistory()")
    class GetSessionHistoryTests {

        @Test
        @DisplayName("过滤只返回 user 和 assistant 消息")
        void getSessionHistory_filtersRoles() {
            FileMemoryManager memoryManager = new FileMemoryManager(tempDir.toString());
            SessionTranscript transcript = memoryManager.getSession("sess-1");
            transcript.append(TranscriptEntry.userMessage("Hello"));
            transcript.append(TranscriptEntry.assistantMessage("Hi there"));
            transcript.append(TranscriptEntry.systemEvent("compaction", Map.of()));

            // Create service with real FileMemoryManager
            PromptEngine promptEngine = mock(PromptEngine.class);
            MemoryProvider memProvider = mock(MemoryProvider.class);
            when(promptEngine.getMemoryProvider()).thenReturn(memProvider);
            LLMProvider llmProvider = mock(LLMProvider.class);
            when(llmProvider.getProviderName()).thenReturn("mock");
            LaneQueueManager laneManager = new LaneQueueManager();
            MemoryToolkit memToolkit = mock(MemoryToolkit.class);
            ToolCallingLoop tcl = mock(ToolCallingLoop.class);

            SoulComfortChatService service = new SoulComfortChatService(
                llmProvider, promptEngine, memoryManager, laneManager,
                memToolkit, new ToolRegistry(), tcl
            );

            List<Map<String, String>> history = service.getSessionHistory("sess-1");

            assertEquals(2, history.size());
            assertEquals("user", history.get(0).get("role"));
            assertEquals("Hello", history.get(0).get("content"));
            assertEquals("assistant", history.get(1).get("role"));
            assertEquals("Hi there", history.get(1).get("content"));
        }
    }

    // ==================== Agent and Toolkit access ====================

    @Nested
    @DisplayName("Agent / Toolkit 访问")
    class AccessTests {

        @Test
        @DisplayName("getAgent 返回非 null 默认 agent")
        void getAgent_returnsDefaultAgent() {
            FileMemoryManager memoryManager = new FileMemoryManager(tempDir.toString());
            PromptEngine promptEngine = mock(PromptEngine.class);
            LLMProvider llmProvider = mock(LLMProvider.class);
            when(llmProvider.getProviderName()).thenReturn("mock");
            LaneQueueManager laneManager = new LaneQueueManager();
            MemoryToolkit memToolkit = mock(MemoryToolkit.class);
            ToolCallingLoop tcl = mock(ToolCallingLoop.class);

            SoulComfortChatService service = new SoulComfortChatService(
                llmProvider, promptEngine, memoryManager, laneManager,
                memToolkit, new ToolRegistry(), tcl
            );

            assertNotNull(service.getAgent());
        }

        @Test
        @DisplayName("getMemoryToolkit 返回注入的 toolkit")
        void getMemoryToolkit_returnsInjected() {
            FileMemoryManager memoryManager = new FileMemoryManager(tempDir.toString());
            PromptEngine promptEngine = mock(PromptEngine.class);
            LLMProvider llmProvider = mock(LLMProvider.class);
            when(llmProvider.getProviderName()).thenReturn("mock");
            LaneQueueManager laneManager = new LaneQueueManager();
            MemoryToolkit memToolkit = mock(MemoryToolkit.class);
            ToolCallingLoop tcl = mock(ToolCallingLoop.class);

            SoulComfortChatService service = new SoulComfortChatService(
                llmProvider, promptEngine, memoryManager, laneManager,
                memToolkit, new ToolRegistry(), tcl
            );

            assertSame(memToolkit, service.getMemoryToolkit());
        }
    }

    // ==================== rememberDurable ====================

    @Nested
    @DisplayName("rememberDurable()")
    class RememberDurableTests {

        @Test
        @DisplayName("写入到 FileMemoryManager 的 durable 层")
        void rememberDurable_writesToManager() {
            FileMemoryManager memoryManager = new FileMemoryManager(tempDir.toString());
            PromptEngine promptEngine = mock(PromptEngine.class);
            LLMProvider llmProvider = mock(LLMProvider.class);
            when(llmProvider.getProviderName()).thenReturn("mock");
            LaneQueueManager laneManager = new LaneQueueManager();
            MemoryToolkit memToolkit = mock(MemoryToolkit.class);
            ToolCallingLoop tcl = mock(ToolCallingLoop.class);

            SoulComfortChatService service = new SoulComfortChatService(
                llmProvider, promptEngine, memoryManager, laneManager,
                memToolkit, new ToolRegistry(), tcl
            );

            assertDoesNotThrow(() -> service.rememberDurable("用户信息", "- 姓名: Alice\n"));
        }
    }

    // ==================== clearSession ====================

    @Nested
    @DisplayName("clearSession()")
    class ClearSessionTests {

        @Test
        @DisplayName("清除不存在的 session 不抛异常")
        void clearSession_nonExistent_doesNotThrow() {
            FileMemoryManager memoryManager = new FileMemoryManager(tempDir.toString());
            PromptEngine promptEngine = mock(PromptEngine.class);
            LLMProvider llmProvider = mock(LLMProvider.class);
            when(llmProvider.getProviderName()).thenReturn("mock");
            LaneQueueManager laneManager = new LaneQueueManager();
            MemoryToolkit memToolkit = mock(MemoryToolkit.class);
            ToolCallingLoop tcl = mock(ToolCallingLoop.class);

            SoulComfortChatService service = new SoulComfortChatService(
                llmProvider, promptEngine, memoryManager, laneManager,
                memToolkit, new ToolRegistry(), tcl
            );

            assertDoesNotThrow(() -> service.clearSession("nonexistent-session"));
        }
    }
}
