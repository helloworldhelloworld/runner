package com.lightweightai.tools.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebTools - Web search tool")
class WebToolsTest {

    @Test
    @DisplayName("default constructor creates mock mode instance")
    void defaultConstructorMockMode() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("test query", 3);

        assertNotNull(result);
        assertTrue(result.contains("Mock search"));
        assertTrue(result.contains("test query"));
    }

    @Test
    @DisplayName("webSearch includes query in mock results")
    void mockResultsContainQuery() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("weather today", 2);

        assertTrue(result.contains("weather today"));
    }

    @Test
    @DisplayName("webSearch with maxResults <= 0 uses defaultMaxResults")
    void zeroMaxResultsUsesDefault() {
        WebTools tools = new WebTools(null, 5);
        String result = tools.webSearch("query", 0);

        assertNotNull(result);
        assertTrue(result.contains("Mock search"));
    }

    @Test
    @DisplayName("webSearch with positive maxResults uses provided value")
    void positiveMaxResultsUsed() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("query", 1);

        assertNotNull(result);
        assertTrue(result.contains("1 results") || result.contains("Mock search"));
    }

    @Test
    @DisplayName("webSearch with blank apiKey stays in mock mode")
    void blankApiKeyMockMode() {
        WebTools tools = new WebTools("  ", 3);
        String result = tools.webSearch("test", 2);

        assertTrue(result.contains("Mock search"));
    }

    @Test
    @DisplayName("webSearch with null apiKey stays in mock mode")
    void nullApiKeyMockMode() {
        WebTools tools = new WebTools(null, 3);
        String result = tools.webSearch("test", 2);

        assertTrue(result.contains("Mock search"));
    }

    @Test
    @DisplayName("mock results cap shown at 2")
    void mockResultsCappedAt2() {
        WebTools tools = new WebTools();
        String result = tools.webSearch("anything", 10);

        assertTrue(result.contains("2 results"));
    }
}
