package com.lightweightai.kernel.memory;

import com.lightweightai.kernel.llm.ConversationMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable snapshot of conversation state
 */
public class ConversationSnapshot {

    private final String conversationId;
    private final List<ConversationMessage> messages;
    private final Instant timestamp;

    public ConversationSnapshot(String conversationId, List<ConversationMessage> messages) {
        this.conversationId = conversationId;
        this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
        this.timestamp = Instant.now();
    }

    public String getConversationId() {
        return conversationId;
    }

    public List<ConversationMessage> getMessages() {
        return messages;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
