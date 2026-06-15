package com.lightweightai.web.config;

import com.lightweightai.kernel.agent.RiskLevel;
import com.lightweightai.kernel.llm.ToolResult;
import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.tools.MemoryToolkit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 守护"minion 能写长期记忆"——write_memory 适配器把内容真的落进 durable 记忆（治"名字记不住"）。
 * 内核侧"工具到达 LLM / 风险闸放行"由 MinionToolWiringAcceptanceTest 守护；本测试守护**写入真生效**。
 */
class MemoryWriteToolTest {

    private MemoryWriteTool toolOver(Path dir) {
        FileMemoryManager mem = new FileMemoryManager(dir, "t", new MockEmbeddingProvider(128));
        return new MemoryWriteTool(MemoryToolkit.create(mem));
    }

    @Test
    @DisplayName("write_memory(type=durable) 真写进长期记忆，可读回")
    void durableWritePersists(@TempDir Path dir) {
        FileMemoryManager mem = new FileMemoryManager(dir, "t", new MockEmbeddingProvider(128));
        MemoryWriteTool tool = new MemoryWriteTool(MemoryToolkit.create(mem));
        try {
            ToolResult r = tool.execute(Map.of(
                "content", "用户的名字叫小杰",
                "type", "durable",
                "section", "用户信息"));

            assertNotNull(r);
            assertTrue(mem.readDurable().contains("小杰"),
                "长期记忆应包含写入内容（这条断了名字就记不住）");
        } finally {
            mem.close();
        }
    }

    @Test
    @DisplayName("契约稳定：name=write_memory、风险 WRITE、schema 必填 content")
    void contractIsStable(@TempDir Path dir) {
        MemoryWriteTool tool = toolOver(dir);
        assertEquals("write_memory", tool.getName());
        assertEquals(RiskLevel.WRITE, tool.riskLevel());
        assertTrue(tool.getSchema().getProperties().containsKey("content"));
    }
}
