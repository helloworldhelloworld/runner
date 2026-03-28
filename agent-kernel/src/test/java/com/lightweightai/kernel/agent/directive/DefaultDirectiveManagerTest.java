package com.lightweightai.kernel.agent.directive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DefaultDirectiveManager - Directive 序列化与分发")
class DefaultDirectiveManagerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private DefaultDirectiveManager manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultDirectiveManager();
    }

    private Directive directive(String id, String namespace, String name, Map<String, Object> payload) {
        return new Directive(id, namespace, name, payload, 30_000);
    }

    @Test
    @DisplayName("单个 primary directive 序列化为 JSON")
    void singlePrimaryDirective() throws Exception {
        Directive primary = directive("d1", "Camera", "TakePhoto", Map.of("resolution", "1080p"));

        String json = manager.getResult("req-1", primary);

        assertNotNull(json);
        JsonNode root = MAPPER.readTree(json);
        assertEquals("DIRECTIVE", root.path("type").asText());
        assertEquals("req-1", root.path("requestId").asText());

        JsonNode directives = root.path("data").path("directives");
        assertTrue(directives.isArray());
        assertEquals(1, directives.size());

        JsonNode first = directives.get(0);
        assertEquals("Camera", first.path("header").path("namespace").asText());
        assertEquals("TakePhoto", first.path("header").path("name").asText());
    }

    @Test
    @DisplayName("primary + common directives 合并")
    void primaryPlusCommon() throws Exception {
        manager.addCommonProvider(p ->
            directive("common-1", "Logging", "TrackEvent", Map.of("event", "tool_call")));

        Directive primary = directive("d1", "GPS", "GetLocation", Map.of());

        String json = manager.getResult("req-1", primary);
        JsonNode directives = MAPPER.readTree(json).path("data").path("directives");

        assertEquals(2, directives.size());
        assertEquals("GPS", directives.get(0).path("header").path("namespace").asText());
        assertEquals("Logging", directives.get(1).path("header").path("namespace").asText());
    }

    @Test
    @DisplayName("common provider 返回 null 时被过滤")
    void nullCommonDirectiveFiltered() throws Exception {
        manager.addCommonProvider(p -> null); // Returns null
        manager.addCommonProvider(p -> directive("c1", "Log", "Track", Map.of()));

        Directive primary = directive("d1", "Camera", "Photo", Map.of());

        String json = manager.getResult("req-1", primary);
        JsonNode directives = MAPPER.readTree(json).path("data").path("directives");

        assertEquals(2, directives.size()); // primary + 1 non-null common
    }

    @Test
    @DisplayName("null primary 抛异常")
    void nullPrimaryThrows() {
        assertThrows(NullPointerException.class,
            () -> manager.getResult("req-1", null));
    }

    @Test
    @DisplayName("null provider 抛异常")
    void nullProviderThrows() {
        assertThrows(NullPointerException.class,
            () -> manager.addCommonProvider(null));
    }

    @Test
    @DisplayName("多个 common providers 按添加顺序")
    void multipleProvidersInOrder() throws Exception {
        manager.addCommonProvider(p -> directive("c1", "A", "First", Map.of()));
        manager.addCommonProvider(p -> directive("c2", "B", "Second", Map.of()));

        Directive primary = directive("d1", "Main", "Primary", Map.of());

        String json = manager.getResult("req-1", primary);
        JsonNode directives = MAPPER.readTree(json).path("data").path("directives");

        assertEquals(3, directives.size());
        assertEquals("Main", directives.get(0).path("header").path("namespace").asText());
        assertEquals("A", directives.get(1).path("header").path("namespace").asText());
        assertEquals("B", directives.get(2).path("header").path("namespace").asText());
    }
}
