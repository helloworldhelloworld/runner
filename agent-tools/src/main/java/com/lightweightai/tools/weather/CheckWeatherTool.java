package com.lightweightai.tools.weather;

import com.lightweightai.kernel.agent.annotation.ToolFunction;
import com.lightweightai.kernel.agent.annotation.ToolParam;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 天气查询云工具（Mock 实现）
 *
 * 根据城市名或经纬度返回模拟天气数据。
 */
public class CheckWeatherTool {

    private static final Map<String, Map<String, Object>> CITY_WEATHER = Map.of(
        "北京", Map.of("temperature", 22, "humidity", 45, "condition", "晴", "windSpeed", "3级"),
        "上海", Map.of("temperature", 25, "humidity", 72, "condition", "多云", "windSpeed", "2级"),
        "南京", Map.of("temperature", 24, "humidity", 60, "condition", "阴", "windSpeed", "2级"),
        "广州", Map.of("temperature", 30, "humidity", 80, "condition", "雷阵雨", "windSpeed", "4级"),
        "深圳", Map.of("temperature", 29, "humidity", 78, "condition", "多云", "windSpeed", "3级"),
        "成都", Map.of("temperature", 20, "humidity", 65, "condition", "小雨", "windSpeed", "1级"),
        "杭州", Map.of("temperature", 23, "humidity", 68, "condition", "晴", "windSpeed", "2级")
    );

    private static final Map<String, Object> DEFAULT_WEATHER =
        Map.of("temperature", 25, "humidity", 55, "condition", "晴", "windSpeed", "2级");

    @ToolFunction(
        name = "checkWeather",
        description = "Check current weather for a given location. Accepts city name, or latitude/longitude coordinates.",
        category = "weather",
        tags = {"weather", "api"},
        readOnly = true,
        idempotent = true
    )
    public String checkWeather(
        @ToolParam(name = "city", description = "City name (e.g., 北京, 上海)") String city,
        @ToolParam(name = "latitude", description = "Latitude coordinate") Double latitude,
        @ToolParam(name = "longitude", description = "Longitude coordinate") Double longitude
    ) {
        String resolvedCity = resolveCity(city, latitude, longitude);
        Map<String, Object> weather = CITY_WEATHER.getOrDefault(resolvedCity, DEFAULT_WEATHER);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("city", resolvedCity);
        result.putAll(weather);

        StringBuilder sb = new StringBuilder();
        sb.append(resolvedCity).append("天气: ");
        sb.append(weather.get("condition")).append(", ");
        sb.append("气温").append(weather.get("temperature")).append("°C, ");
        sb.append("湿度").append(weather.get("humidity")).append("%, ");
        sb.append("风力").append(weather.get("windSpeed"));
        return sb.toString();
    }

    private String resolveCity(String city, Double latitude, Double longitude) {
        if (city != null && !city.isBlank()) {
            return city.trim();
        }
        if (latitude != null && longitude != null) {
            // 简单的坐标→城市映射
            if (latitude > 39 && latitude < 41 && longitude > 115 && longitude < 117) return "北京";
            if (latitude > 30 && latitude < 32 && longitude > 120 && longitude < 122) return "上海";
            if (latitude > 31 && latitude < 33 && longitude > 118 && longitude < 119) return "南京";
            if (latitude > 22 && latitude < 24 && longitude > 112 && longitude < 114) return "广州";
            return "未知城市";
        }
        return "未知城市";
    }
}
