package com.lightweightai.kernel.memory;

import java.util.List;
import java.util.Optional;

/**
 * Storage interface for persisting conversation contexts.
 * Implementations: in-memory, Redis, PostgreSQL, file-based, etc.
 */
public interface ContextStorage {

    /**
     * Save a conversation snapshot
     *
     * @param conversationId Conversation identifier
     * @param snapshot       Snapshot to save
     */
    void save(String conversationId, ConversationSnapshot snapshot);

    /**
     * Load a conversation snapshot
     *
     * @param conversationId Conversation identifier
     * @return Optional containing snapshot if found
     */
    Optional<ConversationSnapshot> load(String conversationId);

    /**
     * Delete a conversation
     *
     * @param conversationId Conversation identifier
     * @return true if deleted
     */
    boolean delete(String conversationId);

    /**
     * List all conversation IDs
     *
     * @return List of conversation IDs
     */
    List<String> listConversations();

    /**
     * Check if a conversation exists
     *
     * @param conversationId Conversation identifier
     * @return true if exists
     */
    default boolean exists(String conversationId) {
        return load(conversationId).isPresent();
    }
}
