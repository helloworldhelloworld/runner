package com.lightweightai.kernel.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolUse - Claude 工具调用请求")
class ToolUseTest {

    @Test
    @DisplayName("正常构建")
    void normalConstruction() {
        Map<String, Object> input = Map.of("city", "Beijing");
        ToolUse toolUse = new ToolUse("toolu_01", "get_weather", input);

        assertEquals("toolu_01", toolUse.getId());
        assertEquals("get_weather", toolUse.getName());
        assertEquals("Beijing", toolUse.getInput().get("city"));
    }

    @Test
    @DisplayName("null input 变为空 map")
    void nullInputBecomesEmpty() {
        ToolUse toolUse = new ToolUse("id1", "tool1", null);
        assertNotNull(toolUse.getInput());
        assertTrue(toolUse.getInput().isEmpty());
    }

    @Test
    @DisplayName("input 不可修改")
    void inputIsUnmodifiable() {
        ToolUse toolUse = new ToolUse("id1", "tool1", Map.of("k", "v"));
        assertThrows(UnsupportedOperationException.class, () ->
                toolUse.getInput().put("new", "val"));
    }

    @Test
    @DisplayName("id 为 null 时抛异常")
    void nullIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new ToolUse(null, "tool1", Map.of()));
    }

    @Test
    @DisplayName("id 为空字符串时抛异常")
    void emptyIdThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new ToolUse("  ", "tool1", Map.of()));
    }

    @Test
    @DisplayName("name 为 null 时抛异常")
    void nullNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new ToolUse("id1", null, Map.of()));
    }

    @Test
    @DisplayName("fromContentBlock 正常解析")
    void fromContentBlock() {
        Map<String, Object> block = new HashMap<>();
        block.put("type", "tool_use");
        block.put("id", "toolu_01");
        block.put("name", "get_weather");
        block.put("input", Map.of("city", "Shanghai"));

        ToolUse toolUse = ToolUse.fromContentBlock(block);
        assertEquals("toolu_01", toolUse.getId());
        assertEquals("get_weather", toolUse.getName());
        assertEquals("Shanghai", toolUse.getInput().get("city"));
    }

    @Test
    @DisplayName("fromContentBlock null 块抛异常")
    void fromContentBlockNull() {
        assertThrows(IllegalArgumentException.class, () ->
                ToolUse.fromContentBlock(null));
    }

    @Test
    @DisplayName("fromContentBlock 错误类型抛异常")
    void fromContentBlockWrongType() {
        Map<String, Object> block = Map.of("type", "text", "text", "hello");
        assertThrows(IllegalArgumentException.class, () ->
                ToolUse.fromContentBlock(block));
    }

    @Test
    @DisplayName("equals 和 hashCode 基于 id")
    void equalsAndHashCode() {
        ToolUse t1 = new ToolUse("id1", "tool1", Map.of("a", "1"));
        ToolUse t2 = new ToolUse("id1", "tool2", Map.of("b", "2"));
        ToolUse t3 = new ToolUse("id2", "tool1", Map.of("a", "1"));

        assertEquals(t1, t2);
        assertNotEquals(t1, t3);
        assertEquals(t1.hashCode(), t2.hashCode());
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void toStringOutput() {
        ToolUse toolUse = new ToolUse("id1", "get_weather", Map.of("city", "BJ"));
        String str = toolUse.toString();
        assertTrue(str.contains("id1"));
        assertTrue(str.contains("get_weather"));
    }
}
