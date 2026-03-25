package com.lightweightai.kernel.memory;

import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.llm.ConversationMessage.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Message - 统一消息模型")
class MessageTest {

    @Nested
    @DisplayName("工厂方法")
    class FactoryMethods {

        @Test
        @DisplayName("user() 创建用户消息")
        void userMessage() {
            Message msg = Message.user("hello");
            assertEquals("user", msg.getRole());
            assertEquals("hello", msg.getContent());
            assertNotNull(msg.getTimestamp());
        }

        @Test
        @DisplayName("assistant() 创建助手消息")
        void assistantMessage() {
            Message msg = Message.assistant("response");
            assertEquals("assistant", msg.getRole());
            assertEquals("response", msg.getContent());
        }

        @Test
        @DisplayName("system() 创建系统消息")
        void systemMessage() {
            Message msg = Message.system("prompt");
            assertEquals("system", msg.getRole());
            assertEquals("prompt", msg.getContent());
        }

        @Test
        @DisplayName("of() 创建自定义角色消息")
        void ofMessage() {
            Message msg = Message.of("tool", "result");
            assertEquals("tool", msg.getRole());
            assertEquals("result", msg.getContent());
        }
    }

    @Nested
    @DisplayName("空值校验")
    class NullChecks {

        @Test
        @DisplayName("null role 抛出 NullPointerException")
        void nullRoleThrows() {
            assertThrows(NullPointerException.class, () -> Message.of(null, "content"));
        }

        @Test
        @DisplayName("null content 抛出 NullPointerException")
        void nullContentThrows() {
            assertThrows(NullPointerException.class, () -> Message.of("user", null));
        }
    }

    @Nested
    @DisplayName("ConversationMessage 转换")
    class Conversion {

        @Test
        @DisplayName("toConversationMessage 转换 user 消息")
        void toConversationMessageUser() {
            Message msg = Message.user("hello");
            ConversationMessage cm = msg.toConversationMessage();

            assertEquals(MessageRole.USER, cm.getRole());
            assertEquals("hello", cm.getTextContent());
        }

        @Test
        @DisplayName("toConversationMessage 转换 assistant 消息")
        void toConversationMessageAssistant() {
            Message msg = Message.assistant("reply");
            ConversationMessage cm = msg.toConversationMessage();

            assertEquals(MessageRole.ASSISTANT, cm.getRole());
            assertEquals("reply", cm.getTextContent());
        }

        @Test
        @DisplayName("toConversationMessage 转换 system 消息")
        void toConversationMessageSystem() {
            Message msg = Message.system("sys");
            ConversationMessage cm = msg.toConversationMessage();

            assertEquals(MessageRole.SYSTEM, cm.getRole());
        }

        @Test
        @DisplayName("toConversationMessage 未知角色默认为 USER")
        void toConversationMessageUnknownRole() {
            Message msg = Message.of("custom", "text");
            ConversationMessage cm = msg.toConversationMessage();

            assertEquals(MessageRole.USER, cm.getRole());
        }

        @Test
        @DisplayName("fromConversationMessage 转换 USER")
        void fromConversationMessageUser() {
            ConversationMessage cm = ConversationMessage.builder()
                .role(MessageRole.USER)
                .textContent("hello")
                .build();

            Message msg = Message.fromConversationMessage(cm);
            assertEquals("user", msg.getRole());
            assertEquals("hello", msg.getContent());
        }

        @Test
        @DisplayName("fromConversationMessage 转换 TOOL")
        void fromConversationMessageTool() {
            ConversationMessage cm = ConversationMessage.builder()
                .role(MessageRole.TOOL)
                .textContent("result")
                .build();

            Message msg = Message.fromConversationMessage(cm);
            assertEquals("tool", msg.getRole());
        }
    }

    @Nested
    @DisplayName("equals 和 hashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("相同 role 和 content 相等")
        void equalMessages() {
            Message m1 = Message.user("hello");
            Message m2 = Message.user("hello");
            assertEquals(m1, m2);
            assertEquals(m1.hashCode(), m2.hashCode());
        }

        @Test
        @DisplayName("不同 content 不相等")
        void differentContent() {
            Message m1 = Message.user("hello");
            Message m2 = Message.user("world");
            assertNotEquals(m1, m2);
        }

        @Test
        @DisplayName("不同 role 不相等")
        void differentRole() {
            Message m1 = Message.user("hello");
            Message m2 = Message.assistant("hello");
            assertNotEquals(m1, m2);
        }

        @Test
        @DisplayName("与 null 不相等")
        void notEqualToNull() {
            assertNotEquals(null, Message.user("hello"));
        }

        @Test
        @DisplayName("与自身相等")
        void equalToSelf() {
            Message m = Message.user("hello");
            assertEquals(m, m);
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("短内容完整显示")
        void shortContent() {
            Message msg = Message.user("short");
            String str = msg.toString();
            assertTrue(str.contains("user"));
            assertTrue(str.contains("short"));
        }

        @Test
        @DisplayName("超过50字符的内容被截断")
        void longContentTruncated() {
            String longText = "a".repeat(60);
            Message msg = Message.user(longText);
            String str = msg.toString();
            assertTrue(str.contains("..."));
            assertFalse(str.contains(longText));
        }

        @Test
        @DisplayName("恰好50字符不截断")
        void exactly50NotTruncated() {
            String text50 = "a".repeat(50);
            Message msg = Message.user(text50);
            String str = msg.toString();
            assertFalse(str.contains("..."));
        }
    }
}
