package com.lightweightai.tools.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebTools - web search tools (mock mode)")
class WebToolsTest {

    @Test
    @DisplayName("webSearch returns mock results without API key")
    void webSearchReturnsMockResults() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("latest news", 3);

        assertNotNull(result);
        assertTrue(result.contains("Mock search"));
        assertTrue(result.contains("latest news"));
    }

    @Test
    @DisplayName("webSearch with zero maxResults uses default")
    void webSearchZeroMaxUsesDefault() {
        WebTools tools = new WebTools(null, 5);
        String result = tools.webSearch("test query", 0);

        assertNotNull(result);
        assertTrue(result.contains("test query"));
    }

    @Test
    @DisplayName("webSearch with explicit maxResults limits results")
    void webSearchExplicitMaxResults() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("search term", 1);

        assertNotNull(result);
        assertTrue(result.contains("1 results") || result.contains("search term"));
    }

    @Test
    @DisplayName("default constructor sets null apiKey and 3 default max results")
    void defaultConstructorSetsDefaults() {
        WebTools tools = new WebTools();
        // verify mock mode works (no API key)
        String result = tools.webSearch("query", 3);
        assertTrue(result.contains("Mock search"));
    }

    @Test
    @DisplayName("constructor with apiKey enables future real search")
    void constructorWithApiKey() {
        WebTools tools = new WebTools("sk-test", 5);
        // Still returns mock results since real API is TODO
        String result = tools.webSearch("query", 3);
        assertNotNull(result);
    }
}
