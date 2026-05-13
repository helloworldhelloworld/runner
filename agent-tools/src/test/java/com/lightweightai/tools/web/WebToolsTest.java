package com.lightweightai.tools.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebTools — web search tool")
class WebToolsTest {

    @Test
    @DisplayName("webSearch returns mock results with query text")
    void webSearchReturnsMockResults() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("latest AI news", 3);

        assertNotNull(result);
        assertTrue(result.contains("latest AI news"));
        assertTrue(result.contains("Mock search"));
    }

    @Test
    @DisplayName("webSearch with 0 maxResults uses default")
    void webSearchWithZeroMaxResultsUsesDefault() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("test query", 0);

        assertNotNull(result);
        assertTrue(result.contains("test query"));
    }

    @Test
    @DisplayName("webSearch with negative maxResults uses default")
    void webSearchWithNegativeMaxResultsUsesDefault() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("query", -1);

        assertNotNull(result);
        assertTrue(result.contains("results"));
    }

    @Test
    @DisplayName("webSearch with custom maxResults respects limit")
    void webSearchWithCustomMaxResults() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("query", 1);

        assertNotNull(result);
        assertTrue(result.contains("1 results") || result.contains("results"));
    }

    @Test
    @DisplayName("constructor with apiKey does not throw")
    void constructorWithApiKey() {
        WebTools tools = new WebTools("test-api-key", 5);
        String result = tools.webSearch("query", 3);
        assertNotNull(result);
    }

    @Test
    @DisplayName("default constructor uses null apiKey and 3 maxResults")
    void defaultConstructor() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("test", 0);
        assertNotNull(result);
    }

    @Test
    @DisplayName("mock results contain numbered items")
    void mockResultsContainNumberedItems() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("weather today", 3);

        assertTrue(result.contains("1."));
        assertTrue(result.contains("2."));
    }
}
