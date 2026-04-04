package com.lightweightai.tools.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebTools")
class WebToolsTest {

    @Test
    void shouldReturnMockResultsByDefault() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("java reactive", 3);

        assertNotNull(result);
        assertTrue(result.contains("java reactive"));
        assertTrue(result.contains("Mock search"));
    }

    @Test
    void shouldUseDefaultMaxResultsWhenZero() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("test query", 0);

        assertNotNull(result);
        assertTrue(result.contains("test query"));
    }

    @Test
    void shouldCreateWithCustomApiKey() {
        WebTools tools = new WebTools("test-api-key", 5);
        // Still returns mock results since no real API integration yet
        String result = tools.webSearch("test", 2);
        assertNotNull(result);
    }

    @Test
    void shouldCreateWithDefaultConstructor() {
        WebTools tools = new WebTools();
        assertNotNull(tools);
    }
}
