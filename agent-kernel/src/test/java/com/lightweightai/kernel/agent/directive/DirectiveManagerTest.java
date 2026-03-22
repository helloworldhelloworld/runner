package com.lightweightai.kernel.agent.directive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirectiveManagerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldReturnOnlyPrimaryWhenNoProviders() throws Exception {
        DefaultDirectiveManager manager = new DefaultDirectiveManager();
        Directive primary = new Directive("d1", "GeoInformation", "GetPosition", Map.of("p", 1), 0);

        String json = manager.getResult("r1", primary);
        JsonNode root = MAPPER.readTree(json);

        assertEquals("DIRECTIVE", root.path("type").asText());
        assertEquals("r1", root.path("request_id").asText());
        assertEquals("d1", root.path("data").path("directive_id").asText());
        assertEquals(1, root.path("data").path("directives").size());
    }

    @Test
    void shouldAppendCommonDirectiveFromProvider() throws Exception {
        DefaultDirectiveManager manager = new DefaultDirectiveManager();
        manager.addCommonProvider(primary ->
            new Directive(primary.getDirectiveId(), "UserInteraction", "DisplayLoadingCard",
                Map.of("state", "open"), 0)
        );
        Directive primary = new Directive("d1", "GeoInformation", "GetPosition", Map.of("p", 1), 0);

        String json = manager.getResult("r1", primary);
        JsonNode root = MAPPER.readTree(json);
        JsonNode directives = root.path("data").path("directives");

        assertEquals(2, directives.size());
        assertEquals("GeoInformation", directives.get(0).path("header").path("namespace").asText());
        assertEquals("UserInteraction", directives.get(1).path("header").path("namespace").asText());
        assertEquals("DisplayLoadingCard", directives.get(1).path("header").path("name").asText());
    }

    @Test
    void shouldSkipNullDirectiveFromProvider() throws Exception {
        DefaultDirectiveManager manager = new DefaultDirectiveManager();
        manager.addCommonProvider(primary -> null);
        Directive primary = new Directive("d1", "GeoInformation", "GetPosition", Map.of("p", 1), 0);

        String json = manager.getResult("r1", primary);
        JsonNode root = MAPPER.readTree(json);

        assertEquals(1, root.path("data").path("directives").size());
    }
}
