package com.lightweightai.web.config;

import com.lightweightai.kernel.memory.embedding.EmbeddingProvider;
import com.lightweightai.kernel.memory.embedding.MockEmbeddingProvider;
import com.lightweightai.kernel.memory.embedding.OpenAIEmbeddingProvider;
import com.lightweightai.kernel.memory.file.FileMemoryManager;
import com.lightweightai.kernel.memory.queue.LaneQueueManager;
import com.lightweightai.kernel.memory.tools.MemoryToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for OpenClaw-style memory system.
 *
 * Provides:
 * - FileMemoryManager: Three-tier memory (Ephemeral/Durable/Session)
 * - LaneQueueManager: Serial execution per session
 * - MemoryToolkit: memory_search / write_memory tools for AI
 */
@Configuration
public class MemoryConfig {

    private static final Logger logger = LoggerFactory.getLogger(MemoryConfig.class);

    @Value("${app.memory.root:#{systemProperties['user.home'] + '/.lightweightai/agents'}}")
    private String memoryRoot;

    @Value("${app.memory.agent-id:soul-comfort}")
    private String agentId;

    @Value("${app.memory.embedding.provider:mock}")
    private String embeddingProvider;

    @Value("${app.memory.embedding.openai-key:}")
    private String openaiEmbeddingKey;

    @Value("${app.memory.embedding.dimensions:128}")
    private int embeddingDimensions;

    private FileMemoryManager fileMemoryManager;
    private LaneQueueManager laneQueueManager;

    @Bean
    public EmbeddingProvider embeddingProvider() {
        switch (embeddingProvider.toLowerCase()) {
            case "openai":
                if (openaiEmbeddingKey == null || openaiEmbeddingKey.isEmpty()) {
                    logger.warn("OpenAI Embedding key not configured, falling back to Mock");
                    return new MockEmbeddingProvider(embeddingDimensions);
                }
                logger.info("Using OpenAI Embedding Provider (text-embedding-3-small)");
                return new OpenAIEmbeddingProvider(openaiEmbeddingKey);

            case "mock":
            default:
                logger.info("Using Mock Embedding Provider (dimensions: {})", embeddingDimensions);
                return new MockEmbeddingProvider(embeddingDimensions);
        }
    }

    @Bean
    public FileMemoryManager fileMemoryManager(EmbeddingProvider embeddingProvider) {
        Path agentRoot = Paths.get(memoryRoot, agentId);
        logger.info("Initializing FileMemoryManager at: {}", agentRoot);

        this.fileMemoryManager = new FileMemoryManager(agentRoot, agentId, embeddingProvider);

        FileMemoryManager.IndexStats stats = fileMemoryManager.getIndexStats();
        logger.info("Memory system ready - BM25 chunks: {}, Vector chunks: {}",
            stats.bm25ChunkCount(), stats.vectorChunkCount());

        return fileMemoryManager;
    }

    @Bean
    public LaneQueueManager laneQueueManager() {
        this.laneQueueManager = new LaneQueueManager();
        logger.info("LaneQueueManager initialized");
        return laneQueueManager;
    }

    @Bean
    public MemoryToolkit memoryToolkit(FileMemoryManager fileMemoryManager) {
        MemoryToolkit toolkit = MemoryToolkit.create(fileMemoryManager);
        logger.info("MemoryToolkit initialized with tools: memory_search, write_memory");
        return toolkit;
    }

    @PreDestroy
    public void cleanup() {
        logger.info("Cleaning up memory system...");
        if (fileMemoryManager != null) {
            fileMemoryManager.close();
        }
        if (laneQueueManager != null) {
            laneQueueManager.close();
        }
    }
}
