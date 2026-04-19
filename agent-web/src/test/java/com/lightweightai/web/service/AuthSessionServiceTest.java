package com.lightweightai.web.service;

import com.lightweightai.web.service.AuthSessionService.SessionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthSessionService - 会话令牌管理")
class AuthSessionServiceTest {

    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        service = new AuthSessionService();
    }

    @Nested
    @DisplayName("Token 创建")
    class TokenCreationTests {

        @Test
        @DisplayName("创建的 token 以 tk- 开头")
        void tokenShouldHavePrefix() {
            String token = service.createSessionToken("user1");
            assertTrue(token.startsWith("tk-"));
        }

        @Test
        @DisplayName("每次创建的 token 唯一")
        void tokensShouldBeUnique() {
            Set<String> tokens = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                tokens.add(service.createSessionToken("user1"));
            }
            assertEquals(100, tokens.size());
        }

        @Test
        @DisplayName("不指定 role 时默认为 USER")
        void shouldDefaultToUserRole() {
            String token = service.createSessionToken("user1");
            Optional<SessionInfo> session = service.resolveSession(token);

            assertTrue(session.isPresent());
            assertEquals("USER", session.get().getRole());
        }

        @Test
        @DisplayName("指定 role 时使用指定的 role")
        void shouldUseSpecifiedRole() {
            String token = service.createSessionToken("admin1", "ADMIN");
            Optional<SessionInfo> session = service.resolveSession(token);

            assertTrue(session.isPresent());
            assertEquals("ADMIN", session.get().getRole());
        }

        @Test
        @DisplayName("role 为 null 时默认为 USER")
        void shouldDefaultRoleWhenNull() {
            String token = service.createSessionToken("user1", null);
            Optional<SessionInfo> session = service.resolveSession(token);

            assertTrue(session.isPresent());
            assertEquals("USER", session.get().getRole());
        }
    }

    @Nested
    @DisplayName("Token 解析")
    class TokenResolutionTests {

        @Test
        @DisplayName("resolveUserId 返回正确的 userId")
        void shouldResolveUserId() {
            String token = service.createSessionToken("user42");
            Optional<String> userId = service.resolveUserId(token);

            assertTrue(userId.isPresent());
            assertEquals("user42", userId.get());
        }

        @Test
        @DisplayName("resolveSession 返回完整的 SessionInfo")
        void shouldResolveSession() {
            String token = service.createSessionToken("user1", "ADMIN");
            Optional<SessionInfo> session = service.resolveSession(token);

            assertTrue(session.isPresent());
            assertEquals("user1", session.get().getUserId());
            assertEquals("ADMIN", session.get().getRole());
        }

        @Test
        @DisplayName("未知 token 返回空 Optional")
        void shouldReturnEmptyForUnknownToken() {
            assertTrue(service.resolveUserId("unknown-token").isEmpty());
            assertTrue(service.resolveSession("unknown-token").isEmpty());
        }

        @Test
        @DisplayName("null token 返回空 Optional")
        void shouldReturnEmptyForNullToken() {
            assertTrue(service.resolveUserId(null).isEmpty());
            assertTrue(service.resolveSession(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("Token 注销")
    class TokenInvalidationTests {

        @Test
        @DisplayName("invalidate 后 token 不可用")
        void shouldInvalidateToken() {
            String token = service.createSessionToken("user1");
            assertTrue(service.resolveUserId(token).isPresent());

            service.invalidate(token);
            assertTrue(service.resolveUserId(token).isEmpty());
        }

        @Test
        @DisplayName("invalidate 不存在的 token 不抛异常")
        void shouldNotThrowForUnknownToken() {
            assertDoesNotThrow(() -> service.invalidate("nonexistent"));
        }

        @Test
        @DisplayName("invalidate 一个 token 不影响其他 token")
        void shouldNotAffectOtherTokens() {
            String token1 = service.createSessionToken("user1");
            String token2 = service.createSessionToken("user2");

            service.invalidate(token1);

            assertTrue(service.resolveUserId(token1).isEmpty());
            assertTrue(service.resolveUserId(token2).isPresent());
        }
    }

    @Nested
    @DisplayName("多用户场景")
    class MultiUserTests {

        @Test
        @DisplayName("同一用户可以有多个 session")
        void sameUserMultipleSessions() {
            String token1 = service.createSessionToken("user1");
            String token2 = service.createSessionToken("user1");

            assertNotEquals(token1, token2);
            assertEquals("user1", service.resolveUserId(token1).orElse(null));
            assertEquals("user1", service.resolveUserId(token2).orElse(null));
        }
    }
}
