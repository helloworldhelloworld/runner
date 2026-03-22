package com.lightweightai.plugin.example;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

import com.lightweightai.kernel.agent.Tool;

import static org.junit.jupiter.api.Assertions.*;

class PluginToolTest {

    @Test
    void greetTool_returnsGreeting() {
        GreetTool tool = new GreetTool();
        assertEquals("greet", tool.getName());
        assertNotNull(tool.getDescription());
        assertNotNull(tool.getSchema());

        ToolResult result = tool.execute(Map.of("name", "张三"));
        assertFalse(result.isError());
        assertTrue(result.getContent().contains("张三"));
    }

    @Test
    void greetTool_defaultName() {
        GreetTool tool = new GreetTool();
        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertTrue(result.getContent().contains("World"));
    }

    @Test
    void randomQuoteTool_returnsQuote() {
        RandomQuoteTool tool = new RandomQuoteTool();
        assertEquals("random_quote", tool.getName());
        assertNotNull(tool.getDescription());

        ToolResult result = tool.execute(Map.of());
        assertFalse(result.isError());
        assertFalse(result.getContent().isBlank());
    }

    @Test
    void spiDiscovery() {
        ServiceLoader<Tool> loader = ServiceLoader.load(Tool.class);
        long count = loader.stream()
            .map(ServiceLoader.Provider::get)
            .filter(t -> t.getName().equals("greet") || t.getName().equals("random_quote"))
            .count();
        assertEquals(2, count, "Both tools should be discoverable via SPI");
    }
}
