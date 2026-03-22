package com.lightweightai.tools.weather;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.annotation.AnnotatedToolScanner;
import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CheckWeatherTool 云工具")
class CheckWeatherToolTest {

    private final List<Tool> tools = AnnotatedToolScanner.scan(new CheckWeatherTool());

    private Tool getCheckWeatherTool() {
        return tools.stream()
            .filter(t -> "checkWeather".equals(t.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("checkWeather tool not found"));
    }

    @Test
    @DisplayName("应通过注解扫描发现 checkWeather 工具")
    void shouldDiscoverViaAnnotation() {
        assertFalse(tools.isEmpty());
        assertTrue(tools.stream().anyMatch(t -> "checkWeather".equals(t.getName())));
    }

    @Test
    @DisplayName("传入 city 应返回天气数据")
    void shouldReturnWeatherForCity() {
        Tool tool = getCheckWeatherTool();
        ToolResult result = tool.execute(Map.of("city", "北京"));

        assertFalse(result.isError());
        String content = result.getContent();
        assertTrue(content.contains("北京"));
        assertTrue(content.contains("晴"));
        assertTrue(content.contains("22°C"));
    }

    @Test
    @DisplayName("传入 latitude/longitude 应返回天气数据")
    void shouldReturnWeatherForCoordinates() {
        Tool tool = getCheckWeatherTool();
        ToolResult result = tool.execute(Map.of(
            "latitude", 39.9, "longitude", 116.4
        ));

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("北京"));
    }

    @Test
    @DisplayName("无参数时应返回默认天气")
    void shouldReturnDefaultWeatherWhenNoParams() {
        Tool tool = getCheckWeatherTool();
        ToolResult result = tool.execute(Map.of());

        assertFalse(result.isError());
        assertTrue(result.getContent().contains("未知城市"));
    }

    @Test
    @DisplayName("不同城市应返回不同天气")
    void shouldReturnDifferentWeatherForDifferentCities() {
        Tool tool = getCheckWeatherTool();

        String beijing = tool.execute(Map.of("city", "北京")).getContent();
        String guangzhou = tool.execute(Map.of("city", "广州")).getContent();

        assertTrue(beijing.contains("晴"));
        assertTrue(guangzhou.contains("雷阵雨"));
    }
}
