package com.lightweightai.kernel.memory;

import com.lightweightai.kernel.llm.ConversationMessage;
import java.time.Instant;
import java.util.Objects;

/**
 * 统一的消息模型
 *
 * 用于会话历史存储，兼容多种 LLM Provider。
 */
public class Message {

    private final String role;
    private final String content;
    private final Instant timestamp;

    private Message(String role, String content) {
        this.role = Objects.requireNonNull(role, "role cannot be null");
        this.content = Objects.requireNonNull(content, "content cannot be null");
        this.timestamp = Instant.now();
    }

    // ==================== 工厂方法 ====================

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

    public static Message system(String content) {
        return new Message("system", content);
    }

    public static Message of(String role, String content) {
        return new Message(role, content);
    }

    // ==================== Getters ====================

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    // ==================== 转换方法 ====================

    /**
     * 转换为 ConversationMessage（兼容现有 LLM 接口）
     */
    public ConversationMessage toConversationMessage() {
        ConversationMessage.MessageRole msgRole = switch (role) {
            case "user" -> ConversationMessage.MessageRole.USER;
            case "assistant" -> ConversationMessage.MessageRole.ASSISTANT;
            case "system" -> ConversationMessage.MessageRole.SYSTEM;
            default -> ConversationMessage.MessageRole.USER;
        };

        return ConversationMessage.builder()
            .role(msgRole)
            .textContent(content)
            .build();
    }

    /**
     * 从 ConversationMessage 创建
     */
    public static Message fromConversationMessage(ConversationMessage msg) {
        String role = switch (msg.getRole()) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            case TOOL -> "tool";
        };
        return new Message(role, msg.getTextContent());
    }

    @Override
    public String toString() {
        return String.format("Message{role='%s', content='%s'}", role,
            content.length() > 50 ? content.substring(0, 50) + "..." : content);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Message message = (Message) o;
        return role.equals(message.role) && content.equals(message.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content);
    }
}
