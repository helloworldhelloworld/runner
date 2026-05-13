package com.lightweightai.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("McpHeaderProvider — functional interface contract")
class McpHeaderProviderTest {

    @Test
    @DisplayName("lambda implementation returns static headers")
    void lambdaStaticHeaders() {
        McpHeaderProvider provider = () -> Map.of(
                "Authorization", "Bearer static-token",
                "X-Custom", "value"
        );

        Map<String, String> headers = provider.getHeaders();
        assertEquals("Bearer static-token", headers.get("Authorization"));
        assertEquals("value", headers.get("X-Custom"));
    }

    @Test
    @DisplayName("provider can return dynamic headers on each call")
    void dynamicHeaders() {
        AtomicInteger counter = new AtomicInteger(0);
        McpHeaderProvider provider = () -> Map.of(
                "X-Request-Id", "req-" + counter.incrementAndGet()
        );

        assertEquals("req-1", provider.getHeaders().get("X-Request-Id"));
        assertEquals("req-2", provider.getHeaders().get("X-Request-Id"));
        assertEquals("req-3", provider.getHeaders().get("X-Request-Id"));
    }

    @Test
    @DisplayName("provider returning empty map is valid")
    void emptyHeadersAreValid() {
        McpHeaderProvider provider = Map::of;
        Map<String, String> headers = provider.getHeaders();
        assertNotNull(headers);
        assertTrue(headers.isEmpty());
    }

    @Test
    @DisplayName("provider can be used as method reference")
    void methodReferenceUsage() {
        HeaderService service = new HeaderService("my-token");
        McpHeaderProvider provider = service::getHeaders;

        Map<String, String> headers = provider.getHeaders();
        assertEquals("Bearer my-token", headers.get("Authorization"));
    }

    private static class HeaderService {
        private final String token;
        HeaderService(String token) { this.token = token; }
        Map<String, String> getHeaders() {
            return Map.of("Authorization", "Bearer " + token);
        }
    }
}
