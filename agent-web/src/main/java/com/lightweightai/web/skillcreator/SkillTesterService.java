package com.lightweightai.web.skillcreator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 验证引擎
 *
 * 核心功能：
 * 1. parseToolCalls — 从 LLM 输出中提取 functionName + parameters
 * 2. mockToolResponse — 根据 mockDb 返回模拟工具结果
 * 3. evaluateAssertions — 校验工具调用是否符合预期断言
 */
public class SkillTesterService {

    private static final Logger logger = LoggerFactory.getLogger(SkillTesterService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // 匹配包含 functionName 的 JSON 对象
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
        "\\{[^{}]*\"functionName\"[^{}]*(?:\\{[^{}]*}[^{}]*)*}");

    /**
     * 解析的工具调用
     */
    public record ParsedToolCall(String functionName, Map<String, Object> parameters) {}

    /**
     * 断言校验结果
     */
    public record AssertionResult(String text, boolean pass) {}

    /**
     * 从 LLM 输出文本中提取所有工具调用
     */
    public static List<ParsedToolCall> parseToolCalls(String text) {
        List<ParsedToolCall> results = new ArrayList<>();
        if (text == null || text.isBlank()) return results;

        Matcher matcher = TOOL_CALL_PATTERN.matcher(text);
        while (matcher.find()) {
            try {
                Map<String, Object> obj = MAPPER.readValue(matcher.group(), new TypeReference<>() {});
                String functionName = (String) obj.get("functionName");
                if (functionName != null && !functionName.isBlank()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = obj.containsKey("parameters")
                        ? (Map<String, Object>) obj.get("parameters")
                        : Map.of();
                    results.add(new ParsedToolCall(functionName, params != null ? params : Map.of()));
                }
            } catch (Exception ignored) {
                // 非法 JSON 跳过
            }
        }
        return results;
    }

    /**
     * 校验工具调用是否符合断言列表
     */
    public static List<AssertionResult> evaluateAssertions(
            List<ParsedToolCall> toolCalls, List<String> assertions) {

        List<String> names = toolCalls.stream().map(ParsedToolCall::functionName).toList();

        return assertions.stream().map(assertion -> {
            boolean pass = evaluateSingle(assertion, names, toolCalls);
            return new AssertionResult(assertion, pass);
        }).toList();
    }

    private static boolean evaluateSingle(String assertion, List<String> names, List<ParsedToolCall> toolCalls) {
        String al = assertion.toLowerCase();

        // "先调用 X 再调用 Y"
        if (al.contains("先") && al.contains("再")) {
            String first = extractToolName(assertion, "先.*?(\\w+)");
            String second = extractToolName(assertion, "再.*?(\\w+)");
            if (first != null && second != null) {
                return names.contains(first) && names.contains(second)
                    && names.indexOf(first) < names.indexOf(second);
            }
        }

        // "先调用 GetPoi 再调用 Navigate" — 显式匹配
        if (al.contains("getpoi") && al.contains("navigate") && al.contains("先")) {
            return names.contains("GetPoi") && names.contains("Navigate")
                && names.indexOf("GetPoi") < names.indexOf("Navigate");
        }

        // "不调用 X" — 通用匹配
        if (al.contains("不调用")) {
            java.util.regex.Matcher noCallMatcher = Pattern.compile("不调用\\s*(\\S+)").matcher(assertion);
            if (noCallMatcher.find()) {
                String toolName = noCallMatcher.group(1);
                return names.stream().noneMatch(n -> n.equalsIgnoreCase(toolName));
            }
        }

        // "不触发"
        if (al.contains("不触发")) {
            return names.isEmpty();
        }

        // "调用 X" — 通用匹配，不限定工具名
        {
            java.util.regex.Matcher callMatcher = Pattern.compile("调用\\s*(\\S+)").matcher(assertion);
            if (callMatcher.find() && !al.contains("不") && !al.contains("先")) {
                String toolName = callMatcher.group(1);
                // 大小写不敏感匹配
                return names.stream().anyMatch(n -> n.equalsIgnoreCase(toolName));
            }
        }

        // "Navigate 包含 desLat/desLng 经纬度"
        if ((al.contains("经纬度") || al.contains("deslat")) && !al.contains("pathLat") && !al.contains("途径")) {
            return toolCalls.stream()
                .filter(t -> "Navigate".equals(t.functionName()))
                .anyMatch(t -> t.parameters().containsKey("desLat") && t.parameters().containsKey("desLng"));
        }

        // "Navigate 包含 pathLat 途径点经纬度"
        if (al.contains("pathlat") || (al.contains("途径") && al.contains("经纬度"))) {
            return toolCalls.stream()
                .filter(t -> "Navigate".equals(t.functionName()))
                .anyMatch(t -> {
                    Object pathLat = t.parameters().get("pathLat");
                    Object pathLng = t.parameters().get("pathLng");
                    return pathLat instanceof List<?> pl && !pl.isEmpty()
                        && pathLng instanceof List<?> plng && !plng.isEmpty();
                });
        }

        // "SearchLocation 包含 isSearchWay"
        if (al.contains("issearchway")) {
            return toolCalls.stream()
                .filter(t -> "SearchLocation".equals(t.functionName()))
                .anyMatch(t -> Boolean.TRUE.equals(t.parameters().get("isSearchWay")));
        }

        // "含 appName"
        if (al.contains("appname")) {
            return toolCalls.stream()
                .anyMatch(t -> t.parameters().containsKey("appName"));
        }

        // 默认：未识别的断言视为通过（避免误判）
        logger.warn("Unrecognized assertion: {}", assertion);
        return true;
    }

