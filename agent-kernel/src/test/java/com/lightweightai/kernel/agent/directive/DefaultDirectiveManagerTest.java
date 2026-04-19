package com.lightweightai.kernel.agent.directive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultDirectiveManager - 指令编排")
class DefaultDirectiveManagerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private DefaultDirectiveManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultDirectiveManager();
    }

    private Directive createDirective(String id, String namespace, String name) {
        return new Directive(id, namespace, name, Map.of("key", "value"), 30000);
    }

    @Nested
    @DisplayName("基本指令生成")
    class BasicDirectiveTests {

        @Test
        @DisplayName("仅主指令时生成正确 JSON")
        void shouldSerializePrimaryDirective() throws Exception {
            Directive primary = createDirective("d-1", "Camera", "TakePhoto");
            String result = manager.getResult("req-1", primary);

            assertNotNull(result);
            JsonNode json = MAPPER.readTree(result);
            assertNotNull(json);

            JsonNode data = json.get("data");
            assertNotNull(data);
            assertEquals("d-1", data.get("directive_id").asText());

            JsonNode directives = data.get("directives");
            assertEquals(1, directives.size());
            assertEquals("Camera", directives.get(0).get("header").get("namespace").asText());
            assertEquals("TakePhoto", directives.get(0).get("header").get("name").asText());
        }

        @Test
        @DisplayName("primary 为 null 时抛出 NullPointerException")
        void shouldThrowOnNullPrimary() {
            assertThrows(NullPointerException.class,
                    () -> manager.getResult("req-1", null));
        }

        @Test
        @DisplayName("指令 payload 正确传递")
        void shouldIncludePayload() throws Exception {
            Directive primary = new Directive("d-1", "GPS", "GetLocation",
                    Map.of("accuracy", "high", "timeout", 5000), 10000);
            String result = manager.getResult("req-1", primary);

            JsonNode json = MAPPER.readTree(result);
            JsonNode payload = json.get("data").get("directives").get(0).get("payload");
            assertEquals("high", payload.get("accuracy").asText());
            assertEquals(5000, payload.get("timeout").asInt());
        }
    }

    @Nested
    @DisplayName("CommonDirectiveProvider")
    class CommonProviderTests {

        @Test
        @DisplayName("添加 common provider 后指令列表包含 primary + common")
        void shouldIncludeCommonDirectives() throws Exception {
            manager.addCommonProvider(primary ->
                    new Directive("common-1", "Logger", "LogAction",
                            Map.of("action", primary.getName()), 5000)
            );

            Directive primary = createDirective("d-1", "Camera", "TakePhoto");
            String result = manager.getResult("req-1", primary);

            JsonNode json = MAPPER.readTree(result);
            JsonNode directives = json.get("data").get("directives");
            assertEquals(2, directives.size());

            assertEquals("Camera", directives.get(0).get("header").get("namespace").asText());
            assertEquals("Logger", directives.get(1).get("header").get("namespace").asText());
        }

        @Test
        @DisplayName("多个 common providers")
        void shouldSupportMultipleCommonProviders() throws Exception {
            manager.addCommonProvider(primary ->
                    new Directive("c-1", "Logger", "Log", Map.of(), 1000));
            manager.addCommonProvider(primary ->
                    new Directive("c-2", "Metrics", "Record", Map.of(), 1000));

            Directive primary = createDirective("d-1", "GPS", "Track");
            String result = manager.getResult("req-1", primary);

            JsonNode directives = MAPPER.readTree(result).get("data").get("directives");
            assertEquals(3, directives.size());
        }

        @Test
        @DisplayName("common provider 返回 null 时被忽略")
        void shouldSkipNullCommonDirectives() throws Exception {
            manager.addCommonProvider(primary -> null);

            Directive primary = createDirective("d-1", "Camera", "TakePhoto");
            String result = manager.getResult("req-1", primary);

            JsonNode directives = MAPPER.readTree(result).get("data").get("directives");
            assertEquals(1, directives.size());
        }

        @Test
        @DisplayName("null provider 抛出 NullPointerException")
        void shouldRejectNullProvider() {
            assertThrows(NullPointerException.class,
                    () -> manager.addCommonProvider(null));
        }
    }

    @Nested
    @DisplayName("指令顺序")
    class DirectiveOrderTests {

        @Test
        @DisplayName("primary 在前，common 在后")
        void primaryShouldBeFirst() throws Exception {
            manager.addCommonProvider(primary ->
                    new Directive("c-1", "After", "Second", Map.of(), 1000));

            Directive primary = createDirective("d-1", "Before", "First");
            String result = manager.getResult("req-1", primary);

            JsonNode directives = MAPPER.readTree(result).get("data").get("directives");
            assertEquals("Before", directives.get(0).get("header").get("namespace").asText());
            assertEquals("After", directives.get(1).get("header").get("namespace").asText());
        }
    }

    @Nested
    @DisplayName("WebSocket 消息格式")
    class MessageFormatTests {

        @Test
        @DisplayName("结果是有效 JSON")
        void shouldProduceValidJson() {
            Directive primary = createDirective("d-1", "NS", "Action");
            String result = manager.getResult("req-1", primary);

            assertDoesNotThrow(() -> MAPPER.readTree(result));
        }

        @Test
        @DisplayName("消息包含 request_id")
        void shouldIncludeRequestId() throws Exception {
            Directive primary = createDirective("d-1", "NS", "Action");
            String result = manager.getResult("req-1", primary);

            JsonNode json = MAPPER.readTree(result);
            assertEquals("req-1", json.get("request_id").asText());
        }
    }
}
