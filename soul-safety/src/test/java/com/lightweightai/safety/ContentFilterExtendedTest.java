package com.lightweightai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ContentFilter - 内容过滤扩展测试")
class ContentFilterExtendedTest {

    private final ContentFilter filter = new ContentFilter();

    @Test
    @DisplayName("null 输入返回 null")
    void nullInput() {
        assertNull(filter.filter(null));
    }

    @Test
    @DisplayName("空字符串返回空字符串")
    void emptyInput() {
        assertEquals("", filter.filter(""));
    }

    @Test
    @DisplayName("空白字符串返回空白")
    void blankInput() {
        assertEquals("   ", filter.filter("   "));
    }

    @Test
    @DisplayName("正常文本不被过滤")
    void normalText() {
        String text = "今天天气不错，心情很好。";
        assertEquals(text, filter.filter(text));
    }

    @Test
    @DisplayName("诊断性语句被过滤 - 你患有")
    void diagnosticFiltered1() {
        String text = "你患有抑郁症，需要治疗。";
        String result = filter.filter(text);
        assertNotEquals(text, result);
        assertTrue(result.contains("专业"));
    }

    @Test
    @DisplayName("诊断性语句被过滤 - 你得了")
    void diagnosticFiltered2() {
        String text = "你得了焦虑症。";
        String result = filter.filter(text);
        assertNotEquals(text, result);
    }

    @Test
    @DisplayName("诊断性语句被过滤 - 诊断为")
    void diagnosticFiltered3() {
        String text = "根据你的描述，可以诊断为抑郁。";
        String result = filter.filter(text);
        assertNotEquals(text, result);
    }

    @Test
    @DisplayName("诊断性语句被过滤 - 症状表现")
    void diagnosticFiltered4() {
        String text = "这是抑郁的症状。";
        String result = filter.filter(text);
        assertNotEquals(text, result);
    }

    @Test
    @DisplayName("用药建议被过滤")
    void medicationFiltered() {
        String text = "建议立即就医。";
        String result = filter.filter(text);
        assertNotEquals(text, result);
    }
}