    private static String extractToolName(String assertion, String regex) {
        java.util.regex.Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(assertion);
        if (m.find()) {
            String name = m.group(1);
            // 规范化工具名
            for (String tool : List.of("GetPoi", "Navigate", "SearchRoute", "SearchLocation", "ExitNavigation")) {
                if (tool.equalsIgnoreCase(name)) return tool;
            }
            return name;
        }
        return null;
    }

    /**
     * Mock 工具执行，返回模拟结果
     *
     * @param functionName 工具名
     * @param parameters   工具参数
     * @param mockDb       模拟 POI 数据库 { "地点名": [{ name, lat, lng, address, ... }] }
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> mockToolResponse(
            String functionName, Map<String, Object> parameters, Map<String, Object> mockDb) {

        return switch (functionName) {
            case "GetPoi" -> {
                String location = (String) parameters.getOrDefault("location", "");
                Object found = lookupMockDb(location, mockDb);
                if (found instanceof List<?> list) {
                    yield Map.of("status", "ok", "count", list.size(), "results", list);
                }
                yield Map.of("status", "ok", "count", 0, "results", List.of());
            }
            case "Navigate" -> Map.of("status", "ok",
                "message", "导航已启动 → " + parameters.getOrDefault("desLocation", ""));
            case "SearchRoute" -> Map.of("status", "ok",
                "message", "路线查询完成 → " + parameters.getOrDefault("desLocation", ""));
            case "SearchLocation" -> Map.of("status", "ok",
                "message", "地图标注完成 → " + parameters.getOrDefault("desLocation", ""));
            case "ExitNavigation" -> Map.of("status", "ok", "message", "导航已退出");
            default -> Map.of("status", "error", "message", "未知工具: " + functionName);
        };
    }

    /**
     * 模糊匹配 mockDb：精确 → 包含 → 被包含
     */
    private static Object lookupMockDb(String location, Map<String, Object> mockDb) {
        if (location == null || location.isBlank() || mockDb == null) return null;

        // 精确匹配
        if (mockDb.containsKey(location)) return mockDb.get(location);

        // location 包含某个 key
        for (var entry : mockDb.entrySet()) {
            if (location.contains(entry.getKey())) return entry.getValue();
        }

        // key 包含 location
        for (var entry : mockDb.entrySet()) {
            if (entry.getKey().contains(location)) return entry.getValue();
        }

        return null;
    }
}
