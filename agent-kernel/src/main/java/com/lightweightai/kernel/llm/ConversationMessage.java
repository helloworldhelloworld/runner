package com.lightweightai.kernel.llm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unified message abstraction - model-agnostic conversation message.
 * Supports multimodal content, tool calls, and metadata.
 */
public class ConversationMessage {

    private final String id;
    private final MessageRole role;
    private final Instant timestamp;
    private final List<ContentBlock> content;
    private final Map<String, Object> metadata;

    public enum MessageRole {
        USER,
        ASSISTANT,
        SYSTEM,
        TOOL
    }

    private ConversationMessage(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.role = builder.role;
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.content = new ArrayList<>(builder.content);
        this.metadata = new HashMap<>(builder.metadata);
    }

    public String getId() {
        return id;
    }

    public MessageRole getRole() {
        return role;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public List<ContentBlock> getContent() {
        return new ArrayList<>(content);
    }

    public Map<String, Object> getMetadata() {
        return new HashMap<>(metadata);
    }

    /**
     * Convenience method to get text content when message contains single text block
     */
    public String getTextContent() {
        if (content.size() == 1 && content.get(0) instanceof TextContent) {
            return ((TextContent) content.get(0)).getText();
        }

        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof TextContent) {
                sb.append(((TextContent) block).getText());
            }
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private MessageRole role;
        private Instant timestamp;
        private List<ContentBlock> content = new ArrayList<>();
        private Map<String, Object> metadata = new HashMap<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder role(MessageRole role) {
            this.role = role;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder content(List<ContentBlock> content) {
            this.content = new ArrayList<>(content);
            return this;
        }

        public Builder addContent(ContentBlock block) {
            this.content.add(block);
            return this;
        }

        public Builder textContent(String text) {
            this.content.add(new TextContent(text));
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public ConversationMessage build() {
            if (role == null) {
                throw new IllegalStateException("Role is required");
            }
            if (content.isEmpty()) {
                throw new IllegalStateException("Content is required");
            }
            return new ConversationMessage(this);
        }
    }
}
