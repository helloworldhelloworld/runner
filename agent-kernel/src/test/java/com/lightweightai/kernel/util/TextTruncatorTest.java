package com.lightweightai.kernel.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TextTruncator - 日志 / WS 载荷安全截断")
class TextTruncatorTest {

    @Test
    @DisplayName("短于上限原样返回")
    void shorterThanLimitReturnsAsIs() {
        assertEquals("hello", TextTruncator.truncate("hello", 100));
    }

    @Test
    @DisplayName("等于上限原样返回")
    void equalToLimitReturnsAsIs() {
        String s = "abcde";
        assertEquals(s, TextTruncator.truncate(s, 5));
    }

    @Test
    @DisplayName("超出上限截断并带剩余字符数标记")
    void longerThanLimitTruncatedWithMarker() {
        String s = "abcdefghij"; // 10 chars
        String out = TextTruncator.truncate(s, 4);
        assertTrue(out.startsWith("abcd"), "应以截断前缀开头: " + out);
        assertTrue(out.contains("+6"), "应含剩余字符数提示: " + out);
        assertTrue(out.length() < s.length() + 20, "截断输出不应比原文长太多");
    }

    @Test
    @DisplayName("null 输入返回 null，不 NPE")
    void nullInputReturnsNull() {
        assertNull(TextTruncator.truncate(null, 10));
    }

    @Test
    @DisplayName("maxLen <= 0 视为不截断")
    void nonPositiveMaxReturnsAsIs() {
        assertEquals("hi", TextTruncator.truncate("hi", 0));
        assertEquals("hi", TextTruncator.truncate("hi", -1));
    }
}
