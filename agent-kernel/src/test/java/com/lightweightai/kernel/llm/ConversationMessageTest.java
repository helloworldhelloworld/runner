package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConversationMessage - 统一消息抽象")
class ConversationMessageTest {

    @Test
    @DisplayName("构建基本消息")
    void buildBasicMessage() {
        ConversationMessage msg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent("Hello")
            .build();

        assertEquals(ConversationMessage.MessageRole.USER, msg.getRole());
        assertNotNull(msg.getId());
        assertNotNull(msg.getTimestamp());
        assertEquals("Hello", msg.getTextContent());
    }

    @Test
    @DisplayName("指定 ID 和时间戳")
    void customIdAndTimestamp() {
        Instant ts = Instant.parse("2025-01-01T00:00:00Z");
        ConversationMessage msg = ConversationMessage.builder()
            .id("custom-id")
            .role(ConversationMessage.MessageRole.ASSISTANT)
            .timestamp(ts)
            .textContent("Response")
            .build();

        assertEquals("custom-id", msg.getId());
        assertEquals(ts, msg.getTimestamp());
    }

    @Test
    @DisplayName("缺少 role 抛异常")
    void missingRoleThrows() {
        assertThrows(IllegalStateException.class, () ->
            ConversationMessage.builder()
                .textContent("no role")
                .build()
        );
    }

    @Test
    @DisplayName("缺少 content 抛异常")
    void missingContentThrows() {
        assertThrows(IllegalStateException.class, () ->
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .build()
        );
    }

    @Test
    @DisplayName("多个文本块拼接")
    void multipleTextBlocks() {
        ConversationMessage msg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent("Hello ")
            .addContent(new TextContent("World"))
            .build();

        assertEquals("Hello World", msg.getTextContent());
    }

    @Test
    @DisplayName("设置内容列表")
    void setContentList() {
        List<ContentBlock> blocks = List.of(new TextContent("A"), new TextContent("B"));
        ConversationMessage msg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.SYSTEM)
            .content(blocks)
            .build();

        assertEquals(2, msg.getContent().size());
        assertEquals("AB", msg.getTextContent());
    }

    @Test
    @DisplayName("metadata 操作")
    void metadataOperations() {
        ConversationMessage msg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent("test")
            .addMetadata("key1", "val1")
            .metadata(Map.of("key2", "val2"))
            .build();

        // metadata(Map) replaces previous metadata
        Map<String, Object> meta = msg.getMetadata();
        assertEquals("val2", meta.get("key2"));
    }

    @Test
    @DisplayName("getContent 返回防御性拷贝")
    void contentDefensiveCopy() {
        ConversationMessage msg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER)
            .textContent("test")
            .build();

        List<ContentBlock> content = msg.getContent();
        content.clear();
        // 原始消息不受影响
        assertEquals(1, msg.getContent().size());
    }

    @Test
    @DisplayName("getMetadata 返回防御性拷贝")
    void metadataDefensiveCopy() {
        ConversationMessage msg = ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.TOOL)
            .textContent("result")
            .addMetadata("k", "v")
            .build();

        Map<String, Object> meta = msg.getMetadata();
        meta.clear();
        assertEquals(1, msg.getMetadata().size());
    }

    @Test
    @DisplayName("所有 MessageRole 枚举值")
    void allRoles() {
        assertEquals(4, ConversationMessage.MessageRole.values().length);
        assertNotNull(ConversationMessage.MessageRole.valueOf("USER"));
        assertNotNull(ConversationMessage.MessageRole.valueOf("ASSISTANT"));
        assertNotNull(ConversationMessage.MessageRole.valueOf("SYSTEM"));
        assertNotNull(ConversationMessage.MessageRole.valueOf("TOOL"));
    }
}
