package com.lightweightai.kernel.agent.directive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultDirectiveManager - 默认指令编排器")
class DefaultDirectiveManagerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private DefaultDirectiveManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultDirectiveManager();
    }

    // ==================== 基本序列化 ====================

    @Nested
    @DisplayName("基本指令序列化")
    class BasicSerialization {

        @Test
        @DisplayName("主指令序列化为合法 JSON")
        void shouldSerializePrimaryDirective() throws Exception {
            Directive primary = new Directive("d-1", "Camera", "TakePhoto",
                Map.of("resolution", "1080p"), 30_000);

            String json = manager.getResult("req-1", primary);

            assertNotNull(json);
            JsonNode root = MAPPER.readTree(json);
            assertNotNull(root);
            assertTrue(json.contains("req-1"));
        }

        @Test
        @DisplayName("JSON 中包含 directive 数据")
        void shouldContainDirectiveData() throws Exception {
            Directive primary = new Directive("d-1", "GPS", "GetLocation",
                Map.of("accuracy", "high"), 10_000);

            String json = manager.getResult("req-2", primary);
            MAPPER.readTree(json); // ensure valid JSON

            assertTrue(json.contains("GPS"));
            assertTrue(json.contains("GetLocation"));
        }

        @Test
        @DisplayName("primary 为 null 应抛异常")
        void shouldThrowOnNullPrimary() {
            assertThrows(NullPointerException.class, () ->
                manager.getResult("req-1", null));
        }
    }

    // ==================== CommonDirectiveProvider ====================

    @Nested
    @DisplayName("CommonDirectiveProvider 支持")
    class CommonProviderTests {

        @Test
        @DisplayName("注入 common directive 后 JSON 包含多个指令")
        void shouldIncludeCommonDirectives() throws Exception {
            manager.addCommonProvider(primary ->
                new Directive("common-1", "System", "Heartbeat", Map.of(), 5000));

            Directive primary = new Directive("d-1", "Camera", "TakePhoto",
                Map.of(), 30_000);

            String json = manager.getResult("req-1", primary);

            assertTrue(json.contains("Camera"));
            assertTrue(json.contains("Heartbeat"));
        }

        @Test
        @DisplayName("common provider 返回 null 时忽略")
        void shouldSkipNullCommonDirective() throws Exception {
            manager.addCommonProvider(primary -> null);

            Directive primary = new Directive("d-1", "Camera", "TakePhoto",
                Map.of(), 30_000);

            String json = manager.getResult("req-1", primary);

            assertNotNull(json);
            assertTrue(json.contains("Camera"));
        }

        @Test
        @DisplayName("多个 common provider 按顺序执行")
        void shouldAddMultipleCommonProviders() throws Exception {
            manager.addCommonProvider(primary ->
                new Directive("c-1", "System", "Log", Map.of(), 1000));
            manager.addCommonProvider(primary ->
                new Directive("c-2", "System", "Track", Map.of(), 1000));

            Directive primary = new Directive("d-1", "Camera", "TakePhoto",
                Map.of(), 30_000);

            String json = manager.getResult("req-1", primary);

            assertTrue(json.contains("Log"));
            assertTrue(json.contains("Track"));
        }

        @Test
        @DisplayName("addCommonProvider null 应抛异常")
        void shouldThrowOnNullProvider() {
            assertThrows(NullPointerException.class, () ->
                manager.addCommonProvider(null));
        }
    }
}
