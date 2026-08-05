package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConversationMessage 全路径覆盖测试。
 *
 * 已有 ConversationMessageTest 覆盖基本构建。
 * 此测试补充：
 * - builder 校验（role/content required）
 * - getTextContent 多 block 拼接
 * - metadata 隔离（返回 copy）
 * - content 隔离（返回 copy）
 * - 自动 id/timestamp 生成
 */
@DisplayName("ConversationMessage - 全路径覆盖")
class ConversationMessageFullTest {

    @Nested
    @DisplayName("Builder 校验")
    class BuilderValidation {

        @Test
        @DisplayName("无 role 时抛 IllegalStateException")
        void noRoleThrows() {
            assertThrows(IllegalStateException.class, () ->
                    ConversationMessage.builder().textContent("hello").build());
        }

        @Test
        @DisplayName("无 content 时抛 IllegalStateException")
        void noContentThrows() {
            assertThrows(IllegalStateException.class, () ->
                    ConversationMessage.builder().role(ConversationMessage.MessageRole.USER).build());
        }
    }

    @Nested
    @DisplayName("自动生成字段")
    class AutoGeneration {

        @Test
        @DisplayName("未指定 id 时自动生成 UUID")
        void autoGeneratesId() {
            ConversationMessage msg = ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent("hi")
                    .build();
            assertNotNull(msg.getId());
            assertFalse(msg.getId().isEmpty());
        }

        @Test
        @DisplayName("指定 id 时使用指定值")
        void usesSpecifiedId() {
            ConversationMessage msg = ConversationMessage.builder()
                    .id("custom-id")
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent("hi")
                    .build();
            assertEquals("custom-id", msg.getId());
        }

        @Test
        @DisplayName("未指定 timestamp 时自动生成")
        void autoGeneratesTimestamp() {
            Instant before = Instant.now();
            ConversationMessage msg = ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent("hi")
                    .build();
            Instant after = Instant.now();

            assertNotNull(msg.getTimestamp());
            assertFalse(msg.getTimestamp().isBefore(before));
            assertFalse(msg.getTimestamp().isAfter(after));
        }
    }

    @Nested
    @DisplayName("getTextContent")
    class GetTextContent {

        @Test
        @DisplayName("单 TextContent block 返回文本")
        void singleTextBlock() {
            ConversationMessage msg = ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent("hello world")
                    .build();
            assertEquals("hello world", msg.getTextContent());
        }

        @Test
        @DisplayName("多 TextContent block 拼接")
        void multipleTextBlocks() {
            ConversationMessage msg = ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.ASSISTANT)
                    .addContent(new TextContent("Hello "))
                    .addContent(new TextContent("World"))
                    .build();
            assertEquals("Hello World", msg.getTextContent());
        }
    }

    @Nested
    @DisplayName("集合隔离")
    class CollectionIsolation {

        @Test
        @DisplayName("getContent 返回防御性副本")
        void contentReturnsCopy() {
            ConversationMessage msg = ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent("test")
                    .build();

            List<ContentBlock> content1 = msg.getContent();
            List<ContentBlock> content2 = msg.getContent();
            assertNotSame(content1, content2, "Each call should return a new copy");
        }

        @Test
        @DisplayName("getMetadata 返回防御性副本")
        void metadataReturnsCopy() {
            ConversationMessage msg = ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent("test")
                    .addMetadata("key", "value")
                    .build();

            Map<String, Object> meta1 = msg.getMetadata();
            Map<String, Object> meta2 = msg.getMetadata();
            assertNotSame(meta1, meta2, "Each call should return a new copy");
            assertEquals("value", meta1.get("key"));
        }
    }

    @Nested
    @DisplayName("metadata builder")
    class MetadataBuilder {

        @Test
        @DisplayName("addMetadata 逐条添加")
        void addMetadataIncrementally() {
            ConversationMessage msg = ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.TOOL)
                    .textContent("result")
                    .addMetadata("tool_use_id", "use-1")
                    .addMetadata("is_error", false)
                    .build();

            assertEquals("use-1", msg.getMetadata().get("tool_use_id"));
            assertEquals(false, msg.getMetadata().get("is_error"));
        }

        @Test
        @DisplayName("metadata(Map) 批量设置")
        void metadataBatchSet() {
            ConversationMessage msg = ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.SYSTEM)
                    .textContent("instructions")
                    .metadata(Map.of("source", "config", "priority", 1))
                    .build();

            assertEquals("config", msg.getMetadata().get("source"));
            assertEquals(1, msg.getMetadata().get("priority"));
        }
    }

    @Nested
    @DisplayName("所有 MessageRole 值")
    class AllRoles {

        @Test
        @DisplayName("四种 role 都能正常构建")
        void allRolesWork() {
            for (ConversationMessage.MessageRole role : ConversationMessage.MessageRole.values()) {
                ConversationMessage msg = ConversationMessage.builder()
                        .role(role)
                        .textContent("test")
                        .build();
                assertEquals(role, msg.getRole());
            }
        }
    }
}
