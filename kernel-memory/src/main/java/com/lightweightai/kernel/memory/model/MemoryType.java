package com.lightweightai.kernel.memory.model;

/**
 * Types of memory in the three-tier memory system.
 * Inspired by OpenClaw's memory architecture.
 */
public enum MemoryType {
    /**
     * Ephemeral memory - daily logs that are append-only.
     * Stored in memory/YYYY-MM-DD.md files.
     */
    EPHEMERAL,

    /**
     * Durable memory - curated long-term knowledge.
     * Stored in MEMORY.md file.
     */
    DURABLE,

    /**
     * Session memory - conversation transcripts.
     * Stored in sessions/*.jsonl files.
     */
    SESSION
}
