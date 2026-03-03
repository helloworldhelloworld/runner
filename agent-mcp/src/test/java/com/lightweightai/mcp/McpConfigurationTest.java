package com.lightweightai.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * McpConfiguration 测试
 *
 * 验证配置模型和工厂方法
 */
@DisplayName("McpConfiguration - Server configuration model")
class McpConfigurationTest {

    @Test
    @DisplayName("Create STDIO server config via factory method")
    void shouldCreateStdioConfig() {
        McpConfiguration.ServerConfig config = McpConfiguration.ServerConfig
            .stdio("npx", "-y", "@weather/mcp-server");

        assertEquals("stdio", config.getTransport());
        assertEquals("npx", config.getCommand());
        assertEquals(List.of("-y", "@weather/mcp-server"), config.getArgs());
        assertTrue(config.isEnabled());
        assertEquals(30, config.getTimeoutSeconds());
    }

    @Test
    @DisplayName("Create SSE server config via factory method")
    void shouldCreateSseConfig() {
        McpConfiguration.ServerConfig config = McpConfiguration.ServerConfig
            .sse("http://localhost:8080/sse");

        assertEquals("sse", config.getTransport());
        assertEquals("http://localhost:8080/sse", config.getUrl());
        assertTrue(config.isEnabled());
    }

    @Test
    @DisplayName("Fluent env and timeout on STDIO config")
    void shouldSupportFluentMethods() {
        McpConfiguration.ServerConfig config = McpConfiguration.ServerConfig
            .stdio("python", "-m", "my_server")
            .withEnv("API_KEY", "test-key")
            .withEnv("DEBUG", "true")
            .withTimeout(60);

        assertEquals("python", config.getCommand());
        assertEquals(Map.of("API_KEY", "test-key", "DEBUG", "true"), config.getEnv());
        assertEquals(60, config.getTimeoutSeconds());
    }

    @Test
    @DisplayName("Add multiple servers to configuration")
    void shouldHoldMultipleServers() {
        McpConfiguration config = new McpConfiguration();
        config.addServer("weather", McpConfiguration.ServerConfig.stdio("npx", "-y", "@weather/mcp-server"));
        config.addServer("filesystem", McpConfiguration.ServerConfig.stdio("npx", "-y", "@mcp/server-filesystem"));
        config.addServer("remote", McpConfiguration.ServerConfig.sse("http://localhost:3000/sse"));

        assertEquals(3, config.getServers().size());
        assertNotNull(config.getServers().get("weather"));
        assertNotNull(config.getServers().get("filesystem"));
        assertNotNull(config.getServers().get("remote"));
    }

    @Test
    @DisplayName("getEnabledServers filters disabled servers")
    void shouldFilterDisabledServers() {
        McpConfiguration config = new McpConfiguration();
        config.addServer("enabled1", McpConfiguration.ServerConfig.stdio("cmd1"));
        config.addServer("enabled2", McpConfiguration.ServerConfig.stdio("cmd2"));

        McpConfiguration.ServerConfig disabled = McpConfiguration.ServerConfig.stdio("cmd3");
        disabled.setEnabled(false);
        config.addServer("disabled", disabled);

        assertEquals(3, config.getServers().size());
        assertEquals(2, config.getEnabledServers().size());
        assertFalse(config.getEnabledServers().containsKey("disabled"));
    }

    @Test
    @DisplayName("ServerConfig default values")
    void shouldHaveCorrectDefaults() {
        McpConfiguration.ServerConfig config = new McpConfiguration.ServerConfig();

        assertEquals("stdio", config.getTransport());
        assertTrue(config.isEnabled());
        assertEquals(30, config.getTimeoutSeconds());
        assertTrue(config.getArgs().isEmpty());
        assertTrue(config.getEnv().isEmpty());
        assertNull(config.getCommand());
        assertNull(config.getUrl());
    }

    @Test
    @DisplayName("Setter-based configuration (for YAML deserialization)")
    void shouldSupportSetters() {
        McpConfiguration.ServerConfig config = new McpConfiguration.ServerConfig();
        config.setTransport("stdio");
        config.setCommand("java");
        config.setArgs(List.of("-jar", "server.jar"));
        config.setEnv(Map.of("PORT", "3000"));
        config.setEnabled(true);
        config.setTimeoutSeconds(45);

        assertEquals("java", config.getCommand());
        assertEquals(List.of("-jar", "server.jar"), config.getArgs());
        assertEquals("3000", config.getEnv().get("PORT"));
        assertEquals(45, config.getTimeoutSeconds());
    }

    @Test
    @DisplayName("toString shows transport type and key info")
    void shouldHaveDescriptiveToString() {
        McpConfiguration.ServerConfig stdio = McpConfiguration.ServerConfig.stdio("npx", "-y", "@test");
        assertTrue(stdio.toString().contains("stdio"));
        assertTrue(stdio.toString().contains("npx"));

        McpConfiguration.ServerConfig sse = McpConfiguration.ServerConfig.sse("http://example.com/sse");
        assertTrue(sse.toString().contains("sse"));
        assertTrue(sse.toString().contains("http://example.com/sse"));
    }
}
