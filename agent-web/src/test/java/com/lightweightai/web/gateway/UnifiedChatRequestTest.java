package com.lightweightai.web.gateway;

import com.lightweightai.web.gateway.UnifiedChatRequest.ClientType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UnifiedChatRequest - 统一聊天请求")
class UnifiedChatRequestTest {

    @Nested
    @DisplayName("ClientType 枚举")
    class ClientTypeTests {

        @Test
        @DisplayName("所有客户端类型枚举值存在")
        void allClientTypesShouldExist() {
            assertNotNull(ClientType.WEB);
            assertNotNull(ClientType.IOS);
            assertNotNull(ClientType.ANDROID);
            assertNotNull(ClientType.HARMONYOS);
            assertNotNull(ClientType.MINIPROGRAM);
            assertNotNull(ClientType.CLI);
            assertNotNull(ClientType.UNKNOWN);
        }

        @Test
        @DisplayName("clientType 为 null 时 getClientType 返回 UNKNOWN")
        void shouldDefaultToUnknownWhenNull() {
            UnifiedChatRequest request = new UnifiedChatRequest();
            assertEquals(ClientType.UNKNOWN, request.getClientType());
        }

        @Test
        @DisplayName("设置 clientType 后正确返回")
        void shouldReturnSetClientType() {
            UnifiedChatRequest request = new UnifiedChatRequest();
            request.setClientType(ClientType.IOS);
            assertEquals(ClientType.IOS, request.getClientType());
        }
    }

    @Nested
    @DisplayName("基本字段")
    class BasicFieldTests {

        @Test
        @DisplayName("设置和获取所有基本字段")
        void shouldGetAndSetAllFields() {
            UnifiedChatRequest request = new UnifiedChatRequest();
            request.setSessionId("session-123");
            request.setMessage("Hello AI");
            request.setStream(true);
            request.setClientType(ClientType.WEB);
            request.setModel("claude-3-5-sonnet");

            assertEquals("session-123", request.getSessionId());
            assertEquals("Hello AI", request.getMessage());
            assertTrue(request.isStream());
            assertEquals(ClientType.WEB, request.getClientType());
            assertEquals("claude-3-5-sonnet", request.getModel());
        }

        @Test
        @DisplayName("stream 默认为 false")
        void streamDefaultsFalse() {
            UnifiedChatRequest request = new UnifiedChatRequest();
            assertFalse(request.isStream());
        }
    }

    @Nested
    @DisplayName("Extra 参数")
    class ExtraParameterTests {

        @Test
        @DisplayName("获取存在的 extra 值")
        void shouldGetExtraValue() {
            UnifiedChatRequest request = new UnifiedChatRequest();
            request.setExtra(Map.of("temperature", 0.7, "userId", "u1"));

            assertEquals(0.7, request.<Double>getExtra("temperature", 0.0));
            assertEquals("u1", request.<String>getExtra("userId", ""));
        }

        @Test
        @DisplayName("获取不存在的 extra 返回默认值")
        void shouldReturnDefaultForMissingExtra() {
            UnifiedChatRequest request = new UnifiedChatRequest();
            request.setExtra(Map.of("a", 1));

            assertEquals("fallback", request.getExtra("missing", "fallback"));
        }

        @Test
        @DisplayName("extra 为 null 时返回默认值")
        void shouldReturnDefaultWhenExtraIsNull() {
            UnifiedChatRequest request = new UnifiedChatRequest();
            assertEquals(42, request.<Integer>getExtra("key", 42));
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTests {

        @Test
        @DisplayName("toString 包含关键信息")
        void shouldContainKeyInfo() {
            UnifiedChatRequest request = new UnifiedChatRequest();
            request.setSessionId("s1");
            request.setClientType(ClientType.ANDROID);
            request.setStream(true);

            String str = request.toString();
            assertTrue(str.contains("s1"));
            assertTrue(str.contains("ANDROID"));
            assertTrue(str.contains("true"));
        }
    }
}
